package top.imsyy.splayer.nativeapp.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrCodeSupportTest {
    @Test
    fun `extractQrBase64Payload returns payload for data uri`() {
        val payload = extractQrBase64Payload("data:image/png;base64,Zm9vYmFy")

        assertEquals("Zm9vYmFy", payload)
    }

    @Test
    fun `extractQrBase64Payload ignores normal url`() {
        val payload = extractQrBase64Payload("https://example.com/qr.png")

        assertNull(payload)
    }

    @Test
    fun `resolveSettingsQrImageSizeDp makes login qr visibly larger without overflowing narrow screens`() {
        assertEquals(280, resolveSettingsQrImageSizeDp(360))
        assertEquals(196, resolveSettingsQrImageSizeDp(260))
    }
}
