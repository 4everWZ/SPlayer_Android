package top.imsyy.splayer.nativeapp.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import top.imsyy.splayer.nativeapp.model.UserAccountUi

class NativeNeteaseLoginClient(
    private val cookieProvider: () -> NativeNeteaseCookies,
    private val transport: NativeNeteaseHttpTransport,
    private val qrCodeGenerator: NativeQrCodeGenerator = ZxingNativeQrCodeGenerator(),
    private val secretKeyProvider: () -> String = ::randomSecretKey,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) : NeteaseLoginClient {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun fetchLoginState(): UserAccountUi? {
        val cookies = cookieProvider()
        val loginStatus = postWeapi(
            uri = "/api/w/nuser/account/get",
            payloadJson = nativeJsonObject("csrf_token" to cookies.csrf),
        )
        val statusPayload = parse(loginStatus)
        val statusData = statusPayload.obj("data").takeIf { it.isNotEmpty() } ?: statusPayload
        val statusUser = statusData.obj("account").toNativeUser(profile = statusData.obj("profile"))
        val accountUser = runCatching {
            val account = postWeapi(
                uri = "/api/nuser/account/get",
                payloadJson = nativeJsonObject("csrf_token" to cookies.csrf),
            )
            parse(account).obj("profile").toNativeUser()
        }.getOrNull()
        return accountUser ?: statusUser
    }

    override suspend fun fetchQrKey(): String {
        val body = postEapi("/api/login/qrcode/unikey", """{"type":3}""")
        val payload = parse(body)
        return payload.obj("data").string("unikey").ifBlank { payload.string("unikey") }
    }

    override suspend fun fetchQrImage(key: String): String {
        return qrCodeGenerator.toPngDataUri("https://music.163.com/login?codekey=$key")
    }

    override suspend fun checkQrState(key: String): QrCheckState {
        val response = postEapiWithResponse(
            uri = "/api/login/qrcode/client/login",
            payloadJson = nativeJsonObject("key" to key, "type" to 3),
        )
        return QrCheckState(
            code = parse(response.body).int("code"),
            cookieHeader = compactCookieHeader(response.cookies),
        )
    }

    override suspend fun refreshLogin(): LoginMutationResult {
        return postEapiWithResponse("/api/login/token/refresh", "{}").toMutationResult()
    }

    override suspend fun logout(): LoginMutationResult {
        return postEapiWithResponse("/api/logout", "{}").toMutationResult()
    }

    override suspend fun sendCaptcha(phone: String, countryCode: String): LoginMutationResult {
        val body = nativeJsonObject(
            "ctcode" to countryCode,
            "secrete" to "music_middleuser_pclogin",
            "cellphone" to phone,
            "csrf_token" to cookieProvider().csrf,
        )
        return postWeapiWithResponse("/api/sms/captcha/sent", body).toMutationResult()
    }

    override suspend fun verifyCaptcha(phone: String, captcha: String, countryCode: String): LoginMutationResult {
        val body = nativeJsonObject(
            "ctcode" to countryCode,
            "cellphone" to phone,
            "captcha" to captcha,
            "csrf_token" to cookieProvider().csrf,
        )
        return postWeapiWithResponse("/api/sms/captcha/verify", body).toMutationResult()
    }

    override suspend fun loginCellphone(
        phone: String,
        captcha: String?,
        password: String?,
        countryCode: String,
    ): LoginMutationResult {
        require(!captcha.isNullOrBlank() || !password.isNullOrBlank()) {
            "手机号登录需要验证码或密码"
        }
        val body = if (!captcha.isNullOrBlank()) {
            nativeJsonObject(
                "type" to "1",
                "https" to "true",
                "phone" to phone,
                "countrycode" to countryCode,
                "captcha" to captcha,
                "remember" to "true",
                "csrf_token" to cookieProvider().csrf,
            )
        } else {
            nativeJsonObject(
                "type" to "1",
                "https" to "true",
                "phone" to phone,
                "countrycode" to countryCode,
                "password" to NativeNeteaseCrypto.md5(password.orEmpty()),
                "remember" to "true",
                "csrf_token" to cookieProvider().csrf,
            )
        }
        return postWeapiWithResponse("/api/w/login/cellphone", body).toMutationResult()
    }

    override suspend fun fetchCountryCodeList(): String {
        return postEapi("/api/lbs/countries/v1", "{}")
    }

    private suspend fun postEapi(uri: String, payloadJson: String): String {
        return postEapiWithResponse(uri, payloadJson).body
    }

    private suspend fun postWeapi(uri: String, payloadJson: String): String {
        return postWeapiWithResponse(uri, payloadJson).body
    }

    private suspend fun postEapiWithResponse(uri: String, payloadJson: String): NativeNeteaseHttpResponse {
        val url = "$INTERFACE_DOMAIN/eapi/${uri.removePrefix("/api/")}"
        return postForm(url, NativeNeteaseCrypto.eapi(uri, payloadJson))
    }

    private suspend fun postWeapiWithResponse(uri: String, payloadJson: String): NativeNeteaseHttpResponse {
        val url = "$MUSIC_DOMAIN/weapi/${uri.removePrefix("/api/")}"
        return postForm(url, NativeNeteaseCrypto.weapi(payloadJson, secretKeyProvider()))
    }

    private suspend fun postForm(url: String, form: Map<String, String>): NativeNeteaseHttpResponse {
        return withContext(dispatcher) {
            transport.postForm(
                url = url,
                form = form,
                headers = nativeHeaders(cookieProvider(), clock),
            )
        }
    }

    private fun parse(body: String): JsonObject {
        return json.parseToJsonElement(body).jsonObject
    }

    private fun NativeNeteaseHttpResponse.toMutationResult(): LoginMutationResult {
        val payload = runCatching { parse(body) }.getOrNull() ?: JsonObject(emptyMap())
        return LoginMutationResult(
            code = payload.int("code"),
            body = body,
            cookieHeader = payload.string("cookie").ifBlank { compactCookieHeader(cookies) },
        )
    }

    private companion object {
        const val INTERFACE_DOMAIN = "https://interface.music.163.com"
        const val MUSIC_DOMAIN = "https://music.163.com"
    }
}

class OkHttpNativeNeteaseHttpTransport(
    private val okHttpClient: OkHttpClient,
) : NativeNeteaseHttpTransport {
    override suspend fun postForm(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String>,
    ): NativeNeteaseHttpResponse {
        val body = FormBody.Builder().apply {
            form.forEach { (key, value) -> add(key, value) }
        }.build()
        val request = Request.Builder()
            .url(url)
            .post(body)
            .apply {
                headers.forEach { (key, value) -> header(key, value) }
            }
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            return NativeNeteaseHttpResponse(
                body = response.body?.string().orEmpty(),
                cookies = response.headers("Set-Cookie"),
            )
        }
    }
}

internal fun nativeHeaders(
    cookies: NativeNeteaseCookies,
    clock: () -> Long,
): Map<String, String> {
    return mapOf(
        "User-Agent" to "NeteaseMusic 9.0.90/5038 (iPhone; iOS 16.2; zh_CN)",
        "Referer" to "https://music.163.com",
        "Cookie" to buildNativeCookieHeader(cookies, clock),
    )
}

internal fun buildNativeCookieHeader(
    cookies: NativeNeteaseCookies,
    clock: () -> Long,
): String {
    return buildList {
        add("os=pc")
        add("appver=9.0.90")
        add("deviceId=splayer-native")
        add("__remember_me=true")
        add("_ntes_nuid=${clock()}")
        if (cookies.musicU.isNotBlank()) add("MUSIC_U=${cookies.musicU}")
        if (cookies.csrf.isNotBlank()) add("__csrf=${cookies.csrf}")
        if (cookies.nmtid.isNotBlank()) add("NMTID=${cookies.nmtid}")
    }.joinToString("; ")
}

internal fun compactCookieHeader(cookies: List<String>): String {
    return cookies.mapNotNull { cookie ->
        val pair = cookie.substringBefore(";").split("=", limit = 2)
        val key = pair.firstOrNull().orEmpty().trim()
        val value = pair.getOrElse(1) { "" }.trim()
        if (key in setOf("MUSIC_U", "__csrf", "NMTID") && value.isNotBlank()) {
            "$key=$value"
        } else {
            null
        }
    }.joinToString("; ")
}

internal fun nativeJsonObject(vararg pairs: Pair<String, Any?>): String {
    return pairs.joinToString(prefix = "{", postfix = "}") { (key, value) ->
        val encoded = when (value) {
            null -> "null"
            is Number, is Boolean -> value.toString()
            else -> "\"${value.toString().replace("\\", "\\\\").replace("\"", "\\\"")}\""
        }
        "\"$key\":$encoded"
    }
}

internal fun randomSecretKey(): String {
    val source = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    return (1..16).map { source.random() }.joinToString("")
}

private val JsonElement.obj: JsonObject
    get() = this as? JsonObject ?: JsonObject(emptyMap())

private fun JsonObject.obj(key: String): JsonObject = this[key] as? JsonObject ?: JsonObject(emptyMap())

private fun JsonObject.array(key: String): List<JsonElement> = (this[key] as? JsonArray)?.toList().orEmpty()

private fun JsonObject.string(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

private fun JsonObject.long(key: String): Long = (this[key] as? JsonPrimitive)?.longOrNull ?: 0L

private fun JsonObject.int(key: String): Int = (this[key] as? JsonPrimitive)?.intOrNull ?: 0

private fun JsonObject.toNativeUser(profile: JsonObject = this): UserAccountUi? {
    val userId = long("id").takeIf { it > 0L }
        ?: long("userId").takeIf { it > 0L }
        ?: profile.long("id").takeIf { it > 0L }
        ?: profile.long("userId").takeIf { it > 0L }
        ?: return null
    return UserAccountUi(
        userId = userId,
        nickname = profile.string("nickname").ifBlank { string("nickname") },
        avatarUrl = profile.string("avatarUrl").ifBlank { string("avatarUrl") },
        backgroundUrl = profile.string("backgroundUrl")
            .ifBlank { profile.string("backgroundImageUrl") }
            .ifBlank { string("backgroundUrl") }
            .ifBlank { string("backgroundImageUrl") },
        signature = profile.string("signature").ifBlank { string("signature") },
        level = int("level").takeIf { it > 0 } ?: profile.int("level"),
        followCount = profile.int("follows"),
        followerCount = profile.int("followeds"),
        listenCount = int("listenSongs").takeIf { it > 0 } ?: profile.int("listenSongs"),
    )
}
