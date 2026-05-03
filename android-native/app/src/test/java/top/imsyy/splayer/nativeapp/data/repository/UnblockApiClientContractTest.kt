package top.imsyy.splayer.nativeapp.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import top.imsyy.splayer.nativeapp.model.UnlockServerMode

class UnblockApiClientContractTest {
    @Test
    fun `android local unblock server order matches remote order`() {
        assertEquals(listOf("bodian", "gequbao", "netease", "kuwo"), ANDROID_UNBLOCK_SERVERS)
    }

    @Test
    fun `switching unblock client uses local and remote with same server name`() = kotlinx.coroutines.runBlocking {
        var mode = UnlockServerMode.LOCAL
        val local = FakeUnblockClient("""{"code":200,"url":"local"}""")
        val remote = FakeUnblockClient("""{"code":200,"url":"remote"}""")
        val client = SwitchingUnblockApiClient(
            modeProvider = { mode },
            localClient = local,
            remoteClient = remote,
        )

        client.get("netease", mapOf("id" to "1"))
        mode = UnlockServerMode.EXTERNAL
        client.get("netease", mapOf("id" to "1"))

        assertEquals(listOf("netease"), local.calls)
        assertEquals(listOf("netease"), remote.calls)
    }

    @Test
    fun `native unblock implementation source mirrors desktop providers without node runtime`() {
        val source = java.io.File(
            "src/main/java/top/imsyy/splayer/nativeapp/data/repository/UnblockApiClient.kt",
        ).readText()

        assertTrue(source.contains("music-api.gdstudio.xyz"))
        assertTrue(source.contains("search.kuwo.cn"))
        assertTrue(source.contains("mobi.kuwo.cn"))
        assertTrue(source.contains("www.gequbao.com"))
        assertTrue(source.contains("bd-api.kuwo.cn"))
        assertTrue(source.contains("\"vipver\", \"1\""))
        assertTrue(source.contains("Dart/2.19"))
        assertTrue(source.contains("okhttp/3.10.0"))
        assertTrue(source.contains("kwplayer_ar_5.1.0.0_B_jiakong_vh.apk"))
        assertTrue(source.contains("kuwotest"))
        assertTrue(source.contains("ylzsxkwm"))
        assertTrue(!source.contains("ProcessBuilder"))
        assertTrue(!source.contains("node"))
        assertTrue(!source.contains("server/standalone"))
        assertTrue(!source.contains("nextLong(100_000_000_001L)"))
    }

    @Test
    fun `kuwo des query output matches desktop sample`() {
        val query = "corp=kuwo&source=kwplayer_ar_5.1.0.0_B_jiakong_vh.apk&p2p=1" +
            "&type=convert_url2&sig=0&format=mp3&rid=123456"

        assertEquals(
            "NI8S5evAnmHH4UXcuKKslDk4RFzONrTj1BvCoyMODMTUvpQlWnft3Eccd/NC9JdLFsVy/tvCgksRUHX6eSWQFEXV3zZS3yrqb2ASAUwCJKYLJ6ShYiU19qWNmoyxI7Y+nHiJEshlvqMdPf7smdueJQ==",
            KuwoDes.encryptQuery(query),
        )
    }
}

private class FakeUnblockClient(
    private val response: String,
) : UnblockApiClient {
    val calls = mutableListOf<String>()

    override suspend fun get(server: String, params: Map<String, String>): String {
        calls += server
        return response
    }
}
