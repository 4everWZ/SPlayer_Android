package top.imsyy.splayer.nativeapp.data.repository

import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import top.imsyy.splayer.nativeapp.data.api.SPlayerApiService
import top.imsyy.splayer.nativeapp.model.UnlockServerMode

internal val ANDROID_UNBLOCK_SERVERS = listOf("bodian", "gequbao", "netease", "kuwo")

interface UnblockApiClient {
    suspend fun get(server: String, params: Map<String, String>): String
}

class RemoteUnblockApiClient(
    private val api: SPlayerApiService,
    private val apiRootProvider: suspend () -> String,
    private val requireApiRoot: Boolean,
) : UnblockApiClient {
    override suspend fun get(server: String, params: Map<String, String>): String {
        val url = resolveApiUrl("unblock/$server")
        return withContext(Dispatchers.IO) {
            api.get(url, params).string()
        }
    }

    private suspend fun resolveApiUrl(path: String): String {
        val apiRoot = apiRootProvider().trim().trimEnd('/')
        if (apiRoot.isBlank() && !requireApiRoot) return path
        check(apiRoot.isNotBlank()) { REMOTE_API_ROOT_REQUIRED_MESSAGE }
        return "$apiRoot/${path.trimStart('/')}"
    }
}

class SwitchingUnblockApiClient(
    private val modeProvider: suspend () -> UnlockServerMode,
    private val localClient: UnblockApiClient,
    private val remoteClient: UnblockApiClient,
) : UnblockApiClient {
    override suspend fun get(server: String, params: Map<String, String>): String {
        return activeClient().get(server, params)
    }

    private suspend fun activeClient(): UnblockApiClient {
        return when (modeProvider()) {
            UnlockServerMode.LOCAL -> localClient
            UnlockServerMode.EXTERNAL -> remoteClient
        }
    }
}

class NativeUnblockApiClient(
    private val okHttpClient: OkHttpClient,
) : UnblockApiClient {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun get(server: String, params: Map<String, String>): String {
        val result = runCatching {
            when (server) {
                "netease" -> resolveNetease(params)
                "kuwo" -> resolveKuwo(buildMatchInfo(params))
                "gequbao" -> resolveGequbao(buildMatchInfo(params))
                "bodian" -> resolveBodian(buildMatchInfo(params))
                else -> null
            }
        }.getOrNull()
        return result?.takeIf { it.url.isNotBlank() }?.toJson() ?: """{"code":404,"url":null}"""
    }

    private suspend fun resolveNetease(params: Map<String, String>): NativeUnblockResult? {
        val id = params["id"]?.trim().orEmpty()
        if (id.isBlank()) return null
        val body = get(
            url = "https://music-api.gdstudio.xyz/api.php",
            params = mapOf("types" to "url", "id" to id, "noCookie" to "true"),
        )
        val payload = json.parseToJsonElement(body).jsonObjectCompat()
        return NativeUnblockResult(
            url = payload.stringCompat("url"),
            br = payload.stringCompat("br"),
        )
    }

    private suspend fun resolveKuwo(match: NativeSongMatchInfo): NativeUnblockResult? {
        if (match.keyword.isBlank()) return null
        val songId = searchKuwoSongId(match, replaceDash = false, vipSearch = false) ?: return null
        val query = "corp=kuwo&source=kwplayer_ar_5.1.0.0_B_jiakong_vh.apk&p2p=1" +
            "&type=convert_url2&sig=0&format=mp3&rid=$songId"
        val body = get(
            url = "http://mobi.kuwo.cn/mobi.s",
            params = mapOf("f" to "kuwo", "q" to KuwoDes.encryptQuery(query)),
            headers = mapOf("User-Agent" to "okhttp/3.10.0"),
        )
        val url = Regex("""http[^\s$"]+""").find(body)?.value.orEmpty()
        return NativeUnblockResult(url = url)
    }

    private suspend fun resolveGequbao(match: NativeSongMatchInfo): NativeUnblockResult? {
        if (match.keyword.isBlank()) return null
        val searchBody = get("https://www.gequbao.com/s/${urlEncode(match.keyword)}")
        val musicId = Regex(
            """<a href="/music/(\d+)" target="_blank" class="music-link d-block">\s*([^<]*)""",
        ).findAll(searchBody).firstOrNull { result ->
            isSongMatch(
                resultName = result.groupValues.getOrNull(2).orEmpty().trim(),
                resultArtist = null,
                match = match,
            )
        }?.groupValues?.getOrNull(1) ?: return null
        val detailBody = get("https://www.gequbao.com/music/$musicId")
        val playId = Regex(""""play_id":"(.*?)"""").find(detailBody)?.groupValues?.getOrNull(1) ?: return null
        val headers = mapOf(
            "accept" to "application/json, text/javascript, */*; q=0.01",
            "content-type" to "application/x-www-form-urlencoded; charset=UTF-8",
            "x-requested-with" to "XMLHttpRequest",
            "cookie" to "server_name_session=${randomHex(16)}",
            "Referer" to "https://www.gequbao.com/music/$musicId",
        )
        val body = postForm(
            url = "https://www.gequbao.com/api/play-url",
            form = mapOf("id" to playId),
            headers = headers,
        )
        val payload = json.parseToJsonElement(body).jsonObjectCompat()
        val url = payload.objCompat("data").stringCompat("url")
        return NativeUnblockResult(url = url)
    }

    private suspend fun resolveBodian(match: NativeSongMatchInfo): NativeUnblockResult? {
        if (match.keyword.isBlank()) return null
        val songId = searchKuwoSongId(match, replaceDash = true, vipSearch = true) ?: return null
        val deviceId = randomDeviceId()
        val headers = mapOf(
            "user-agent" to "Dart/2.19 (dart:io)",
            "plat" to "ar",
            "channel" to "aliopen",
            "devid" to deviceId,
            "ver" to "3.9.0",
            "host" to "bd-api.kuwo.cn",
            "X-Forwarded-For" to "1.0.1.114",
        )
        sendBodianAdFreeRequest(deviceId)
        val audioUrl = signBodianUrl(
            "http://bd-api.kuwo.cn/api/play/music/v2/audioUrl?&br=320kmp3&musicId=$songId",
        )
        val body = get(audioUrl, headers = headers)
        val payload = json.parseToJsonElement(body).jsonObjectCompat()
        val url = payload.objCompat("data").stringCompat("audioUrl")
        return NativeUnblockResult(url = url)
    }

    private suspend fun searchKuwoSongId(
        match: NativeSongMatchInfo,
        replaceDash: Boolean,
        vipSearch: Boolean,
    ): String? {
        val keyword = if (replaceDash) match.keyword.replace(" - ", " ") else match.keyword
        val url = "http://search.kuwo.cn/r.s"
        val body = get(
            url = url,
            params = buildMap {
                put("correct", "1")
                if (vipSearch) put("vipver", "1")
                put("stype", "comprehensive")
                put("encoding", "utf8")
                put("rformat", "json")
                put("mobi", "1")
                put("show_copyright_off", "1")
                put("searchapi", "6")
                put("all", keyword)
            },
        )
        val payload = json.parseToJsonElement(body).jsonObjectCompat()
        val absList = payload.arrayCompat("content")
            .getOrNull(1)
            ?.jsonObjectCompat()
            ?.objCompat("musicpage")
            ?.arrayCompat("abslist")
            .orEmpty()
        return absList.firstNotNullOfOrNull { element ->
            val item = element.jsonObjectCompat()
            val rid = item.stringCompat("MUSICRID").removePrefix("MUSIC_")
            val name = item.stringCompat("SONGNAME")
            val artist = item.stringCompat("ARTIST")
            rid.takeIf { it.isNotBlank() && isSongMatch(name, artist, match) }
        }
    }

    private suspend fun sendBodianAdFreeRequest(deviceId: String) {
        val headers = mapOf(
            "user-agent" to "Dart/2.19 (dart:io)",
            "plat" to "ar",
            "channel" to "aliopen",
            "devid" to deviceId,
            "ver" to "3.9.0",
            "host" to "bd-api.kuwo.cn",
            "qimei36" to "1e9970cbcdc20a031dee9f37100017e1840e",
            "content-type" to "application/json; charset=utf-8",
        )
        runCatching {
            postRaw(
                url = "http://bd-api.kuwo.cn/api/service/advert/watch?uid=-1&token=&timestamp=1724306124436&sign=15a676d66285117ad714e8c8371691da",
                body = """{"type":5,"subType":5,"musicId":0,"adToken":""}""",
                headers = headers,
            )
        }
    }

    private suspend fun get(
        url: String,
        params: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
    ): String {
        val resolvedUrl = appendQuery(url, params)
        val request = Request.Builder().url(resolvedUrl).get().apply {
            headers.forEach { (key, value) -> header(key, value) }
        }.build()
        return execute(request)
    }

    private suspend fun postForm(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
    ): String {
        val body = FormBody.Builder().apply {
            form.forEach { (key, value) -> add(key, value) }
        }.build()
        val request = Request.Builder().url(url).post(body).apply {
            headers.forEach { (key, value) -> header(key, value) }
        }.build()
        return execute(request)
    }

    private suspend fun postRaw(
        url: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
    ): String {
        val requestBody = body.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).post(requestBody).apply {
            headers.forEach { (key, value) -> header(key, value) }
        }.build()
        return execute(request)
    }

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use ""
            response.body?.string().orEmpty()
        }
    }
}

private data class NativeSongMatchInfo(
    val keyword: String,
    val songName: String,
    val artist: String,
)

private data class NativeUnblockResult(
    val url: String,
    val br: String = "",
) {
    fun toJson(): String {
        val brField = if (br.isBlank()) "" else ""","br":${jsonString(br)}"""
        return """{"code":200,"url":${jsonString(url)}$brField}"""
    }
}

private fun buildMatchInfo(params: Map<String, String>): NativeSongMatchInfo {
    var songName = params["songName"].orEmpty()
    var artist = params["artist"].orEmpty()
    val keyword = params["keyword"].orEmpty()
    if (songName.isBlank() && keyword.isNotBlank()) {
        val lastIdx = keyword.lastIndexOf("-")
        if (lastIdx > 0) {
            songName = keyword.substring(0, lastIdx).trim()
            if (artist.isBlank()) {
                artist = keyword.substring(lastIdx + 1).trim()
            }
        } else {
            songName = keyword.trim()
        }
    }
    return NativeSongMatchInfo(keyword = keyword, songName = songName, artist = artist)
}

private fun isSongMatch(
    resultName: String,
    resultArtist: String?,
    match: NativeSongMatchInfo,
): Boolean {
    val normalizedResult = normalizeSongName(resultName)
    val normalizedOriginal = normalizeSongName(match.songName)
    if (normalizedResult.isBlank()) return false
    if (normalizedOriginal.isNotBlank() &&
        !normalizedResult.contains(normalizedOriginal) &&
        !normalizedOriginal.contains(normalizedResult)
    ) {
        return false
    }
    if (!resultArtist.isNullOrBlank() && match.artist.isNotBlank()) {
        val normalizedResultArtist = normalizeArtist(resultArtist)
        val normalizedOriginalArtist = normalizeArtist(match.artist)
        if (normalizedResultArtist.isNotBlank() && normalizedOriginalArtist.isNotBlank()) {
            if (!normalizedResultArtist.contains(normalizedOriginalArtist) &&
                !normalizedOriginalArtist.contains(normalizedResultArtist)
            ) {
                return false
            }
        }
    }
    return true
}

private fun normalizeSongName(name: String): String {
    return name.lowercase(Locale.ROOT)
        .replace(Regex("""[（(][^）)]*[）)]"""), "")
        .trim()
}

private fun normalizeArtist(artist: String): String {
    return artist.lowercase(Locale.ROOT)
        .replace(Regex("""[&/、，,;；]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
}

private fun appendQuery(url: String, params: Map<String, String>): String {
    if (params.isEmpty()) return url
    val joiner = if (url.contains("?")) "&" else "?"
    return url + joiner + params.entries.joinToString("&") { (key, value) ->
        "${urlEncode(key)}=${urlEncode(value)}"
    }
}

private fun signBodianUrl(rawUrl: String): String {
    val currentTime = System.currentTimeMillis()
    val withTimestamp = "$rawUrl&timestamp=$currentTime"
    val path = java.net.URI(rawUrl).path
    val filteredChars = withTimestamp
        .substringAfter("?")
        .replace(Regex("""[^a-zA-Z0-9]"""), "")
        .toList()
        .sorted()
        .joinToString("")
    val sign = md5("kuwotest$filteredChars$path")
    return "$withTimestamp&sign=$sign"
}

private fun md5(value: String): String {
    return MessageDigest.getInstance("MD5")
        .digest(value.toByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }
}

private fun randomDeviceId(): String {
    val maxExclusive = 100_000_000_001L
    return ((SecureRandom().nextLong() and Long.MAX_VALUE) % maxExclusive).toString()
}

private fun randomHex(byteCount: Int): String {
    val bytes = ByteArray(byteCount)
    SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { byte -> "%02x".format(byte) }
}

private fun urlEncode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

@OptIn(ExperimentalEncodingApi::class)
internal object KuwoDes {
    private val secretKey = "ylzsxkwm".encodeToByteArray()

    fun encryptQuery(query: String): String {
        return Base64.encode(crypt(query.encodeToByteArray(), secretKey, mode = 0))
    }

    // Kuwo mobile API 使用的历史 DES 变体，按 desktop kwDES.js 保持字节序兼容。
    private fun crypt(msg: ByteArray, key: ByteArray, mode: Int): ByteArray {
        var keyLong = 0L
        repeat(8) { i ->
            keyLong = ((key[i].toInt() and 0xff).toLong() shl (i * 8)) or keyLong
        }
        val blockCount = msg.size / 8
        val subKeys = LongArray(16)
        subKeys(keyLong, subKeys, mode)
        val inputBlocks = LongArray(blockCount)
        repeat(blockCount) { block ->
            repeat(8) { index ->
                inputBlocks[block] = ((msg[index + block * 8].toInt() and 0xff).toLong() shl (index * 8)) or
                    inputBlocks[block]
            }
        }
        val outputBlocks = LongArray((1 + 8 * (blockCount + 1)) / 8)
        repeat(blockCount) { index ->
            outputBlocks[index] = des64(subKeys, inputBlocks[index])
        }
        val rest = msg.copyOfRange(blockCount * 8, msg.size)
        var restLong = 0L
        repeat(msg.size % 8) { index ->
            restLong = ((rest[index].toInt() and 0xff).toLong() shl (index * 8)) or restLong
        }
        if (rest.isNotEmpty() || mode == 0) {
            outputBlocks[blockCount] = des64(subKeys, restLong)
        }
        val output = ByteArray(8 * outputBlocks.size)
        var outputIndex = 0
        outputBlocks.forEach { block ->
            repeat(8) { index ->
                output[outputIndex] = ((block ushr (index * 8)) and 0xff).toByte()
                outputIndex += 1
            }
        }
        return output
    }

    private fun subKeys(key: Long, keys: LongArray, mode: Int) {
        var transformedKey = bitTransform(arrayPc1, 56, key)
        repeat(16) { index ->
            transformedKey = ((transformedKey and arrayLsMask[arrayLs[index]]) shl (28 - arrayLs[index])) or
                ((transformedKey and arrayLsMask[arrayLs[index]].inv()) ushr arrayLs[index])
            keys[index] = bitTransform(arrayPc2, 64, transformedKey)
        }
        if (mode == 1) {
            repeat(8) { index ->
                val swap = keys[index]
                keys[index] = keys[15 - index]
                keys[15 - index] = swap
            }
        }
    }

    private fun des64(keys: LongArray, input: Long): Long {
        val pR = LongArray(8)
        val pSource = LongArray(2)
        var output = bitTransform(arrayIp, 64, input)
        pSource[0] = output and 0xffffffffL
        pSource[1] = output and -4294967296L ushr 32

        repeat(16) { index ->
            var sOut = 0L
            var right = bitTransform(arrayE, 64, pSource[1])
            right = right xor keys[index]
            repeat(8) { j ->
                pR[j] = (right ushr (j * 8)) and 255L
            }
            for (sbi in 7 downTo 0) {
                sOut = (sOut shl 4) or matrixNSBox[sbi][pR[sbi].toInt()].toLong()
            }
            right = bitTransform(arrayP, 32, sOut)
            val left = pSource[0]
            pSource[0] = pSource[1]
            pSource[1] = left xor right
        }
        pSource.reverse()
        output = ((pSource[1] shl 32) and -4294967296L) or (pSource[0] and 0xffffffffL)
        return bitTransform(arrayIp1, 64, output)
    }

    private fun bitTransform(table: LongArray, count: Int, source: Long): Long {
        var result = 0L
        repeat(count) { index ->
            val bitIndex = table[index]
            if (bitIndex >= 0 && (source and arrayMask[bitIndex.toInt()]) != 0L) {
                result = result or arrayMask[index]
            }
        }
        return result
    }

    private val arrayE = longArrayOf(31, 0, 1, 2, 3, 4, -1, -1, 3, 4, 5, 6, 7, 8, -1, -1, 7, 8, 9, 10, 11, 12, -1, -1, 11, 12, 13, 14, 15, 16, -1, -1, 15, 16, 17, 18, 19, 20, -1, -1, 19, 20, 21, 22, 23, 24, -1, -1, 23, 24, 25, 26, 27, 28, -1, -1, 27, 28, 29, 30, 31, 30, -1, -1)
    private val arrayIp = longArrayOf(57, 49, 41, 33, 25, 17, 9, 1, 59, 51, 43, 35, 27, 19, 11, 3, 61, 53, 45, 37, 29, 21, 13, 5, 63, 55, 47, 39, 31, 23, 15, 7, 56, 48, 40, 32, 24, 16, 8, 0, 58, 50, 42, 34, 26, 18, 10, 2, 60, 52, 44, 36, 28, 20, 12, 4, 62, 54, 46, 38, 30, 22, 14, 6)
    private val arrayIp1 = longArrayOf(39, 7, 47, 15, 55, 23, 63, 31, 38, 6, 46, 14, 54, 22, 62, 30, 37, 5, 45, 13, 53, 21, 61, 29, 36, 4, 44, 12, 52, 20, 60, 28, 35, 3, 43, 11, 51, 19, 59, 27, 34, 2, 42, 10, 50, 18, 58, 26, 33, 1, 41, 9, 49, 17, 57, 25, 32, 0, 40, 8, 48, 16, 56, 24)
    private val arrayLsMask = longArrayOf(0, 0x100001, 0x300003)
    private val arrayMask = LongArray(64) { index -> 1L shl index }.also { masks ->
        masks[masks.lastIndex] = Long.MIN_VALUE
    }
    private val arrayP = longArrayOf(15, 6, 19, 20, 28, 11, 27, 16, 0, 14, 22, 25, 4, 17, 30, 9, 1, 7, 23, 13, 31, 26, 2, 8, 18, 12, 29, 5, 21, 10, 3, 24)
    private val arrayPc1 = longArrayOf(56, 48, 40, 32, 24, 16, 8, 0, 57, 49, 41, 33, 25, 17, 9, 1, 58, 50, 42, 34, 26, 18, 10, 2, 59, 51, 43, 35, 62, 54, 46, 38, 30, 22, 14, 6, 61, 53, 45, 37, 29, 21, 13, 5, 60, 52, 44, 36, 28, 20, 12, 4, 27, 19, 11, 3)
    private val arrayPc2 = longArrayOf(13, 16, 10, 23, 0, 4, -1, -1, 2, 27, 14, 5, 20, 9, -1, -1, 22, 18, 11, 3, 25, 7, -1, -1, 15, 6, 26, 19, 12, 1, -1, -1, 40, 51, 30, 36, 46, 54, -1, -1, 29, 39, 50, 44, 32, 47, -1, -1, 43, 48, 38, 55, 33, 52, -1, -1, 45, 41, 49, 35, 28, 31, -1, -1)
    private val arrayLs = intArrayOf(1, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1)
    private val matrixNSBox = arrayOf(
        intArrayOf(14, 4, 3, 15, 2, 13, 5, 3, 13, 14, 6, 9, 11, 2, 0, 5, 4, 1, 10, 12, 15, 6, 9, 10, 1, 8, 12, 7, 8, 11, 7, 0, 0, 15, 10, 5, 14, 4, 9, 10, 7, 8, 12, 3, 13, 1, 3, 6, 15, 12, 6, 11, 2, 9, 5, 0, 4, 2, 11, 14, 1, 7, 8, 13),
        intArrayOf(15, 0, 9, 5, 6, 10, 12, 9, 8, 7, 2, 12, 3, 13, 5, 2, 1, 14, 7, 8, 11, 4, 0, 3, 14, 11, 13, 6, 4, 1, 10, 15, 3, 13, 12, 11, 15, 3, 6, 0, 4, 10, 1, 7, 8, 4, 11, 14, 13, 8, 0, 6, 2, 15, 9, 5, 7, 1, 10, 12, 14, 2, 5, 9),
        intArrayOf(10, 13, 1, 11, 6, 8, 11, 5, 9, 4, 12, 2, 15, 3, 2, 14, 0, 6, 13, 1, 3, 15, 4, 10, 14, 9, 7, 12, 5, 0, 8, 7, 13, 1, 2, 4, 3, 6, 12, 11, 0, 13, 5, 14, 6, 8, 15, 2, 7, 10, 8, 15, 4, 9, 11, 5, 9, 0, 14, 3, 10, 7, 1, 12),
        intArrayOf(7, 10, 1, 15, 0, 12, 11, 5, 14, 9, 8, 3, 9, 7, 4, 8, 13, 6, 2, 1, 6, 11, 12, 2, 3, 0, 5, 14, 10, 13, 15, 4, 13, 3, 4, 9, 6, 10, 1, 12, 11, 0, 2, 5, 0, 13, 14, 2, 8, 15, 7, 4, 15, 1, 10, 7, 5, 6, 12, 11, 3, 8, 9, 14),
        intArrayOf(2, 4, 8, 15, 7, 10, 13, 6, 4, 1, 3, 12, 11, 7, 14, 0, 12, 2, 5, 9, 10, 13, 0, 3, 1, 11, 15, 5, 6, 8, 9, 14, 14, 11, 5, 6, 4, 1, 3, 10, 2, 12, 15, 0, 13, 2, 8, 5, 11, 8, 0, 15, 7, 14, 9, 4, 12, 7, 10, 9, 1, 13, 6, 3),
        intArrayOf(12, 9, 0, 7, 9, 2, 14, 1, 10, 15, 3, 4, 6, 12, 5, 11, 1, 14, 13, 0, 2, 8, 7, 13, 15, 5, 4, 10, 8, 3, 11, 6, 10, 4, 6, 11, 7, 9, 0, 6, 4, 2, 13, 1, 9, 15, 3, 8, 15, 3, 1, 14, 12, 5, 11, 0, 2, 12, 14, 7, 5, 10, 8, 13),
        intArrayOf(4, 1, 3, 10, 15, 12, 5, 0, 2, 11, 9, 6, 8, 7, 6, 9, 11, 4, 12, 15, 0, 3, 10, 5, 14, 13, 7, 8, 13, 14, 1, 2, 13, 6, 14, 9, 4, 1, 2, 14, 11, 13, 5, 0, 1, 10, 8, 3, 0, 11, 3, 5, 9, 4, 15, 2, 7, 8, 12, 15, 10, 7, 6, 12),
        intArrayOf(13, 7, 10, 0, 6, 9, 5, 15, 8, 4, 3, 10, 11, 14, 12, 5, 2, 11, 9, 6, 15, 12, 0, 3, 4, 1, 14, 13, 1, 2, 7, 8, 1, 2, 12, 15, 10, 4, 0, 3, 13, 14, 6, 9, 7, 8, 9, 6, 15, 1, 5, 12, 3, 10, 14, 5, 8, 7, 11, 0, 4, 13, 2, 11),
    )
}

private fun kotlinx.serialization.json.JsonElement.jsonObjectCompat() =
    this as? kotlinx.serialization.json.JsonObject ?: kotlinx.serialization.json.JsonObject(emptyMap())

private fun kotlinx.serialization.json.JsonObject.objCompat(key: String) =
    this[key]?.jsonObjectCompat() ?: kotlinx.serialization.json.JsonObject(emptyMap())

private fun kotlinx.serialization.json.JsonObject.arrayCompat(key: String) =
    this[key] as? kotlinx.serialization.json.JsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())

private fun kotlinx.serialization.json.JsonObject.stringCompat(key: String): String =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()

private fun jsonString(value: String): String {
    return "\"" + value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n") + "\""
}
