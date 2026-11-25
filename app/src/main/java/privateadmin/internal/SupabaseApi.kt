package privateadmin.internal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class Session(val access:String,val refresh:String,val expiresAt:Long)
class SupabaseApi {
 @Volatile var session:Session?=null
 private val refreshMutex=Mutex()
 private suspend fun request(path:String,method:String="GET",body:String?=null,auth:Boolean=true,prefer:String?=null):String=withContext(Dispatchers.IO) {
  val c=URL(Config.SUPABASE_URL+path).openConnection() as HttpURLConnection
  try {
   c.requestMethod=method;c.connectTimeout=15000;c.readTimeout=20000
   c.setRequestProperty("apikey",Config.SUPABASE_PUBLISHABLE_KEY);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","application/json");if(prefer!=null)c.setRequestProperty("Prefer",prefer)
   if(auth)session?.let{c.setRequestProperty("Authorization","Bearer "+it.access)}
   if(body!=null){c.doOutput=true;c.outputStream.use{it.write(body.toByteArray(Charsets.UTF_8))}}
   val code=c.responseCode;val stream=if(code in 200..299)c.inputStream else c.errorStream
   val response=stream?.bufferedReader()?.use{it.readText()}?:""
   if(code !in 200..299)throw IllegalStateException(parseError(response,code))
   response
  } finally { c.disconnect() }
 }
 private fun parseError(s:String,code:Int):String=try{JSONObject(s).optString("message",JSONObject(s).optString("error","HTTP "+code))}catch(_:Exception){"HTTP "+code}
 suspend fun signIn(email:String,password:String):Session {
  val j=JSONObject(request("/auth/v1/token?grant_type=password","POST",JSONObject().put("email",email).put("password",password).toString(),false))
  val user=j.optJSONObject("user")?:JSONObject()
  if(user.optString("id")!=Config.OWNER_UID)throw IllegalStateException("This account is not the authorized owner.")
  return Session(j.getString("access_token"),j.getString("refresh_token"),System.currentTimeMillis()/1000+j.optLong("expires_in",3600)).also{session=it}
 }
 suspend fun refresh():Session {
  val old=session?:throw IllegalStateException("Please sign in again.")
  val j=JSONObject(request("/auth/v1/token?grant_type=refresh_token","POST",JSONObject().put("refresh_token",old.refresh).toString(),false))
  val user=j.optJSONObject("user")?:JSONObject()
  if(user.optString("id")!=Config.OWNER_UID)throw IllegalStateException("This account is not the authorized owner.")
  return Session(j.getString("access_token"),j.getString("refresh_token"),System.currentTimeMillis()/1000+j.optLong("expires_in",3600)).also{session=it}
 }
 private suspend fun ensureToken(){refreshMutex.withLock{if((session?.expiresAt?:0L)<System.currentTimeMillis()/1000+90)refresh()}}
 suspend fun publish(body:JSONObject):JSONObject {ensureToken();return JSONObject(request("/functions/v1/mdm-publish","POST",body.toString()))}
 suspend fun history(body:JSONObject):JSONObject {ensureToken();return JSONObject(request("/functions/v1/mdm-history","POST",body.toString()))}
 suspend fun snapshot():JSONObject=JSONObject(request("/functions/v1/bustime-data-sync?action=snapshot","GET",null,false))
 suspend fun syncStatus():JSONArray=JSONArray(request("/functions/v1/bustime-data-sync?action=status","GET",null,false))
 suspend fun syncChanges(since:Long,offset:Int):JSONArray=JSONArray(request("/functions/v1/bustime-data-sync?action=changes&since=$since&offset=$offset","GET",null,false))

 suspend fun uploadAdPoster(path:String,bytes:ByteArray,mime:String):String=withContext(Dispatchers.IO) {
  ensureToken()
  val safePath=path.split("/").joinToString("/") { java.net.URLEncoder.encode(it,"UTF-8").replace("+","%20") }
  val c=URL(Config.SUPABASE_URL.trimEnd('/')+"/storage/v1/object/ads-posters/"+safePath).openConnection() as HttpURLConnection
  try {
   c.requestMethod="POST";c.connectTimeout=20000;c.readTimeout=30000;c.doOutput=true
   c.setRequestProperty("apikey",Config.SUPABASE_PUBLISHABLE_KEY)
   c.setRequestProperty("Authorization","Bearer "+(session?.access?:throw IllegalStateException("Please sign in again.")))
   c.setRequestProperty("Content-Type",mime);c.setRequestProperty("x-upsert","false")
   c.outputStream.use{it.write(bytes)}
   val code=c.responseCode
   if(code !in 200..299){val detail=(if(code>=400)c.errorStream else c.inputStream)?.bufferedReader()?.use{it.readText()}.orEmpty();throw IllegalStateException("Poster upload failed (HTTP "+code+"): "+detail)}
   Config.SUPABASE_URL.trimEnd()+"/storage/v1/object/public/ads-posters/"+safePath
  } finally {c.disconnect()}
 }
 suspend fun deleteAdPoster(savedUrl:String){ensureToken()
  val base=Config.SUPABASE_URL.trimEnd('/')
  val marker="/storage/v1/object/public/ads-posters/"
  val at=savedUrl.indexOf(marker)
  if(at<0||savedUrl.substring(0,at)!=base)throw IllegalStateException("The saved poster is not a managed ads-posters file.")
  val path=try{java.net.URLDecoder.decode(savedUrl.substring(at+marker.length).substringBefore("?"),"UTF-8")}catch(_:Exception){throw IllegalStateException("The saved poster URL is invalid.")}
  if(path.isBlank()||path.split("/").any{it.isBlank()||it=="."||it==".."})throw IllegalStateException("The saved poster path is invalid.")
  val current=loadAdSettings();val my=current.optJSONObject("my")?:JSONObject()
  my.put("poster_url","");current.put("my",my);saveAdSettings(current)
  val encoded=path.split("/").joinToString("/") { java.net.URLEncoder.encode(it,"UTF-8").replace("+","%20") }
  val c=URL(base+"/storage/v1/object/ads-posters/"+encoded).openConnection() as HttpURLConnection
  try{c.requestMethod="DELETE";c.connectTimeout=20000;c.readTimeout=30000;c.setRequestProperty("apikey",Config.SUPABASE_PUBLISHABLE_KEY);c.setRequestProperty("Authorization","Bearer "+(session?.access?:throw IllegalStateException("Please sign in again.")));val code=c.responseCode;if(code !in 200..299){val detail=c.errorStream?.bufferedReader()?.use{it.readText()}.orEmpty();throw IllegalStateException("Poster URL cleared, but Storage deletion failed (HTTP "+code+"): "+detail)}}finally{c.disconnect()}
 }
 suspend fun loadAdSettings():JSONObject {ensureToken();val rows=JSONArray(request("/rest/v1/ad_settings?select=config&id=eq.1","GET"));return rows.optJSONObject(0)?.optJSONObject("config")?:JSONObject()}
 suspend fun saveAdSettings(config:JSONObject){ensureToken();request("/rest/v1/ad_settings?on_conflict=id","POST",JSONObject().put("id",1).put("config",config).put("updated_at",java.time.Instant.now().toString()).toString(),true,"resolution=merge-duplicates,return=minimal")}
 suspend fun busOperators():JSONArray {
  ensureToken()
  return JSONArray(request("/rest/v1/bus_operators?select=id,operator_name,service_name,bus_types,location,phone,whatsapp,details,active&active=eq.true&order=service_name.asc&limit=500","GET"))
 }
 suspend fun addBusOperatorIfMissing(payload:JSONObject):Boolean {
  ensureToken()
  val rows=busOperators()
  val service=payload.optString("service_name").trim()
  val phone=payload.optString("phone").filter{it.isDigit()}
  val location=payload.optString("location").trim()
  val exists=(0 until rows.length()).any{index->
   val row=rows.optJSONObject(index)?:return@any false
   val rowService=row.optString("service_name").ifBlank{row.optString("operator_name")}.trim()
   val rowPhone=row.optString("phone").filter{it.isDigit()}
   val rowLocation=row.optString("location").trim()
   rowService.equals(service,true)&&rowPhone==phone&&rowLocation.equals(location,true)
  }
  if(exists)return false
  request("/rest/v1/bus_operators","POST",payload.toString(),true,"return=minimal")
  return true
 }
 suspend fun analyticsEvents():JSONArray {ensureToken();return JSONArray(request("/rest/v1/analytics_events?select=id,created_at,event_name,section,session_id,properties,app_version,platform&order=created_at.desc&limit=5000","GET"))}

}
