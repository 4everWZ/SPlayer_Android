package top.imsyy.splayer.nativeapp.data.repository

import org.junit.Assert.assertTrue
import org.junit.Test

class NativeQrCodeGeneratorTest {
    @Test
    fun `qr generator returns png data uri`() {
        val result = ZxingNativeQrCodeGenerator().toPngDataUri("https://music.163.com/login?codekey=test")

        assertTrue(result.startsWith("data:image/png;base64,"))
        assertTrue(result.length > "data:image/png;base64,".length)
    }
}
