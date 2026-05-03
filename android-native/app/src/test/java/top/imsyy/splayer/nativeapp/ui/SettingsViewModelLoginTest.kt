package top.imsyy.splayer.nativeapp.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsViewModelLoginTest {
    @Test
    fun `settings init does not call native login network before user action`() {
        val settingsViewModel = readSettingsViewModelSource()
        val initBlock = settingsViewModel.substringAfter("init {").substringBefore("fun refreshAccount")

        assertFalse(initBlock.contains("refreshAccount()"))
        assertFalse(initBlock.contains("loginRepository."))
    }

    @Test
    fun `stop qr login and view model clearing cancel qr polling`() {
        val settingsViewModel = readSettingsViewModelSource()
        val stopQrLogin = settingsViewModel.substringAfter("fun stopQrLogin()").substringBefore("override fun onCleared")
        val onCleared = settingsViewModel.substringAfter("override fun onCleared()").substringBefore("fun setShowTranslation")

        assertTrue(stopQrLogin.contains("qrPollingJob?.cancel()"))
        assertTrue(stopQrLogin.contains("qrPollingJob = null"))
        assertTrue(onCleared.contains("stopQrLogin()"))
    }

    @Test
    fun `settings login surface keeps qr login only`() {
        val settingsScreen = File("src/main/java/top/imsyy/splayer/nativeapp/ui/screen/SettingsScreen.kt").readText()
        val settingsViewModel = readSettingsViewModelSource()

        assertFalse(settingsScreen.contains("手机号"))
        assertFalse(settingsScreen.contains("验证码"))
        assertFalse(settingsScreen.contains("国家码"))
        assertFalse(settingsViewModel.contains("sendCaptcha"))
        assertFalse(settingsViewModel.contains("loginWithCaptcha"))
    }

    @Test
    fun `settings surface exposes concurrent playback switch`() {
        val settingsScreen = File("src/main/java/top/imsyy/splayer/nativeapp/ui/screen/SettingsScreen.kt").readText()
        val viewModels = readViewModelsSource()

        assertTrue(settingsScreen.contains("允许与其他应用同时播放"))
        assertTrue(settingsScreen.contains("state.allowConcurrentPlayback"))
        assertTrue(viewModels.contains("val allowConcurrentPlayback: Boolean = false"))
        assertTrue(viewModels.contains("fun setAllowConcurrentPlayback"))
    }

    private fun readSettingsViewModelSource(): String {
        return readViewModelsSource().substringAfter("class SettingsViewModel")
    }

    private fun readViewModelsSource(): String {
        return File("src/main/java/top/imsyy/splayer/nativeapp/ui/ViewModels.kt").readText()
    }
}
