package top.imsyy.splayer.nativeapp.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import top.imsyy.splayer.nativeapp.data.local.AppSettingsStore

@Singleton
class LyricsPreferences @Inject constructor(
    private val appSettingsStore: AppSettingsStore,
) {
    val showTranslation: Flow<Boolean> = appSettingsStore.settings.map { it.showTranslation }
    val showRomanized: Flow<Boolean> = appSettingsStore.settings.map { it.showRoma }
    val lyricFontScale: Flow<Float> = appSettingsStore.settings.map { it.lyricFontScale.coerceIn(85, 135) / 100f }

    suspend fun setShowRomanized(enabled: Boolean) {
        appSettingsStore.setShowRoma(enabled)
    }

    suspend fun setShowTranslation(enabled: Boolean) {
        appSettingsStore.setShowTranslation(enabled)
    }

    suspend fun setLyricFontScale(scale: Int) {
        appSettingsStore.setLyricFontScale(scale)
    }
}
