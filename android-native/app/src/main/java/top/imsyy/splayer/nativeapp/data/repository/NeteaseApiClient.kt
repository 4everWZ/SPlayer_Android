package top.imsyy.splayer.nativeapp.data.repository

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import top.imsyy.splayer.nativeapp.data.api.SPlayerApiService
import top.imsyy.splayer.nativeapp.data.local.AppSettingsStore
import top.imsyy.splayer.nativeapp.model.UnlockServerMode

internal const val REMOTE_API_ROOT_REQUIRED_MESSAGE = "远程 API 模式需要 API 根路径"

internal val ANDROID_USED_NETEASE_PATHS = listOf(
    "login/qr/key",
    "login/qr/create",
    "login/qr/check",
    "login/status",
    "logout",
    "user/account",
    "homepage/block/page",
    "personalized",
    "recommend/songs",
    "top/song",
    "top/artists",
    "album/new",
    "toplist/detail",
    "dj/recommend",
    "dj/toplist",
    "dj/category/recommend",
    "dj/detail",
    "dj/program",
    "user/detail",
    "likelist",
    "user/playlist",
    "album/sublist",
    "user/record",
    "playlist/detail",
    "playlist/track/all",
    "album",
    "album/detail/dynamic",
    "search/default",
    "search/hot/detail",
    "cloudsearch",
    "lyric/new",
    "lyric/ttml",
    "comment/hot",
    "comment/new",
    "playmode/intelligence/list",
    "song/url/v1",
)

interface NeteaseApiClient {
    suspend fun get(path: String, params: Map<String, String> = emptyMap()): String

    suspend fun canRequestOfficialApi(): Boolean
}

class RemoteNeteaseApiClient(
    private val api: SPlayerApiService,
    private val apiRootProvider: suspend () -> String,
    private val requireApiRoot: Boolean,
) : NeteaseApiClient {
    override suspend fun get(path: String, params: Map<String, String>): String {
        val url = resolveApiUrl("netease/$path")
        return withContext(Dispatchers.IO) {
            api.get(url, params).string()
        }
    }

    override suspend fun canRequestOfficialApi(): Boolean = !requireApiRoot || apiRootProvider().isNotBlank()

    private suspend fun resolveApiUrl(path: String): String {
        val apiRoot = apiRootProvider().trim().trimEnd('/')
        if (apiRoot.isBlank() && !requireApiRoot) return path
        check(apiRoot.isNotBlank()) { REMOTE_API_ROOT_REQUIRED_MESSAGE }
        return "$apiRoot/${path.trimStart('/')}"
    }
}

class SwitchingNeteaseApiClient(
    private val modeProvider: suspend () -> UnlockServerMode,
    private val localClient: NeteaseApiClient,
    private val remoteClient: NeteaseApiClient,
) : NeteaseApiClient {
    override suspend fun get(path: String, params: Map<String, String>): String {
        return activeClient().get(path, params)
    }

    override suspend fun canRequestOfficialApi(): Boolean = activeClient().canRequestOfficialApi()

    private suspend fun activeClient(): NeteaseApiClient {
        return when (modeProvider()) {
            UnlockServerMode.LOCAL -> localClient
            UnlockServerMode.EXTERNAL -> remoteClient
        }
    }
}

@Singleton
class NativeNeteaseApiClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val appSettingsStore: AppSettingsStore,
) : NeteaseApiClient {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun get(path: String, params: Map<String, String>): String {
        val endpoint = NativeNeteaseEndpoint.fromRepositoryPath(path)
            ?: error("本地 API 暂未覆盖 Netease endpoint: $path")
        return when (endpoint.path) {
            "login/qr/create" -> buildQrCreateResponse(params)
            "playlist/track/all" -> fetchPlaylistTrackAll(params)
            "lyric/ttml" -> fetchTtmlLyric(params)
            else -> requestEndpoint(endpoint, params)
        }
    }

    override suspend fun canRequestOfficialApi(): Boolean = true

    private suspend fun requestEndpoint(
        endpoint: NativeNeteaseEndpoint,
        params: Map<String, String>,
    ): String {
        val upstreamPath = endpoint.resolveUri(params)
        val data = endpoint.buildData(params)
        val response = requestMusicApi(upstreamPath, data, endpoint.crypto)
        return when (endpoint.path) {
            "login/qr/key" -> """{"data":${response.body},"code":200}"""
            "login/qr/check" -> injectCookieIntoJson(response.body, response.cookieHeader)
            "login/status" -> {
                if (response.body.contains(""""code":200""")) """{"data":${response.body}}""" else response.body
            }
            "user/detail" -> response.body.replace("avatarImgId_str", "avatarImgIdStr")
            else -> response.body
        }
    }

    private suspend fun fetchPlaylistTrackAll(params: Map<String, String>): String {
        val id = params.required("id")
        val limit = params["limit"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1000
        val offset = params["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val detailEndpoint = requireNotNull(NativeNeteaseEndpoint.fromRepositoryPath("playlist/detail"))
        val detailResponse = requestEndpoint(detailEndpoint, mapOf("id" to id, "s" to params.getOrDefault("s", "8")))
        val root = json.parseToJsonElement(detailResponse).jsonObjectCompat()
        val ids = root.objCompat("playlist")
            .arrayCompat("trackIds")
            .drop(offset)
            .take(limit)
            .mapNotNull { element -> element.jsonObjectCompat().longCompat("id").takeIf { it > 0L } }
        if (ids.isEmpty()) return """{"songs":[]}"""
        val detailData = mapOf(
            "c" to ids.joinToString(prefix = "[", postfix = "]") { trackId -> """{"id":$trackId}""" },
        )
        return requestMusicApi("/api/v3/song/detail", detailData, NativeNeteaseCrypto.DEFAULT).body
    }

    private fun buildQrCreateResponse(params: Map<String, String>): String {
        val key = params.required("key")
        val qrUrl = "https://music.163.com/login?codekey=${urlEncode(key)}"
        val qrImg = if (params["qrimg"] == "true") {
            buildQrPngDataUri(qrUrl)
        } else {
            ""
        }
        return """{"code":200,"data":{"qrurl":"$qrUrl","qrimg":"$qrImg"}}"""
    }

    private suspend fun fetchTtmlLyric(params: Map<String, String>): String {
        val id = params.required("id")
        val request = Request.Builder()
            .url("https://amll-ttml-db.stevexmh.net/ncm/$id")
            .get()
            .build()
        return withContext(Dispatchers.IO) {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) "" else response.body?.string().orEmpty()
            }
        }
    }

    private suspend fun requestMusicApi(
        uri: String,
        data: Map<String, Any?>,
        crypto: NativeNeteaseCrypto,
    ): NativeNeteaseResponse {
        val cookie = appSettingsStore.buildCookieHeader()
        val packet = NativeNeteaseCryptoEngine.encrypt(
            uri = uri,
            data = data,
            crypto = crypto,
            cookieHeader = cookie,
        )
        val request = Request.Builder()
            .url(packet.url)
            .post(packet.formBody)
            .header("User-Agent", packet.userAgent)
            .header("Referer", packet.referer)
            .header("Cookie", packet.cookieHeader)
            .build()
        return withContext(Dispatchers.IO) {
            okHttpClient.newCall(request).execute().use { response ->
                NativeNeteaseResponse(
                    body = response.body?.string().orEmpty(),
                    cookieHeader = response.headers("Set-Cookie").joinToString(";") { cookieHeader ->
                        cookieHeader.substringBefore(";")
                    },
                )
            }
        }
    }

    private fun injectCookieIntoJson(body: String, cookieHeader: String): String {
        val trimmed = body.trim()
        if (cookieHeader.isBlank()) return trimmed
        if (trimmed.endsWith("}")) {
            return trimmed.dropLast(1) + ""","cookie":${jsonString(cookieHeader)}}"""
        }
        return trimmed
    }
}

internal enum class NativeNeteaseCrypto {
    DEFAULT,
    WEAPI,
}

internal data class NativeNeteaseEndpoint(
    val path: String,
    val crypto: NativeNeteaseCrypto,
    val uriResolver: (Map<String, String>) -> String,
    val dataBuilder: (Map<String, String>) -> Map<String, Any?>,
) {
    fun resolveUri(params: Map<String, String>): String = uriResolver(params)

    fun buildData(params: Map<String, String>): Map<String, Any?> = dataBuilder(params)

    companion object {
        private val endpoints = listOf(
            endpoint("login/qr/key", "/api/login/qrcode/unikey") { mapOf("type" to 3) },
            endpoint("login/qr/create", "/local/login/qr/create") { p -> mapOf("key" to p.required("key")) },
            endpoint("login/qr/check", "/api/login/qrcode/client/login") { p ->
                mapOf("key" to p.required("key"), "type" to 3)
            },
            endpoint("login/status", "/api/w/nuser/account/get", NativeNeteaseCrypto.WEAPI),
            endpoint("logout", "/api/logout"),
            endpoint("user/account", "/api/nuser/account/get", NativeNeteaseCrypto.WEAPI),
            endpoint("homepage/block/page", "/api/homepage/block/page", NativeNeteaseCrypto.WEAPI) { p ->
                mapOf("refresh" to (p["refresh"] == "true")) + optional(p, "cursor")
            },
            endpoint("personalized", "/api/personalized/playlist", NativeNeteaseCrypto.WEAPI) { p ->
                mapOf("limit" to p.getOrDefault("limit", "30"), "total" to true, "n" to 1000)
            },
            endpoint("recommend/songs", "/api/v3/discovery/recommend/songs", NativeNeteaseCrypto.WEAPI),
            endpoint("top/song", "/api/v1/discovery/new/songs", NativeNeteaseCrypto.WEAPI) { p ->
                mapOf("areaId" to p.getOrDefault("type", "0"), "total" to true)
            },
            endpoint("top/artists", "/api/artist/top", NativeNeteaseCrypto.WEAPI) { p ->
                mapOf("limit" to p.getOrDefault("limit", "50"), "offset" to p.getOrDefault("offset", "0"), "total" to true)
            },
            endpoint("album/new", "/api/album/new", NativeNeteaseCrypto.WEAPI) { p ->
                mapOf(
                    "limit" to p.getOrDefault("limit", "30"),
                    "offset" to p.getOrDefault("offset", "0"),
                    "total" to true,
                    "area" to p.getOrDefault("area", "ALL"),
                )
            },
            endpoint("toplist/detail", "/api/toplist/detail", NativeNeteaseCrypto.WEAPI),
            endpoint("dj/recommend", "/api/djradio/recommend/v1", NativeNeteaseCrypto.WEAPI),
            endpoint("dj/toplist", "/api/djradio/toplist", NativeNeteaseCrypto.WEAPI) { p ->
                mapOf(
                    "limit" to p.getOrDefault("limit", "100"),
                    "offset" to p.getOrDefault("offset", "0"),
                    "type" to if (p["type"] == "hot") "1" else "0",
                )
            },
            endpoint("dj/category/recommend", "/api/djradio/home/category/recommend", NativeNeteaseCrypto.WEAPI),
            endpoint("dj/detail", "/api/djradio/v2/get", NativeNeteaseCrypto.WEAPI) { p -> mapOf("id" to p.required("rid")) },
            endpoint("dj/program", "/api/dj/program/byradio", NativeNeteaseCrypto.WEAPI) { p ->
                mapOf(
                    "radioId" to p.required("rid"),
                    "limit" to p.getOrDefault("limit", "30"),
                    "offset" to p.getOrDefault("offset", "0"),
                    "asc" to p.getOrDefault("asc", "false"),
                )
            },
            endpoint("user/detail", NativeNeteaseCrypto.WEAPI, { p -> "/api/v1/user/detail/${p.required("uid")}" }),
            endpoint("likelist", "/api/song/like/get") { p -> mapOf("uid" to p.required("uid")) },
            endpoint("user/playlist", "/api/user/playlist", NativeNeteaseCrypto.WEAPI) { p ->
                mapOf(
                    "uid" to p.required("uid"),
                    "limit" to p.getOrDefault("limit", "30"),
                    "offset" to p.getOrDefault("offset", "0"),
                    "includeVideo" to true,
                )
            },
            endpoint("album/sublist", "/api/album/sublist", NativeNeteaseCrypto.WEAPI) { p ->
                mapOf("limit" to p.getOrDefault("limit", "25"), "offset" to p.getOrDefault("offset", "0"), "total" to true)
            },
            endpoint("user/record", "/api/v1/play/record", NativeNeteaseCrypto.WEAPI) { p ->
                mapOf("uid" to p.required("uid"), "type" to p.getOrDefault("type", "0"))
            },
            endpoint("playlist/detail", "/api/v6/playlist/detail") { p ->
                mapOf("id" to p.required("id"), "n" to "100000", "s" to p.getOrDefault("s", "8"))
            },
            endpoint("playlist/track/all", "/api/v6/playlist/detail"),
            endpoint("album", NativeNeteaseCrypto.WEAPI, { p -> "/api/v1/album/${p.required("id")}" }),
            endpoint("album/detail/dynamic", "/api/album/detail/dynamic", NativeNeteaseCrypto.WEAPI) { p ->
                mapOf("id" to p.required("id"))
            },
            endpoint("search/default", "/api/search/defaultkeyword/get"),
            endpoint("search/hot/detail", "/api/hotsearchlist/get", NativeNeteaseCrypto.WEAPI),
            endpoint("cloudsearch", "/api/cloudsearch/pc") { p ->
                mapOf(
                    "s" to p.required("keywords"),
                    "type" to p.getOrDefault("type", "1"),
                    "limit" to p.getOrDefault("limit", "30"),
                    "offset" to p.getOrDefault("offset", "0"),
                    "total" to true,
                )
            },
            endpoint("lyric/new", "/api/song/lyric/v1") { p ->
                mapOf(
                    "id" to p.required("id"),
                    "cp" to false,
                    "tv" to 0,
                    "lv" to 0,
                    "rv" to 0,
                    "kv" to 0,
                    "yv" to 0,
                    "ytv" to 0,
                    "yrv" to 0,
                )
            },
            endpoint("lyric/ttml", "/api/lyric/ttml") { p -> mapOf("id" to p.required("id")) },
            endpoint("comment/hot", NativeNeteaseCrypto.WEAPI, { p ->
                "/api/v1/resource/hotcomments/${resourceType(p)}${p.required("id")}"
            }) { p ->
                mapOf(
                    "rid" to p.required("id"),
                    "limit" to p.getOrDefault("limit", "20"),
                    "offset" to p.getOrDefault("offset", "0"),
                    "beforeTime" to p.getOrDefault("before", "0"),
                )
            },
            endpoint("comment/new", "/api/v2/resource/comments") { p ->
                val pageSize = p.getOrDefault("pageSize", "20")
                val pageNo = p.getOrDefault("pageNo", "1")
                val sortType = p.getOrDefault("sortType", "99")
                val cursor = if (sortType == "3") p.getOrDefault("cursor", "0") else {
                    ((pageNo.toIntOrNull() ?: 1) - 1).coerceAtLeast(0).times(pageSize.toIntOrNull() ?: 20).toString()
                }
                mapOf(
                    "threadId" to "${resourceType(p)}${p.required("id")}",
                    "pageNo" to pageNo,
                    "showInner" to true,
                    "pageSize" to pageSize,
                    "cursor" to cursor,
                    "sortType" to sortType,
                )
            },
            endpoint("playmode/intelligence/list", "/api/playmode/intelligence/list") { p ->
                mapOf(
                    "songId" to p.required("id"),
                    "type" to "fromPlayOne",
                    "playlistId" to p.required("pid"),
                    "startMusicId" to p.getOrDefault("sid", p.required("id")),
                    "count" to p.getOrDefault("count", "1"),
                )
            },
            endpoint("song/url/v1", "/api/song/enhance/player/url/v1") { p ->
                buildMap {
                    put("ids", "[${p.required("id")}]")
                    put("level", p.required("level"))
                    put("encodeType", "flac")
                    if (p["level"] == "sky") put("immerseType", "c51")
                }
            },
        )
        private val byPath = endpoints.associateBy { it.path }

        fun fromRepositoryPath(path: String): NativeNeteaseEndpoint? = byPath[path]

        private fun endpoint(
            path: String,
            uri: String,
            crypto: NativeNeteaseCrypto = NativeNeteaseCrypto.DEFAULT,
            dataBuilder: (Map<String, String>) -> Map<String, Any?> = { emptyMap<String, Any?>() },
        ) = NativeNeteaseEndpoint(path, crypto, { uri }, dataBuilder)

        private fun endpoint(
            path: String,
            crypto: NativeNeteaseCrypto,
            uriResolver: (Map<String, String>) -> String,
            dataBuilder: (Map<String, String>) -> Map<String, Any?> = { emptyMap<String, Any?>() },
        ) = NativeNeteaseEndpoint(path, crypto, uriResolver, dataBuilder)
    }
}

private data class NativeNeteaseResponse(
    val body: String,
    val cookieHeader: String = "",
)

private data class NativeNeteaseRequestPacket(
    val url: String,
    val formBody: FormBody,
    val cookieHeader: String,
    val userAgent: String,
    val referer: String,
)

private object NativeNeteaseCryptoEngine {
    private const val API_DOMAIN = "https://interface.music.163.com"
    private const val DOMAIN = "https://music.163.com"
    private const val PRESET_KEY = "0CoJUm6Qyw8W8jud"
    private const val EAPI_KEY = "e82ckenh8dichen8"
    private const val IV = "0102030405060708"
    private const val BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    private const val PUBLIC_KEY_BASE64 =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ37BUrX/aKzmFbt7clFSs6sXqHauqKWqdtLkF2KexO40H1YTX8z2lSgBBOAxLsvaklV8k4cBFK9snQXE9/DDaFt6Rr7iVZMldczhC0JNgTz+SHXT6CBHuX3e9SdB1Ua44oncaTWz7OBGLbCiK45wIDAQAB"
    private val random = SecureRandom()

    fun encrypt(
        uri: String,
        data: Map<String, Any?>,
        crypto: NativeNeteaseCrypto,
        cookieHeader: String,
    ): NativeNeteaseRequestPacket {
        return when (crypto) {
            NativeNeteaseCrypto.WEAPI -> encryptWeapi(uri, data, cookieHeader)
            NativeNeteaseCrypto.DEFAULT -> encryptEapi(uri, data, cookieHeader)
        }
    }

    private fun encryptWeapi(
        uri: String,
        data: Map<String, Any?>,
        cookieHeader: String,
    ): NativeNeteaseRequestPacket {
        val payload = buildJsonObject(data + ("csrf_token" to extractCookie(cookieHeader, "__csrf")))
        val secretKey = randomBase62(16)
        val params = aesCbcBase64(aesCbcBase64(payload, PRESET_KEY), secretKey)
        val encSecKey = rsaEncrypt(secretKey.reversed())
        return NativeNeteaseRequestPacket(
            url = "$DOMAIN/weapi/${uri.removePrefix("/api/")}",
            formBody = FormBody.Builder()
                .add("params", params)
                .add("encSecKey", encSecKey)
                .build(),
            cookieHeader = cookieHeader,
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            referer = DOMAIN,
        )
    }

    private fun encryptEapi(
        uri: String,
        data: Map<String, Any?>,
        cookieHeader: String,
    ): NativeNeteaseRequestPacket {
        val header = linkedMapOf(
            "osver" to "14",
            "deviceId" to "SPLAYER_NATIVE_ANDROID",
            "os" to "iPhone OS",
            "appver" to "9.0.90",
            "versioncode" to "140",
            "mobilename" to "",
            "buildver" to (System.currentTimeMillis() / 1000).toString(),
            "resolution" to "1920x1080",
            "__csrf" to extractCookie(cookieHeader, "__csrf"),
            "channel" to "distribution",
            "requestId" to "${System.currentTimeMillis()}_${random.nextInt(10000).toString().padStart(4, '0')}",
        )
        extractCookie(cookieHeader, "MUSIC_U").takeIf { it.isNotBlank() }?.let { header["MUSIC_U"] = it }
        val payload = buildJsonObject(data + ("header" to header) + ("e_r" to false))
        val digest = md5("nobody${uri}use${payload}md5forencrypt")
        val encryptedData = "$uri-36cd479b6b5-$payload-36cd479b6b5-$digest"
        return NativeNeteaseRequestPacket(
            url = "$API_DOMAIN/eapi/${uri.removePrefix("/api/")}",
            formBody = FormBody.Builder()
                .add("params", aesEcbHex(encryptedData, EAPI_KEY))
                .build(),
            cookieHeader = buildHeaderCookie(header),
            userAgent = "NeteaseMusic 9.0.90/5038 (iPhone; iOS 16.2; zh_CN)",
            referer = DOMAIN,
        )
    }

    private fun aesCbcBase64(text: String, key: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(IV.toByteArray(Charsets.UTF_8)),
        )
        return Base64.encodeToString(cipher.doFinal(text.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun aesEcbHex(text: String, key: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"))
        return cipher.doFinal(text.toByteArray(Charsets.UTF_8)).joinToString("") { byte ->
            "%02X".format(byte.toInt() and 0xff)
        }
    }

    private fun rsaEncrypt(text: String): String {
        val spec = X509EncodedKeySpec(Base64.decode(PUBLIC_KEY_BASE64, Base64.DEFAULT))
        val keyFactory = java.security.KeyFactory.getInstance("RSA")
        val publicKey = keyFactory.generatePublic(spec) as RSAPublicKey
        val message = BigInteger(1, text.toByteArray(Charsets.UTF_8))
        return message.modPow(publicKey.publicExponent, publicKey.modulus)
            .toString(16)
            .padStart(256, '0')
    }

    private fun md5(text: String): String {
        return MessageDigest.getInstance("MD5")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun randomBase62(length: Int): String {
        return buildString {
            repeat(length) {
                append(BASE62[random.nextInt(BASE62.length)])
            }
        }
    }

    private fun buildHeaderCookie(header: Map<String, String>): String {
        return header.entries.joinToString("; ") { (key, value) -> "${urlEncode(key)}=${urlEncode(value)}" }
    }
}

private fun Map<String, String>.required(key: String): String {
    return get(key)?.takeIf { it.isNotBlank() } ?: error("缺少 Netease 参数: $key")
}

private fun optional(params: Map<String, String>, key: String): Map<String, String> {
    return params[key]?.takeIf { it.isNotBlank() }?.let { mapOf(key to it) }.orEmpty()
}

private fun resourceType(params: Map<String, String>): String {
    return when (params.getOrDefault("type", "0")) {
        "0" -> "R_SO_4_"
        "1" -> "R_MV_5_"
        "2" -> "A_PL_0_"
        "3" -> "R_AL_3_"
        "4" -> "A_DJ_1_"
        "5" -> "R_VI_62_"
        "6" -> "A_EV_2_"
        "7" -> "A_DR_14_"
        else -> "R_SO_4_"
    }
}

private fun extractCookie(cookieHeader: String, key: String): String {
    return cookieHeader.split(';')
        .firstOrNull { it.substringBefore('=').trim() == key }
        ?.substringAfter('=', "")
        ?.trim()
        .orEmpty()
}

private fun urlEncode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

private fun jsonString(value: String): String {
    return "\"" + value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n") + "\""
}

private fun buildJsonObject(values: Map<String, Any?>): String {
    return values.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
        val jsonValue = toJsonValue(value)
        "${jsonString(key)}:$jsonValue"
    }
}

private fun toJsonValue(value: Any?): String {
    return when (value) {
        null -> "null"
        is Boolean -> value.toString()
        is Number -> value.toString()
        is Map<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            buildJsonObject(value.entries.associate { (key, item) -> key.toString() to item })
        }
        is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]") { item -> toJsonValue(item) }
        else -> jsonString(value.toString())
    }
}

private fun buildQrPngDataUri(content: String): String {
    val matrix = QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        360,
        360,
        mapOf(EncodeHintType.MARGIN to 1),
    )
    val bitmap = android.graphics.Bitmap.createBitmap(matrix.width, matrix.height, android.graphics.Bitmap.Config.ARGB_8888)
    for (y in 0 until matrix.height) {
        for (x in 0 until matrix.width) {
            bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    val output = ByteArrayOutputStream()
    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
    return "data:image/png;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
}

private fun kotlinx.serialization.json.JsonElement.jsonObjectCompat() =
    this as? kotlinx.serialization.json.JsonObject ?: kotlinx.serialization.json.JsonObject(emptyMap())

private fun kotlinx.serialization.json.JsonObject.objCompat(key: String) =
    this[key]?.jsonObjectCompat() ?: kotlinx.serialization.json.JsonObject(emptyMap())

private fun kotlinx.serialization.json.JsonObject.arrayCompat(key: String) =
    this[key] as? kotlinx.serialization.json.JsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())

private fun kotlinx.serialization.json.JsonObject.longCompat(key: String): Long =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull() ?: 0L
