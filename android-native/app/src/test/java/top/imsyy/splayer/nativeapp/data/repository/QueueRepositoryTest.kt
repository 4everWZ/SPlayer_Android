package top.imsyy.splayer.nativeapp.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import top.imsyy.splayer.nativeapp.data.local.FailedSourceDao
import top.imsyy.splayer.nativeapp.data.local.FailedSourceEntity
import top.imsyy.splayer.nativeapp.data.local.PlaybackQueueDao
import top.imsyy.splayer.nativeapp.data.local.PlaybackQueueEntity
import top.imsyy.splayer.nativeapp.data.local.PlaybackSnapshotDao
import top.imsyy.splayer.nativeapp.data.local.PlaybackSnapshotEntity
import top.imsyy.splayer.nativeapp.data.local.RecentPlayDao
import top.imsyy.splayer.nativeapp.data.local.RecentPlayEntity
import top.imsyy.splayer.nativeapp.di.PLAYBACK_SNAPSHOT_CREATE_SQL
import top.imsyy.splayer.nativeapp.model.TrackItem

class QueueRepositoryTest {
    @Test
    fun `upsertPlaybackSnapshot persists and observes snapshot`() = runBlocking {
        val snapshotDao = FakePlaybackSnapshotDao()
        val repository = QueueRepository(
            playbackQueueDao = FakePlaybackQueueDao(),
            recentPlayDao = FakeRecentPlayDao(),
            failedSourceDao = FakeFailedSourceDao(),
            playbackSnapshotDao = snapshotDao,
        )

        repository.upsertPlaybackSnapshot(
            PlaybackSnapshot(
                currentTrackId = 42L,
                currentIndex = 3,
                positionMs = 64_000L,
                durationMs = 180_000L,
                savedAtMs = 900L,
            ),
        )

        val restored = repository.observePlaybackSnapshot().first()
        requireNotNull(restored)
        assertEquals(42L, restored.currentTrackId)
        assertEquals(3, restored.currentIndex)
        assertEquals(64_000L, restored.positionMs)
        assertEquals(180_000L, restored.durationMs)
        assertEquals(900L, restored.savedAtMs)
    }

    @Test
    fun `clearQueue also clears playback snapshot`() = runBlocking {
        val snapshotDao = FakePlaybackSnapshotDao()
        val repository = QueueRepository(
            playbackQueueDao = FakePlaybackQueueDao(),
            recentPlayDao = FakeRecentPlayDao(),
            failedSourceDao = FakeFailedSourceDao(),
            playbackSnapshotDao = snapshotDao,
        )
        repository.upsertPlaybackSnapshot(
            PlaybackSnapshot(
                currentTrackId = 42L,
                currentIndex = 0,
                positionMs = 10_000L,
                durationMs = 180_000L,
                savedAtMs = 1L,
            ),
        )

        repository.clearQueue()

        assertNull(repository.observePlaybackSnapshot().first())
    }

    @Test
    fun `replaceQueue does not publish empty queue while swapping loaded playlist pages`() = runBlocking {
        val queueDao = FakePlaybackQueueDao()
        val repository = QueueRepository(
            playbackQueueDao = queueDao,
            recentPlayDao = FakeRecentPlayDao(),
            failedSourceDao = FakeFailedSourceDao(),
            playbackSnapshotDao = FakePlaybackSnapshotDao(),
        )
        queueDao.seedQueue(
            listOf(
                PlaybackQueueEntity(
                    queueIndex = 0,
                    songId = 1L,
                    songName = "旧歌",
                    artists = "旧歌手",
                    album = "旧专辑",
                    coverUrl = "",
                    durationMs = 1_000L,
                ),
            ),
        )
        queueDao.clearRecordedQueues()

        repository.replaceQueue(
            listOf(
                TrackItem(
                    id = 2L,
                    name = "新歌",
                    artists = "新歌手",
                    album = "新专辑",
                    coverUrl = "",
                    durationMs = 2_000L,
                ),
            ),
        )

        assertEquals(listOf(listOf(2L)), queueDao.recordedSongIds())
    }

    @Test
    fun `playback snapshot migration creates single row table without dropping existing tables`() {
        val sql = PLAYBACK_SNAPSHOT_CREATE_SQL

        assertEquals(true, sql.contains("CREATE TABLE IF NOT EXISTS `playback_snapshot`"))
        assertEquals(true, sql.contains("`id` INTEGER NOT NULL"))
        assertEquals(true, sql.contains("`currentTrackId` INTEGER NOT NULL"))
        assertEquals(true, sql.contains("`positionMs` INTEGER NOT NULL"))
        assertEquals(true, sql.contains("PRIMARY KEY(`id`)"))
    }
}

private class FakePlaybackQueueDao : PlaybackQueueDao {
    private val queue = MutableStateFlow<List<PlaybackQueueEntity>>(emptyList())
    private val recordedQueues = mutableListOf<List<PlaybackQueueEntity>>()

    override fun observeQueue() = queue

    override suspend fun replaceQueue(items: List<PlaybackQueueEntity>) {
        queue.value = items
        recordedQueues.add(items)
    }

    override suspend fun clearQueue() {
        queue.value = emptyList()
        recordedQueues.add(emptyList())
    }

    override suspend fun replaceQueueTransaction(items: List<PlaybackQueueEntity>) {
        queue.value = items
        recordedQueues.add(items)
    }

    fun seedQueue(items: List<PlaybackQueueEntity>) {
        queue.value = items
    }

    fun clearRecordedQueues() {
        recordedQueues.clear()
    }

    fun recordedSongIds(): List<List<Long>> {
        return recordedQueues.map { items -> items.map { it.songId } }
    }
}

private class FakePlaybackSnapshotDao : PlaybackSnapshotDao {
    private val snapshot = MutableStateFlow<PlaybackSnapshotEntity?>(null)

    override fun observeSnapshot() = snapshot

    override suspend fun upsertSnapshot(snapshot: PlaybackSnapshotEntity) {
        this.snapshot.value = snapshot
    }

    override suspend fun clearSnapshot() {
        snapshot.value = null
    }
}

private class FakeRecentPlayDao : RecentPlayDao {
    private val recent = MutableStateFlow<List<RecentPlayEntity>>(emptyList())

    override fun observeRecent(limit: Int) = recent.map { items -> items.take(limit) }

    override suspend fun upsert(item: RecentPlayEntity) {
        recent.value = listOf(item) + recent.value.filterNot { it.songId == item.songId }
    }

    override suspend fun pruneOld(limit: Int) {
        recent.value = recent.value.take(limit)
    }

    override suspend fun clearAll() {
        recent.value = emptyList()
    }
}

private class FakeFailedSourceDao : FailedSourceDao {
    private val failedSources = mutableListOf<FailedSourceEntity>()

    override suspend fun findSources(songId: Long): List<String> {
        return failedSources.filter { it.songId == songId }.map { it.source }
    }

    override suspend fun insert(item: FailedSourceEntity) {
        if (failedSources.none { it.songId == item.songId && it.source == item.source }) {
            failedSources += item
        }
    }

    override suspend fun clear(songId: Long) {
        failedSources.removeAll { it.songId == songId }
    }

    override suspend fun clearAll() {
        failedSources.clear()
    }
}
