package top.imsyy.splayer.nativeapp.player

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import top.imsyy.splayer.nativeapp.data.repository.QueueRepository
import top.imsyy.splayer.nativeapp.data.repository.SPlayerRemoteRepository
import top.imsyy.splayer.nativeapp.model.TrackItem
import top.imsyy.splayer.nativeapp.model.TrackSource

@Singleton
class TrackSourceResolver @Inject constructor(
    private val remoteRepository: SPlayerRemoteRepository,
    private val queueRepository: QueueRepository,
) {
    private val officialSource = "official"
    private val orderedUnlockServers = listOf("netease", "kuwo", "gequbao", "bodian")

    suspend fun resolve(track: TrackItem): TrackSource {
        val failedSources = queueRepository.getFailedSources(track.id)
        val candidates = orderedUnlockServers.filterNot { failedSources.contains(it) }
        val tryOfficial = !failedSources.contains(officialSource)
        return remoteRepository.resolveSongSource(track, tryOfficial, candidates)
    }

    suspend fun getRetryLimit(track: TrackItem): Int {
        val failedSources = queueRepository.getFailedSources(track.id)
        val availableOfficial = if (failedSources.contains(officialSource)) 0 else 1
        val availableUnlockSources = orderedUnlockServers.count { !failedSources.contains(it) }
        return max(0, availableOfficial + availableUnlockSources - 1)
    }

    suspend fun markFailed(track: TrackItem, source: String?) {
        if (!source.isNullOrBlank()) {
            queueRepository.markSourceFailed(track.id, source)
        }
    }

    suspend fun clearFailures(track: TrackItem) {
        queueRepository.clearFailedSources(track.id)
    }
}
