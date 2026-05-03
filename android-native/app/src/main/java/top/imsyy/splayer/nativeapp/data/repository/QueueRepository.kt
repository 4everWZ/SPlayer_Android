package top.imsyy.splayer.nativeapp.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import top.imsyy.splayer.nativeapp.data.local.FailedSourceDao
import top.imsyy.splayer.nativeapp.data.local.FailedSourceEntity
import top.imsyy.splayer.nativeapp.data.local.PlaybackQueueDao
import top.imsyy.splayer.nativeapp.data.local.PlaybackQueueEntity
import top.imsyy.splayer.nativeapp.data.local.PlaybackSnapshotDao
import top.imsyy.splayer.nativeapp.data.local.PlaybackSnapshotEntity
import top.imsyy.splayer.nativeapp.data.local.RecentPlayDao
import top.imsyy.splayer.nativeapp.data.local.RecentPlayEntity
import top.imsyy.splayer.nativeapp.model.TrackItem

data class PlaybackSnapshot(
    val currentTrackId: Long,
    val currentIndex: Int,
    val positionMs: Long,
    val durationMs: Long,
    val savedAtMs: Long,
)

@Singleton
class QueueRepository @Inject constructor(
    private val playbackQueueDao: PlaybackQueueDao,
    private val recentPlayDao: RecentPlayDao,
    private val failedSourceDao: FailedSourceDao,
    private val playbackSnapshotDao: PlaybackSnapshotDao,
) {
    fun observeQueue(): Flow<List<TrackItem>> = playbackQueueDao.observeQueue().map { items ->
        items.map { entity ->
            TrackItem(
                id = entity.songId,
                name = entity.songName,
                artists = entity.artists,
                album = entity.album,
                coverUrl = entity.coverUrl,
                durationMs = entity.durationMs,
            )
        }
    }

    fun observeRecent(): Flow<List<TrackItem>> = recentPlayDao.observeRecent().map { items ->
        items.map { entity ->
            TrackItem(
                id = entity.songId,
                name = entity.songName,
                artists = entity.artists,
                album = entity.album,
                coverUrl = entity.coverUrl,
                durationMs = entity.durationMs,
            )
        }
    }

    fun observePlaybackSnapshot(): Flow<PlaybackSnapshot?> = playbackSnapshotDao.observeSnapshot().map { entity ->
        entity?.let {
            PlaybackSnapshot(
                currentTrackId = it.currentTrackId,
                currentIndex = it.currentIndex,
                positionMs = it.positionMs,
                durationMs = it.durationMs,
                savedAtMs = it.savedAtMs,
            )
        }
    }

    suspend fun replaceQueue(tracks: List<TrackItem>) {
        playbackQueueDao.clearQueue()
        playbackQueueDao.replaceQueue(
            tracks.mapIndexed { index, track ->
                PlaybackQueueEntity(
                    queueIndex = index,
                    songId = track.id,
                    songName = track.name,
                    artists = track.artists,
                    album = track.album,
                    coverUrl = track.coverUrl,
                    durationMs = track.durationMs,
                )
            },
        )
    }

    suspend fun addRecent(track: TrackItem) {
        recentPlayDao.upsert(
            RecentPlayEntity(
                songId = track.id,
                songName = track.name,
                artists = track.artists,
                album = track.album,
                coverUrl = track.coverUrl,
                durationMs = track.durationMs,
                playedAt = System.currentTimeMillis(),
            ),
        )
        recentPlayDao.pruneOld()
    }

    suspend fun clearQueue() {
        playbackQueueDao.clearQueue()
        playbackSnapshotDao.clearSnapshot()
    }

    suspend fun clearRecent() {
        recentPlayDao.clearAll()
    }

    suspend fun getFailedSources(songId: Long): Set<String> = failedSourceDao.findSources(songId).toSet()

    suspend fun markSourceFailed(songId: Long, source: String) {
        failedSourceDao.insert(FailedSourceEntity(songId = songId, source = source))
    }

    suspend fun clearFailedSources(songId: Long) {
        failedSourceDao.clear(songId)
    }

    suspend fun clearAllFailedSources() {
        failedSourceDao.clearAll()
    }

    suspend fun upsertPlaybackSnapshot(snapshot: PlaybackSnapshot) {
        playbackSnapshotDao.upsertSnapshot(
            PlaybackSnapshotEntity(
                currentTrackId = snapshot.currentTrackId,
                currentIndex = snapshot.currentIndex,
                positionMs = snapshot.positionMs,
                durationMs = snapshot.durationMs,
                savedAtMs = snapshot.savedAtMs,
            ),
        )
    }

    suspend fun clearPlaybackSnapshot() {
        playbackSnapshotDao.clearSnapshot()
    }
}
