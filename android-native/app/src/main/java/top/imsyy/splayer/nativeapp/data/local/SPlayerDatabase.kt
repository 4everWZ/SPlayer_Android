package top.imsyy.splayer.nativeapp.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "playback_queue")
data class PlaybackQueueEntity(
    @PrimaryKey val queueIndex: Int,
    val songId: Long,
    val songName: String,
    val artists: String,
    val album: String,
    val coverUrl: String,
    val durationMs: Long,
)

@Entity(tableName = "recent_play")
data class RecentPlayEntity(
    @PrimaryKey val songId: Long,
    val songName: String,
    val artists: String,
    val album: String,
    val coverUrl: String,
    val durationMs: Long,
    val playedAt: Long,
)

@Entity(tableName = "failed_source")
data class FailedSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val source: String,
)

@Dao
interface PlaybackQueueDao {
    @Query("SELECT * FROM playback_queue ORDER BY queueIndex ASC")
    fun observeQueue(): Flow<List<PlaybackQueueEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceQueue(items: List<PlaybackQueueEntity>)

    @Query("DELETE FROM playback_queue")
    suspend fun clearQueue()
}

@Dao
interface RecentPlayDao {
    @Query("SELECT * FROM recent_play ORDER BY playedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 30): Flow<List<RecentPlayEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: RecentPlayEntity)

    @Query("DELETE FROM recent_play WHERE songId NOT IN (SELECT songId FROM recent_play ORDER BY playedAt DESC LIMIT :limit)")
    suspend fun pruneOld(limit: Int = 60)

    @Query("DELETE FROM recent_play")
    suspend fun clearAll()
}

@Dao
interface FailedSourceDao {
    @Query("SELECT source FROM failed_source WHERE songId = :songId")
    suspend fun findSources(songId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: FailedSourceEntity)

    @Query("DELETE FROM failed_source WHERE songId = :songId")
    suspend fun clear(songId: Long)

    @Query("DELETE FROM failed_source")
    suspend fun clearAll()
}

@Database(
    entities = [PlaybackQueueEntity::class, RecentPlayEntity::class, FailedSourceEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class SPlayerDatabase : RoomDatabase() {
    abstract fun playbackQueueDao(): PlaybackQueueDao
    abstract fun recentPlayDao(): RecentPlayDao
    abstract fun failedSourceDao(): FailedSourceDao
}
