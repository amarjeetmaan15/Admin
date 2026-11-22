package privateadmin.internal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class Session(val access:String,val refresh:String,val expiresAt:Long)
class SupabaseApi {
 @Volatile var session:Session?=null
 private suspend fun request(path:String,method:String="GET",body:String?=null,auth:Boolean=true):String=withContext(Dispatchers.IO) {
  val c=URL(Config.SUPABASE_URL+path).openConnection() as HttpURLConnection
  try {
   c.requestMethod=method;c.connectTimeout=15000;c.readTimeout=20000
   c.setRequestProperty("apikey",Config.SUPABASE_PUBLISHABLE_KEY);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","application/json")
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
 private suspend fun ensureToken(){if((session?.expiresAt?:0L)<System.currentTimeMillis()/1000+90)refresh()}
 suspend fun publish(body:JSONObject):JSONObject {ensureToken();return JSONObject(request("/functions/v1/mdm-publish","POST",body.toString()))}
 suspend fun syncStatus():JSONArray=JSONArray(request("/functions/v1/bustime-data-sync?action=status","GET",null,false))
 suspend fun syncChanges(since:Long,offset:Int):JSONArray=JSONArray(request("/functions/v1/bustime-data-sync?action=changes&since=$since&offset=$offset","GET",null,false))
}
