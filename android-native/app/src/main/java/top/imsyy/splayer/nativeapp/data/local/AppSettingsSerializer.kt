package top.imsyy.splayer.nativeapp.data.local

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream
import top.imsyy.splayer.nativeapp.AppSettingsProto
import top.imsyy.splayer.nativeapp.BuildConfig
import top.imsyy.splayer.nativeapp.model.ThemeMode
import top.imsyy.splayer.nativeapp.model.UnlockServerMode

object AppSettingsSerializer : Serializer<AppSettingsProto> {
    override val defaultValue: AppSettingsProto =
        AppSettingsProto.newBuilder()
            .setApiRoot(BuildConfig.API_ROOT)
            .setPlayMode(0)
            .setShowTranslation(true)
            .setShowRoma(false)
            .setAutoPlay(true)
            .setShowQueueCount(true)
            .setLyricFontScale(100)
            .setThemeMode(ThemeMode.DARK.rawValue)
            .setUnlockServerMode(UnlockServerMode.LOCAL.rawValue)
            .build()

    override suspend fun readFrom(input: InputStream): AppSettingsProto {
        try {
            return AppSettingsProto.parseFrom(input)
        } catch (error: Exception) {
            throw CorruptionException("读取设置失败", error)
        }
    }

    override suspend fun writeTo(t: AppSettingsProto, output: OutputStream) {
        t.writeTo(output)
    }
}
