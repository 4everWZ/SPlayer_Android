package top.imsyy.splayer.nativeapp.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
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

@Entity(tableName = "playback_snapshot")
data class PlaybackSnapshotEntity(
    @PrimaryKey val id: Int = 1,
    val currentTrackId: Long,
    val currentIndex: Int,
    val positionMs: Long,
    val durationMs: Long,
    val savedAtMs: Long,
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

@Entity(tableName = "playlist_detail_cache")
data class PlaylistDetailCacheEntity(
    @PrimaryKey val playlistId: Long,
    val name: String,
    val coverUrl: String,
    val description: String,
    val playCount: Long,
    val subscribedCount: Long,
    val trackCount: Int,
    val tracksJson: String,
    val cachedAt: Long,
)

@Dao
interface PlaybackQueueDao {
    @Query("SELECT * FROM playback_queue ORDER BY queueIndex ASC")
    fun observeQueue(): Flow<List<PlaybackQueueEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceQueue(items: List<PlaybackQueueEntity>)

    @Query("DELETE FROM playback_queue")
    suspend fun clearQueue()

    @Transaction
    suspend fun replaceQueueTransaction(items: List<PlaybackQueueEntity>) {
        clearQueue()
        replaceQueue(items)
    }
}

@Dao
interface PlaybackSnapshotDao {
    @Query("SELECT * FROM playback_snapshot WHERE id = 1")
    fun observeSnapshot(): Flow<PlaybackSnapshotEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshot(snapshot: PlaybackSnapshotEntity)

    @Query("DELETE FROM playback_snapshot")
    suspend fun clearSnapshot()
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

@Dao
interface PlaylistDetailCacheDao {
    @Query("SELECT * FROM playlist_detail_cache WHERE playlistId = :playlistId")
    suspend fun findById(playlistId: Long): PlaylistDetailCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PlaylistDetailCacheEntity)

    @Query("DELETE FROM playlist_detail_cache WHERE playlistId = :playlistId")
    suspend fun deleteById(playlistId: Long)
}

@Database(
    entities = [
        PlaybackQueueEntity::class,
        PlaybackSnapshotEntity::class,
        RecentPlayEntity::class,
        FailedSourceEntity::class,
        PlaylistDetailCacheEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class SPlayerDatabase : RoomDatabase() {
    abstract fun playbackQueueDao(): PlaybackQueueDao
    abstract fun playbackSnapshotDao(): PlaybackSnapshotDao
    abstract fun recentPlayDao(): RecentPlayDao
    abstract fun failedSourceDao(): FailedSourceDao
    abstract fun playlistDetailCacheDao(): PlaylistDetailCacheDao
}
