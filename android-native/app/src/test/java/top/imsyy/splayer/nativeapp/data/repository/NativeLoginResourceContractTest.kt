package top.imsyy.splayer.nativeapp.data.repository

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class NativeUnlockResourceContractTest {
    @Test
    fun `android native unlock source does not reference node or local server runtime`() {
        val sourceRoot = File("src/main/java/top/imsyy/splayer/nativeapp")
            .walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .joinToString("\n") { file -> file.readText() }

        assertFalse(sourceRoot.contains("ProcessBuilder"))
        assertFalse(sourceRoot.contains("node"))
        assertFalse(sourceRoot.contains("25884"))
        assertFalse(sourceRoot.contains("server/standalone"))
        assertFalse(sourceRoot.contains("VITE_SERVER_PORT"))
        assertFalse(sourceRoot.contains("NeteaseLoginRepository"))
        assertFalse(sourceRoot.contains("NativeNeteaseLoginClient"))
    }
}
