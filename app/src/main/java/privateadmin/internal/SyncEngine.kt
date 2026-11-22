package privateadmin.internal
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
class SyncEngine(private val api:SupabaseApi, private val dao:LocalDao) {
 suspend fun sync():Long=withContext(Dispatchers.IO) {
  val status=api.syncStatus().optJSONObject(0) ?: JSONObject()
  val target=status.optLong("revision",0); var cursor=dao.meta()?.revision?:0L; var offset=0
  if(cursor>target){dao.clearRoutes();dao.clearPlaces();cursor=0}
  while(true){val rows=api.syncChanges(cursor,offset);if(rows.length()==0)break
   for(i in 0 until rows.length()){val row=rows.getJSONObject(i);val id=row.optString("entity_id");val type=row.optString("entity_type")
    if(row.optString("operation")=="delete"){if(type=="route")dao.deleteRoute(id) else if(type=="place")dao.deletePlace(id)}
    else {val p=row.optJSONObject("payload")?:JSONObject()
     if(type=="place")dao.putPlace(PlaceRow(id,p.optString("kind","place"),p.optJSONObject("name")?.toString()?:"{}"))
     if(type=="route")dao.putRoute(RouteRow(id,p.toString(),p.optString("from"),p.optString("to"),p.optString("time").ifBlank{null}))
    };cursor=maxOf(cursor,row.optLong("revision"))
   };offset+=rows.length();if(rows.length()<1000)break
  };dao.putMeta(SyncMeta(revision=maxOf(cursor,target)));maxOf(cursor,target)
 }
}
