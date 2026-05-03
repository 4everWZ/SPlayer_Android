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

    private fun readSettingsViewModelSource(): String {
        val source = File("src/main/java/top/imsyy/splayer/nativeapp/ui/ViewModels.kt").readText()
        return source.substringAfter("class SettingsViewModel")
    }
}
