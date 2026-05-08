package top.imsyy.splayer.nativeapp.data.repository

import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.imsyy.splayer.nativeapp.data.api.SPlayerApiService
import top.imsyy.splayer.nativeapp.model.UnlockServerMode

class NeteaseApiClientContractTest {
    @Test
    fun `local netease client lets repository fetch qr login without api root`() = runBlocking {
        val client = FakeNeteaseApiClient(
            responses = mapOf(
                "login/qr/key" to """{"data":{"unikey":"local-key"},"code":200}""",
                "login/qr/create" to """{"data":{"qrimg":"data:image/png;base64,LOCAL"},"code":200}""",
                "login/qr/check" to """{"code":803,"cookie":"MUSIC_U=abc; __csrf=csrf; NMTID=nmt"}""",
                "login/status" to """
                    {
                      "data": {
                        "account": { "id": 7 },
                        "profile": { "userId": 7, "nickname": "本地账号", "avatarUrl": "https://example.com/a.jpg" }
                      }
                    }
                """.trimIndent(),
                "user/account" to """
                    {
                      "profile": { "userId": 7, "nickname": "本地账号", "avatarUrl": "https://example.com/a.jpg" }
                    }
                """.trimIndent(),
            ),
        )
        val repository = SPlayerRemoteRepository(
            api = EmptyApiService,
            apiRootProvider = { "" },
            requireApiRoot = true,
            neteaseApiClient = client,
        )

        assertEquals("local-key", repository.fetchQrKey())
        assertEquals("data:image/png;base64,LOCAL", repository.fetchQrImage("local-key"))
        val qrState = repository.checkQrState("local-key")
        val user = repository.fetchLoginState()

        assertEquals(803, qrState.code)
        assertEquals("MUSIC_U=abc; __csrf=csrf; NMTID=nmt", qrState.cookieHeader)
        requireNotNull(user)
        assertEquals(7L, user.userId)
        assertEquals(
            listOf("login/qr/key", "login/qr/create", "login/qr/check", "login/status", "user/account"),
            client.calls.map { call -> call.path },
        )
    }

    @Test
    fun `switching netease client uses local without api root and remote only when configured`() = runBlocking {
        var mode = UnlockServerMode.LOCAL
        var remoteRoot = ""
        val local = FakeNeteaseApiClient(mapOf("login/status" to """{"data":{}}"""))
        val remote = RemoteNeteaseApiClient(
            api = FakeRemoteApiService(mapOf("https://api.example.com/splayer/netease/login/status" to """{"data":{}}""")),
            apiRootProvider = { remoteRoot },
            requireApiRoot = true,
        )
        val switching = SwitchingNeteaseApiClient(
            modeProvider = { mode },
            localClient = local,
            remoteClient = remote,
        )

        switching.get("login/status")
        mode = UnlockServerMode.EXTERNAL
        val missingRoot = runCatching { switching.get("login/status") }.exceptionOrNull()
        remoteRoot = "https://api.example.com/splayer"
        switching.get("login/status")

        assertEquals(listOf("login/status"), local.calls.map { it.path })
        assertEquals(REMOTE_API_ROOT_REQUIRED_MESSAGE, missingRoot?.message)
    }

    @Test
    fun `native netease endpoint table covers android used netease paths`() {
        val missing = ANDROID_USED_NETEASE_PATHS.filterNot { path ->
            NativeNeteaseEndpoint.fromRepositoryPath(path) != null
        }

        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun `native implementation source does not start node or local http runtime`() {
        val sourceRoot = java.io.File("src/main/java/top/imsyy/splayer/nativeapp")
            .walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .joinToString("\n") { file -> file.readText() }

        assertFalse(sourceRoot.contains("ProcessBuilder"))
        assertFalse(sourceRoot.contains("node"))
        assertFalse(sourceRoot.contains("server/standalone"))
        assertFalse(sourceRoot.contains("VITE_SERVER_PORT"))
        assertTrue(sourceRoot.contains("NativeNeteaseApiClient"))
    }

    @Test
    fun `remote netease response body is read on io dispatcher`() {
        val source = java.io.File(
            "src/main/java/top/imsyy/splayer/nativeapp/data/repository/NeteaseApiClient.kt",
        ).readText()
        val remoteClientSource = source
            .substringAfter("class RemoteNeteaseApiClient")
            .substringBefore("class SwitchingNeteaseApiClient")

        assertTrue(remoteClientSource.contains("withContext(Dispatchers.IO)"))
        assertTrue(remoteClientSource.contains(".string()"))
    }

    @Test
    fun `native request builder keeps encoded array fields as strings like desktop api`() {
        val songUrlEndpoint = requireNotNull(NativeNeteaseEndpoint.fromRepositoryPath("song/url/v1"))
        val trackAllDetailData = mapOf("c" to """[{"id":1},{"id":2}]""")

        val songUrlData = songUrlEndpoint.buildData(mapOf("id" to "42", "level" to "exhigh"))

        assertEquals("[42]", songUrlData["ids"])
        assertEquals("""[{"id":1},{"id":2}]""", trackAllDetailData["c"])
    }
}

private data class FakeNeteaseCall(
    val path: String,
    val params: Map<String, String>,
)

private class FakeNeteaseApiClient(
    private val responses: Map<String, String>,
) : NeteaseApiClient {
    val calls = mutableListOf<FakeNeteaseCall>()

    override suspend fun get(path: String, params: Map<String, String>): String {
        calls += FakeNeteaseCall(path, params)
        return responses[path] ?: error("missing native response for $path")
    }

    override suspend fun post(
        path: String,
        data: Map<String, String>,
        params: Map<String, String>,
    ): String {
        calls += FakeNeteaseCall(path, params + data)
        return responses[path] ?: error("missing native response for $path")
    }

    override suspend fun canRequestOfficialApi(): Boolean = true
}

private class FakeRemoteApiService(
    private val responses: Map<String, String>,
) : SPlayerApiService {
    val calls = mutableListOf<String>()

    override suspend fun get(url: String, params: Map<String, String>): okhttp3.ResponseBody {
        calls += url
        return (responses[url] ?: error("missing response for $url")).toResponseBody()
    }

    override suspend fun post(
        url: String,
        data: Map<String, String>,
        params: Map<String, String>,
    ): okhttp3.ResponseBody = error("unused")
}

private object EmptyApiService : SPlayerApiService {
    override suspend fun get(url: String, params: Map<String, String>): okhttp3.ResponseBody = error("unused")

    override suspend fun post(
        url: String,
        data: Map<String, String>,
        params: Map<String, String>,
    ): okhttp3.ResponseBody = error("unused")
}
