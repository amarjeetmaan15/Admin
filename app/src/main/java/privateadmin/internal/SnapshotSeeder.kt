package privateadmin.internal

import android.content.res.AssetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object SnapshotSeeder {
 suspend fun seed(assets:AssetManager,dao:LocalDao) = withContext(Dispatchers.IO) {
  if(dao.meta()!=null)return@withContext
  val root=assets.open("routes.json").bufferedReader().use{JSONObject(it.readText())}
  val places=root.optJSONArray("places")?:org.json.JSONArray()
  val routes=root.optJSONArray("routes")?:org.json.JSONArray()
  for(i in 0 until places.length()){
   val p=places.getJSONObject(i)
   dao.putPlace(PlaceRow(p.getString("id"),p.optString("kind","place"),p.optJSONObject("name")?.toString()?:"{}"))
  }
  for(i in 0 until routes.length()){
   val r=routes.getJSONObject(i)
   dao.putRoute(RouteRow(r.getString("id"),r.toString(),r.optString("from"),r.optString("to"),r.optString("time").ifBlank{null}))
  }
  dao.putMeta(SyncMeta(revision=root.optLong("snapshotRevision",0)))
 }
}
