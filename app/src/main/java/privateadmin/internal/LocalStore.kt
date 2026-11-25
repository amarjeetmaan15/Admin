package privateadmin.internal

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "places")
data class PlaceRow(@PrimaryKey val id:String, val kind:String, val names:String, val city:String?=null, val district:String?=null, val payload:String="{}")
@Entity(tableName = "routes")
data class RouteRow(@PrimaryKey val id:String, val payload:String, val fromId:String, val toId:String, val time:String?)
@Entity(tableName = "sync_meta")
data class SyncMeta(@PrimaryKey val id:Int=1, val revision:Long=0)

@Dao interface LocalDao {
    @Query("SELECT * FROM places ORDER BY id") fun places(): Flow<List<PlaceRow>>
    @Query("SELECT * FROM routes ORDER BY fromId,toId,time") fun routes(): Flow<List<RouteRow>>
    @Query("SELECT * FROM places") suspend fun allPlaces():List<PlaceRow>
    @Query("SELECT * FROM routes") suspend fun allRoutes():List<RouteRow>
    @Query("SELECT * FROM sync_meta WHERE id=1") suspend fun meta():SyncMeta?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putPlace(row:PlaceRow)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putRoute(row:RouteRow)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putMeta(row:SyncMeta)
    @Query("DELETE FROM places WHERE id=:id") suspend fun deletePlace(id:String)
    @Query("DELETE FROM routes WHERE id=:id") suspend fun deleteRoute(id:String)
    @Query("DELETE FROM routes") suspend fun clearRoutes()
    @Query("DELETE FROM places") suspend fun clearPlaces()
    @Transaction
    suspend fun replaceSnapshot(places:List<PlaceRow>,routes:List<RouteRow>,meta:SyncMeta){
        clearRoutes()
        clearPlaces()
        places.forEach{putPlace(it)}
        routes.forEach{putRoute(it)}
        putMeta(meta)
    }
}
@Database(entities=[PlaceRow::class,RouteRow::class,SyncMeta::class],version=1,exportSchema=false)
abstract class LocalDb:RoomDatabase() {
    abstract fun dao():LocalDao
    companion object { @Volatile private var INSTANCE:LocalDb?=null
        fun get(context:Context)=INSTANCE?: synchronized(this) { INSTANCE?:Room.databaseBuilder(context.applicationContext,LocalDb::class.java,"private_admin.db").build().also{INSTANCE=it} }
    }
}
