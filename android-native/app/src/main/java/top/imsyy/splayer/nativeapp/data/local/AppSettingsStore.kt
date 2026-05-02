package top.imsyy.splayer.nativeapp.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
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
        update { current -> current.toBuilder().setUnlockServerMode(mode.rawValue).build() }
    }

    fun buildCookieHeader(): String {
        val current = settings.value
        return buildList {
            if (current.musicU.isNotBlank()) add("MUSIC_U=${current.musicU}")
            if (current.csrf.isNotBlank()) add("__csrf=${current.csrf}")
            if (current.nmtid.isNotBlank()) add("NMTID=${current.nmtid}")
        }.joinToString("; ")
    }

    val apiRoot: String
        get() = settings.value.apiRoot.ifBlank { AppSettingsSerializer.defaultValue.apiRoot }

    val unlockServerMode: UnlockServerMode
        get() = UnlockServerMode.fromRaw(settings.value.unlockServerMode)
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
