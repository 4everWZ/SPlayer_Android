package top.imsyy.splayer.nativeapp.data.repository

import javax.inject.Provider
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import top.imsyy.splayer.nativeapp.data.api.SPlayerApiService
import top.imsyy.splayer.nativeapp.model.UserAccountUi

class NeteaseLoginRepositoryTest {
    @Test
    fun `blank api root uses native login provider for every login method`() = runBlocking {
        val nativeLogin = FakeLoginClient(
            loginState = UserAccountUi(userId = 44L, nickname = "原生账号", avatarUrl = "https://example.com/avatar.jpg"),
            qrKey = "native-key",
            qrImage = "data:image/png;base64,native",
            qrState = QrCheckState(code = 803, cookieHeader = "MUSIC_U=native"),
            mutation = LoginMutationResult(code = 200, body = """{"code":200}""", cookieHeader = "MUSIC_U=native"),
            countryList = """{"data":[]}""",
        )
        var nativeCreated = 0
        val repository = NeteaseLoginRepository(
            remoteRepository = SPlayerRemoteRepository(
                api = FakeLoginApiService(emptyMap()),
                apiRootProvider = { "" },
                requireApiRoot = true,
            ),
            apiRootProvider = { "" },
            nativeLoginClientProvider = Provider {
                nativeCreated += 1
                nativeLogin
            },
        )

        assertEquals(44L, repository.fetchLoginState()?.userId)
        assertEquals("native-key", repository.fetchQrKey())
        assertEquals("data:image/png;base64,native", repository.fetchQrImage("native-key"))
        assertEquals(803, repository.checkQrState("native-key").code)
        assertEquals(200, repository.refreshLogin().code)
        assertEquals(200, repository.logout().code)
        assertEquals(200, repository.sendCaptcha("13800138000").code)
        assertEquals(200, repository.verifyCaptcha("13800138000", "123456").code)
        assertEquals(200, repository.loginCellphone("13800138000", captcha = "123456").code)
        assertEquals("""{"data":[]}""", repository.fetchCountryCodeList())
        assertEquals(10, nativeCreated)
        assertEquals(1, nativeLogin.fetchLoginStateCalls)
        assertEquals(1, nativeLogin.fetchQrKeyCalls)
        assertEquals(1, nativeLogin.fetchQrImageCalls)
        assertEquals(1, nativeLogin.checkQrStateCalls)
        assertEquals(1, nativeLogin.refreshLoginCalls)
        assertEquals(1, nativeLogin.logoutCalls)
        assertEquals(1, nativeLogin.sendCaptchaCalls)
        assertEquals(1, nativeLogin.verifyCaptchaCalls)
        assertEquals(1, nativeLogin.loginCellphoneCalls)
        assertEquals(1, nativeLogin.fetchCountryCodeListCalls)
    }

    @Test
    fun `configured api root uses remote login methods and does not instantiate native provider`() = runBlocking {
        val apiRoot = "https://api.example.com/splayer"
        val api = FakeLoginApiService(
            responses = mapOf(
                "$apiRoot/netease/login/status" to """{"data":{"account":{"id":46},"profile":{"userId":46,"nickname":"远程账号"}}}""",
                "$apiRoot/netease/user/account" to """{"profile":{"userId":46,"nickname":"远程账号"}}""",
                "$apiRoot/netease/login/qr/key" to """{"data":{"unikey":"remote-key"}}""",
                "$apiRoot/netease/login/qr/create" to """{"data":{"qrimg":"data:image/png;base64,remote"}}""",
                "$apiRoot/netease/login/qr/check" to """{"code":803,"cookie":"MUSIC_U=remote"}""",
                "$apiRoot/netease/login/refresh" to """{"code":200,"cookie":"MUSIC_U=refresh"}""",
                "$apiRoot/netease/logout" to """{"code":200}""",
                "$apiRoot/netease/captcha/sent" to """{"code":200}""",
                "$apiRoot/netease/captcha/verify" to """{"code":200}""",
                "$apiRoot/netease/login/cellphone" to """{"code":200,"cookie":"MUSIC_U=phone"}""",
                "$apiRoot/netease/countries/code/list" to """{"data":[{"label":"亚洲"}]}""",
            ),
        )
        var nativeCreated = 0
        val repository = NeteaseLoginRepository(
            remoteRepository = SPlayerRemoteRepository(
                api = api,
                apiRootProvider = { apiRoot },
                requireApiRoot = true,
            ),
            apiRootProvider = { apiRoot },
            nativeLoginClientProvider = Provider {
                nativeCreated += 1
                FakeLoginClient()
            },
        )

        assertEquals(46L, repository.fetchLoginState()?.userId)
        assertEquals("remote-key", repository.fetchQrKey())
        assertEquals("data:image/png;base64,remote", repository.fetchQrImage("remote-key"))
        assertEquals("MUSIC_U=remote", repository.checkQrState("remote-key").cookieHeader)
        assertEquals("MUSIC_U=refresh", repository.refreshLogin().cookieHeader)
        assertEquals(200, repository.logout().code)
        assertEquals(200, repository.sendCaptcha("13800138000").code)
        assertEquals(200, repository.verifyCaptcha("13800138000", "123456").code)
        assertEquals("MUSIC_U=phone", repository.loginCellphone("13800138000", captcha = "123456").cookieHeader)
        assertEquals("""{"data":[{"label":"亚洲"}]}""", repository.fetchCountryCodeList())

        assertEquals(0, nativeCreated)
        assertFalse(api.calls.any { call -> call.url.contains("music.163.com") })
    }

    @Test
    fun `api root changes are observed on next login call`() = runBlocking {
        var apiRoot = ""
        val configuredRoot = "https://api.example.com/splayer"
        val nativeLogin = FakeLoginClient(qrKey = "native-key")
        val api = FakeLoginApiService(
            responses = mapOf(
                "$configuredRoot/netease/login/qr/key" to """{"data":{"unikey":"remote-key"}}""",
            ),
        )
        val repository = NeteaseLoginRepository(
            remoteRepository = SPlayerRemoteRepository(
                api = api,
                apiRootProvider = { apiRoot },
                requireApiRoot = true,
            ),
            apiRootProvider = { apiRoot },
            nativeLoginClientProvider = Provider { nativeLogin },
        )

        assertEquals("native-key", repository.fetchQrKey())
        apiRoot = configuredRoot
        assertEquals("remote-key", repository.fetchQrKey())
    }
}

private class FakeLoginApiService(
    private val responses: Map<String, String>,
) : SPlayerApiService {
    val calls = mutableListOf<LoginApiCall>()

    override suspend fun get(url: String, params: Map<String, String>): okhttp3.ResponseBody {
        calls += LoginApiCall(url = url, params = params)
        return (responses[url] ?: error("missing response for $url")).toResponseBody()
    }

    override suspend fun post(url: String, data: Map<String, String>, params: Map<String, String>) =
        error("unused")
}

private data class LoginApiCall(
    val url: String,
    val params: Map<String, String>,
)

private class FakeLoginClient(
    private val loginState: UserAccountUi? = null,
    private val qrKey: String = "",
    private val qrImage: String = "",
    private val qrState: QrCheckState = QrCheckState(code = 801),
    private val mutation: LoginMutationResult = LoginMutationResult(code = 200, body = """{"code":200}"""),
    private val countryList: String = "",
) : NeteaseLoginClient {
    var fetchLoginStateCalls = 0
    var fetchQrKeyCalls = 0
    var fetchQrImageCalls = 0
    var checkQrStateCalls = 0
    var refreshLoginCalls = 0
    var logoutCalls = 0
    var sendCaptchaCalls = 0
    var verifyCaptchaCalls = 0
    var loginCellphoneCalls = 0
    var fetchCountryCodeListCalls = 0

    override suspend fun fetchLoginState(): UserAccountUi? {
        fetchLoginStateCalls += 1
        return loginState
    }

    override suspend fun fetchQrKey(): String {
        fetchQrKeyCalls += 1
        return qrKey
    }

    override suspend fun fetchQrImage(key: String): String {
        fetchQrImageCalls += 1
        return qrImage
    }

    override suspend fun checkQrState(key: String): QrCheckState {
        checkQrStateCalls += 1
        return qrState
    }

    override suspend fun refreshLogin(): LoginMutationResult {
        refreshLoginCalls += 1
        return mutation
    }

    override suspend fun logout(): LoginMutationResult {
        logoutCalls += 1
        return mutation
    }

    override suspend fun sendCaptcha(phone: String, countryCode: String): LoginMutationResult {
        sendCaptchaCalls += 1
        return mutation
    }

    override suspend fun verifyCaptcha(phone: String, captcha: String, countryCode: String): LoginMutationResult {
        verifyCaptchaCalls += 1
        return mutation
    }

    override suspend fun loginCellphone(
        phone: String,
        captcha: String?,
        password: String?,
        countryCode: String,
    ): LoginMutationResult {
        loginCellphoneCalls += 1
        return mutation
    }

    override suspend fun fetchCountryCodeList(): String {
        fetchCountryCodeListCalls += 1
        return countryList
    }
}
