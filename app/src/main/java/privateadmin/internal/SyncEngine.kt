package privateadmin.internal

import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SyncEngine(private val api:SupabaseApi, private val dao:LocalDao) {
 private val syncMutex=Mutex()

 suspend fun sync():Long=syncMutex.withLock {
  withContext(Dispatchers.IO) {
   val statusRows=api.syncStatus()
   val status=statusRows.optJSONObject(0)?:throw IllegalStateException("Sync status is unavailable. Local data was kept unchanged.")
   if(!status.has("revision")||status.isNull("revision"))throw IllegalStateException("Sync status did not include a revision. Local data was kept unchanged.")
   val target=status.optLong("revision",-1L)
   if(target<0L)throw IllegalStateException("Sync status revision is invalid. Local data was kept unchanged.")
   val saved=dao.meta()?.revision?:0L
   if(saved>target)throw IllegalStateException("Server sync revision is older than this device. Local data was kept unchanged; retry after server sync is restored.")
   if(saved==target)return@withContext target

   val startCursor=saved
   var maxSeen=saved
   var offset=0
   while(true) {
    val rows=api.syncChanges(startCursor,offset)
    if(rows.length()==0)break
    for(i in 0 until rows.length()) {
     val row=rows.optJSONObject(i)?:throw IllegalStateException("Sync returned an invalid change record. Please retry.")
     val id=row.optString("entity_id").trim()
     val type=row.optString("entity_type")
     val operation=row.optString("operation")
     val revision=row.optLong("revision",-1L)
     if(id.isBlank()||revision<=startCursor||revision>target)throw IllegalStateException("Sync returned an incomplete or invalid change record. Please retry.")
     if(operation=="delete") {
      when(type) {
       "route"->dao.deleteRoute(id)
       "place"->dao.deletePlace(id)
       else->throw IllegalStateException("Sync returned an unsupported entity type: $type")
      }
     } else if(operation=="upsert") {
      val payload=row.optJSONObject("payload")?:throw IllegalStateException("Sync returned an empty record for $id.")
      when(type) {
       "place"->dao.putPlace(PlaceRow(id,payload.optString("kind","place"),payload.optJSONObject("name")?.toString()?:"{}",payload.optString("city").ifBlank{null},payload.optString("district").ifBlank{null},payload.toString()))
       "route"->dao.putRoute(RouteRow(id,payload.toString(),payload.optString("from"),payload.optString("to"),payload.optString("time").ifBlank{null}))
       else->throw IllegalStateException("Sync returned an unsupported entity type: $type")
      }
     } else throw IllegalStateException("Sync returned an unsupported operation: $operation")
     maxSeen=maxOf(maxSeen,revision)
    }
    offset+=rows.length()
    if(rows.length()<1000)break
   }
   if(maxSeen<target)throw IllegalStateException("Sync is incomplete (device revision $maxSeen, server revision $target). Local sync cursor was not advanced; retry.")
   dao.putMeta(SyncMeta(revision=target))
   target
  }
 }
}
