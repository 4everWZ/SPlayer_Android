package top.imsyy.splayer.nativeapp.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.net.URI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import top.imsyy.splayer.nativeapp.AppSettingsProto
import top.imsyy.splayer.nativeapp.di.ApplicationScope
import top.imsyy.splayer.nativeapp.model.PlayMode
import top.imsyy.splayer.nativeapp.model.ThemeMode
import top.imsyy.splayer.nativeapp.model.UnlockServerMode

private val Context.appSettingsDataStore: DataStore<AppSettingsProto> by dataStore(
    fileName = "app_settings.pb",
    serializer = AppSettingsSerializer,
)

@Singleton
class AppSettingsStore @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope appScope: CoroutineScope,
) {
    private val store = context.appSettingsDataStore

    val settings: StateFlow<AppSettingsProto> = store.data.stateIn(
        scope = appScope,
        started = SharingStarted.Eagerly,
        initialValue = AppSettingsSerializer.defaultValue,
    )

    suspend fun update(transform: (AppSettingsProto) -> AppSettingsProto) {
        store.updateData(transform)
    }

    suspend fun updateCookies(musicU: String?, csrf: String?, nmtid: String?) {
        update { current ->
            current.toBuilder()
                .setMusicU(mergeCookieValue(current.musicU, musicU))
                .setCsrf(mergeCookieValue(current.csrf, csrf))
                .setNmtid(mergeCookieValue(current.nmtid, nmtid))
                .build()
        }
    }

    suspend fun updateCookiesFromHeader(cookieHeader: String) {
        val parsed = parseCookieHeader(cookieHeader)
        updateCookies(
            musicU = parsed["MUSIC_U"],
            csrf = parsed["__csrf"],
            nmtid = parsed["NMTID"],
        )
    }

    suspend fun setAccount(userId: Long, nickname: String, avatarUrl: String = "") {
        update { current ->
            current.toBuilder()
                .setAccountId(userId.toString())
                .setAccountNickname(nickname)
                .setAccountAvatarUrl(avatarUrl)
                .build()
        }
    }

    suspend fun clearAccount() {
        update { current ->
            current.toBuilder()
                .clearAccountId()
                .clearAccountNickname()
                .clearAccountAvatarUrl()
                .clearMusicU()
                .clearCsrf()
                .clearNmtid()
                .build()
        }
    }

    suspend fun setPlayMode(mode: PlayMode) {
        update { current -> current.toBuilder().setPlayMode(mode.rawValue).build() }
    }

    suspend fun setShowRoma(enabled: Boolean) {
        update { current -> current.toBuilder().setShowRoma(enabled).build() }
    }

    suspend fun setShowTranslation(enabled: Boolean) {
        update { current -> current.toBuilder().setShowTranslation(enabled).build() }
    }

    suspend fun setAutoPlay(enabled: Boolean) {
        update { current -> current.toBuilder().setAutoPlay(enabled).build() }
    }

    suspend fun setShowQueueCount(enabled: Boolean) {
        update { current -> current.toBuilder().setShowQueueCount(enabled).build() }
    }

    suspend fun setAllowConcurrentPlayback(enabled: Boolean) {
        update { current -> current.toBuilder().setAllowConcurrentPlayback(enabled).build() }
    }

    suspend fun setLyricFontScale(scale: Int) {
        update { current ->
            current.toBuilder()
                .setLyricFontScale(scale.coerceIn(85, 135))
                .build()
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        update { current -> current.toBuilder().setThemeMode(mode.rawValue).build() }
    }

    suspend fun setUnlockServerMode(mode: UnlockServerMode) {
        update { current ->
            current.toBuilder()
                .setUnlockServerMode(mode.rawValue)
                .setUnlockServerModeUserConfigured(true)
                .build()
        }
    }

    suspend fun setApiRoot(rawApiRoot: String): String {
        val normalized = normalizeApiRoot(rawApiRoot)
        update { current ->
            current.withConfiguredApiRoot(normalized)
        }
        return normalized
    }

    suspend fun currentApiRoot(): String = configuredApiRoot(store.data.first())

    suspend fun currentUnlockServerMode(): UnlockServerMode = configuredUnlockServerMode(store.data.first())

    fun buildCookieHeader(): String {
        val current = settings.value
        return buildList {
            if (current.musicU.isNotBlank()) add("MUSIC_U=${current.musicU}")
            if (current.csrf.isNotBlank()) add("__csrf=${current.csrf}")
            if (current.nmtid.isNotBlank()) add("NMTID=${current.nmtid}")
        }.joinToString("; ")
    }

    val apiRoot: String
        get() = configuredApiRoot(settings.value)

    val unlockServerMode: UnlockServerMode
        get() = configuredUnlockServerMode(settings.value)
}

internal fun configuredApiRoot(settings: AppSettingsProto): String {
    return settings.apiRoot.takeIf { settings.apiRootUserConfigured }.orEmpty()
}

internal fun configuredUnlockServerMode(settings: AppSettingsProto): UnlockServerMode {
    if (!settings.unlockServerModeUserConfigured) return UnlockServerMode.LOCAL
    return UnlockServerMode.fromRaw(settings.unlockServerMode)
}

internal fun AppSettingsProto.withConfiguredApiRoot(normalizedApiRoot: String): AppSettingsProto {
    val builder = toBuilder()
        .setApiRoot(normalizedApiRoot)
        .setApiRootUserConfigured(normalizedApiRoot.isNotBlank())
    if (normalizedApiRoot.isBlank()) {
        builder
            .setUnlockServerMode(UnlockServerMode.LOCAL.rawValue)
            .setUnlockServerModeUserConfigured(false)
    }
    return builder.build()
}

internal fun normalizeApiRoot(rawApiRoot: String): String {
    val trimmed = rawApiRoot.trim().trimEnd('/')
    if (trimmed.isBlank()) return ""
    val uri = runCatching { URI(trimmed) }.getOrNull()
    val scheme = uri?.scheme.orEmpty().lowercase()
    require(scheme == "http" || scheme == "https") {
        "API 根路径必须以 http:// 或 https:// 开头"
    }
    require(!uri?.host.isNullOrBlank()) {
        "API 根路径必须包含主机名"
    }
    return trimmed
}

internal fun mergeCookieValue(current: String, incoming: String?): String {
    return incoming?.takeIf { it.isNotBlank() } ?: current
}

internal fun parseCookieHeader(cookieHeader: String): Map<String, String> {
    return cookieHeader.split(';')
        .mapNotNull { item ->
            val trimmed = item.trim()
            if (trimmed.isBlank() || !trimmed.contains('=')) return@mapNotNull null
            val pair = trimmed.split('=', limit = 2)
            val key = pair.firstOrNull().orEmpty().trim()
            val value = pair.getOrElse(1) { "" }.trim()
            if (key.isBlank() || value.isBlank()) return@mapNotNull null
            key to value
        }
        .toMap()
}
