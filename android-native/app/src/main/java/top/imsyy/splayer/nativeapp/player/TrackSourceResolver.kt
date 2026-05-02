package top.imsyy.splayer.nativeapp.player

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import top.imsyy.splayer.nativeapp.data.local.AppSettingsStore
import top.imsyy.splayer.nativeapp.data.repository.QueueRepository
import top.imsyy.splayer.nativeapp.data.repository.SPlayerRemoteRepository
import top.imsyy.splayer.nativeapp.model.TrackItem
import top.imsyy.splayer.nativeapp.model.TrackSource
import top.imsyy.splayer.nativeapp.model.UnlockServerMode

internal const val LOCAL_NETEASE_UNLOCK_SOURCE = "native-netease"

private val orderedRemoteUnlockServers = listOf("netease", "kuwo", "gequbao", "bodian")

internal fun resolveEnabledUnlockServers(
    mode: UnlockServerMode,
    failedSources: Set<String>,
): List<String> {
    val orderedServers = when (mode) {
        UnlockServerMode.LOCAL -> listOf(LOCAL_NETEASE_UNLOCK_SOURCE)
        UnlockServerMode.EXTERNAL -> orderedRemoteUnlockServers
    }
    return orderedServers.filterNot { failedSources.contains(it) }
}

@Singleton
class TrackSourceResolver @Inject constructor(
    private val remoteRepository: SPlayerRemoteRepository,
    private val queueRepository: QueueRepository,
    private val appSettingsStore: AppSettingsStore,
) {
    private val officialSource = "official"

    suspend fun resolve(track: TrackItem): TrackSource {
        val failedSources = queueRepository.getFailedSources(track.id)
        val candidates = resolveEnabledUnlockServers(
            mode = appSettingsStore.unlockServerMode,
            failedSources = failedSources,
        )
        val tryOfficial = !failedSources.contains(officialSource)
        return remoteRepository.resolveSongSource(track, tryOfficial, candidates)
    }

    suspend fun getRetryLimit(track: TrackItem): Int {
        val failedSources = queueRepository.getFailedSources(track.id)
        val availableOfficial = if (failedSources.contains(officialSource)) 0 else 1
        val availableUnlockSources = resolveEnabledUnlockServers(
            mode = appSettingsStore.unlockServerMode,
            failedSources = failedSources,
        ).size
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
