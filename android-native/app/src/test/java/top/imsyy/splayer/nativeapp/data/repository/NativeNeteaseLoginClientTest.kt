package top.imsyy.splayer.nativeapp.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeNeteaseLoginClientTest {
    @Test
    fun `fetchQrKey posts native eapi qrcode unikey request`() = runBlocking {
        val transport = FakeNativeNeteaseTransport(
            responses = mapOf(
                "https://interface.music.163.com/eapi/login/qrcode/unikey" to NativeNeteaseHttpResponse(
                    body = """{"code":200,"unikey":"native-key"}""",
                ),
            ),
        )
        val client = NativeNeteaseLoginClient(
            cookieProvider = { NativeNeteaseCookies() },
            transport = transport,
            secretKeyProvider = { "abcdefghijklmnop" },
            clock = { 123_456_789L },
        )

        val key = client.fetchQrKey()

        assertEquals("native-key", key)
        val request = transport.requests.single()
        assertEquals("https://interface.music.163.com/eapi/login/qrcode/unikey", request.url)
        assertTrue(request.form.containsKey("params"))
        assertTrue(request.headers.getValue("Cookie").contains("os=pc"))
        assertTrue(request.headers.getValue("User-Agent").contains("NeteaseMusic"))
    }

    @Test
    fun `checkQrState returns code and compact cookie header`() = runBlocking {
        val transport = FakeNativeNeteaseTransport(
            responses = mapOf(
                "https://interface.music.163.com/eapi/login/qrcode/client/login" to NativeNeteaseHttpResponse(
                    body = """{"code":803}""",
                    cookies = listOf(
                        "MUSIC_U=native-music-u; Path=/; HttpOnly",
                        "__csrf=native-csrf; Path=/",
                    ),
                ),
            ),
        )
        val client = NativeNeteaseLoginClient(
            cookieProvider = { NativeNeteaseCookies() },
            transport = transport,
            secretKeyProvider = { "abcdefghijklmnop" },
            clock = { 123_456_789L },
        )

        val state = client.checkQrState("native-key")

        assertEquals(803, state.code)
        assertEquals("MUSIC_U=native-music-u; __csrf=native-csrf", state.cookieHeader)
    }

    @Test
    fun `fetchLoginState reads login status then prefers user account profile`() = runBlocking {
        val transport = FakeNativeNeteaseTransport(
            responses = mapOf(
                "https://music.163.com/weapi/w/nuser/account/get" to NativeNeteaseHttpResponse(
                    body = """
                        {
                          "code": 200,
                          "account": { "id": 47 },
                          "profile": { "userId": 47, "nickname": "状态账号" }
                        }
                    """.trimIndent(),
                ),
                "https://music.163.com/weapi/nuser/account/get" to NativeNeteaseHttpResponse(
                    body = """
                        {
                          "code": 200,
                          "profile": {
                            "userId": 48,
                            "nickname": "资料账号",
                            "avatarUrl": "https://example.com/avatar.jpg"
                          }
                        }
                    """.trimIndent(),
                ),
            ),
        )
        val client = NativeNeteaseLoginClient(
            cookieProvider = { NativeNeteaseCookies(musicU = "native-music-u", csrf = "native-csrf") },
            transport = transport,
            secretKeyProvider = { "abcdefghijklmnop" },
            clock = { 123_456_789L },
        )

        val user = client.fetchLoginState()

        requireNotNull(user)
        assertEquals(48L, user.userId)
        assertEquals("资料账号", user.nickname)
        assertEquals(
            listOf(
                "https://music.163.com/weapi/w/nuser/account/get",
                "https://music.163.com/weapi/nuser/account/get",
            ),
            transport.requests.map { it.url },
        )
    }

    @Test
    fun `fetchQrImage returns data uri image compatible with remote qrimg response`() = runBlocking {
        val client = NativeNeteaseLoginClient(
            cookieProvider = { NativeNeteaseCookies() },
            transport = FakeNativeNeteaseTransport(),
            qrCodeGenerator = FakeQrCodeGenerator(),
            secretKeyProvider = { "abcdefghijklmnop" },
            clock = { 123_456_789L },
        )

        assertEquals(
            "data:image/png;base64,bmF0aXZlLXFy",
            client.fetchQrImage("native-key"),
        )
    }

    @Test
    fun `refreshLogin returns compact cookie header`() = runBlocking {
        val transport = FakeNativeNeteaseTransport(
            responses = mapOf(
                "https://interface.music.163.com/eapi/login/token/refresh" to NativeNeteaseHttpResponse(
                    body = """{"code":200}""",
                    cookies = listOf(
                        "MUSIC_U=refresh-music-u; Path=/; HttpOnly",
                        "NMTID=refresh-nmtid; Path=/",
                    ),
                ),
            ),
        )
        val client = NativeNeteaseLoginClient(
            cookieProvider = { NativeNeteaseCookies(musicU = "old", csrf = "csrf") },
            transport = transport,
            qrCodeGenerator = FakeQrCodeGenerator(),
            secretKeyProvider = { "abcdefghijklmnop" },
            clock = { 123_456_789L },
        )

        val result = client.refreshLogin()

        assertEquals(200, result.code)
        assertEquals("MUSIC_U=refresh-music-u; NMTID=refresh-nmtid", result.cookieHeader)
        assertEquals("https://interface.music.163.com/eapi/login/token/refresh", transport.requests.single().url)
    }

    @Test
    fun `logout posts native eapi logout request`() = runBlocking {
        val transport = FakeNativeNeteaseTransport(
            responses = mapOf(
                "https://interface.music.163.com/eapi/logout" to NativeNeteaseHttpResponse(
                    body = """{"code":200}""",
                ),
            ),
        )
        val client = NativeNeteaseLoginClient(
            cookieProvider = { NativeNeteaseCookies(musicU = "old", csrf = "csrf") },
            transport = transport,
            qrCodeGenerator = FakeQrCodeGenerator(),
            secretKeyProvider = { "abcdefghijklmnop" },
            clock = { 123_456_789L },
        )

        val result = client.logout()

        assertEquals(200, result.code)
        assertEquals("https://interface.music.163.com/eapi/logout", transport.requests.single().url)
    }

    @Test
    fun `sendCaptcha verifyCaptcha loginCellphone and countryList use native endpoints`() = runBlocking {
        val transport = FakeNativeNeteaseTransport(
            responses = mapOf(
                "https://music.163.com/weapi/sms/captcha/sent" to NativeNeteaseHttpResponse(
                    body = """{"code":200}""",
                ),
                "https://music.163.com/weapi/sms/captcha/verify" to NativeNeteaseHttpResponse(
                    body = """{"code":200}""",
                ),
                "https://music.163.com/weapi/w/login/cellphone" to NativeNeteaseHttpResponse(
                    body = """{"code":200}""",
                    cookies = listOf("MUSIC_U=phone-music-u; Path=/; HttpOnly"),
                ),
                "https://interface.music.163.com/eapi/lbs/countries/v1" to NativeNeteaseHttpResponse(
                    body = """{"code":200,"data":[{"label":"亚洲"}]}""",
                ),
            ),
        )
        val client = NativeNeteaseLoginClient(
            cookieProvider = { NativeNeteaseCookies(csrf = "native-csrf") },
            transport = transport,
            qrCodeGenerator = FakeQrCodeGenerator(),
            secretKeyProvider = { "abcdefghijklmnop" },
            clock = { 123_456_789L },
        )

        assertEquals(200, client.sendCaptcha("13800138000", "86").code)
        assertEquals(200, client.verifyCaptcha("13800138000", "123456", "86").code)
        assertEquals("MUSIC_U=phone-music-u", client.loginCellphone("13800138000", captcha = "123456").cookieHeader)
        assertTrue(client.fetchCountryCodeList().contains("亚洲"))
        assertEquals(
            listOf(
                "https://music.163.com/weapi/sms/captcha/sent",
                "https://music.163.com/weapi/sms/captcha/verify",
                "https://music.163.com/weapi/w/login/cellphone",
                "https://interface.music.163.com/eapi/lbs/countries/v1",
            ),
            transport.requests.map { it.url },
        )
    }
}

private class FakeNativeNeteaseTransport(
    private val responses: Map<String, NativeNeteaseHttpResponse> = emptyMap(),
) : NativeNeteaseHttpTransport {
    val requests = mutableListOf<NativeNeteaseRequest>()

    override suspend fun postForm(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String>,
    ): NativeNeteaseHttpResponse {
        requests += NativeNeteaseRequest(url = url, form = form, headers = headers)
        return responses[url] ?: error("missing response for $url")
    }
}

private data class NativeNeteaseRequest(
    val url: String,
    val form: Map<String, String>,
    val headers: Map<String, String>,
)

private class FakeQrCodeGenerator : NativeQrCodeGenerator {
    override fun toPngDataUri(content: String): String {
        assertEquals("https://music.163.com/login?codekey=native-key", content)
        return "data:image/png;base64,bmF0aXZlLXFy"
    }
}
