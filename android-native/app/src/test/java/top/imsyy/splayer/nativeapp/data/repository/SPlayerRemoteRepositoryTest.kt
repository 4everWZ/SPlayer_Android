package top.imsyy.splayer.nativeapp.data.repository

import java.util.Collections
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.imsyy.splayer.nativeapp.data.api.SPlayerApiService
import top.imsyy.splayer.nativeapp.model.TrackItem

class SPlayerRemoteRepositoryTest {
    @Test
    fun `fetchDiscoveryHome aggregates playlist songs artists albums and toplist`() = runBlocking {
        val repository = SPlayerRemoteRepository(
            api = FakeApiService(
                responses = mapOf(
                    "netease/personalized" to """
                    {
                      "result": [
                        { "id": 1, "name": "推荐歌单", "picUrl": "https://example.com/p1.jpg", "trackCount": 20 }
                      ]
                    }
                    """.trimIndent(),
                    "netease/top/song" to """
                    {
                      "data": [
                        {
                          "id": 101,
                          "name": "新歌一",
                          "dt": 180000,
                          "artists": [{ "name": "歌手甲" }],
                          "album": { "name": "专辑甲", "picUrl": "https://example.com/a1.jpg" }
                        }
                      ]
                    }
                    """.trimIndent(),
                    "netease/top/artists" to """
                    {
                      "artists": [
                        { "id": 201, "name": "热门歌手", "img1v1Url": "https://example.com/ar1.jpg", "musicSize": 10 }
                      ]
                    }
                    """.trimIndent(),
                    "netease/album/new" to """
                    {
                      "albums": [
                        { "id": 301, "name": "新专辑", "picUrl": "https://example.com/al1.jpg", "size": 8 }
                      ]
                    }
                    """.trimIndent(),
                    "netease/toplist/detail" to """
                    {
                      "list": [
                        { "id": 401, "name": "飙升榜", "coverImgUrl": "https://example.com/t1.jpg", "trackCount": 100 }
                      ]
                    }
                    """.trimIndent(),
                ),
            ),
        )

        val result = repository.fetchDiscoveryHome()

        assertEquals(1, result.recommendedPlaylists.size)
        assertEquals("推荐歌单", result.recommendedPlaylists.first().name)
        assertEquals(1, result.newSongs.size)
        assertEquals("新歌一", result.newSongs.first().name)
        assertEquals(1, result.topArtists.size)
        assertEquals("热门歌手", result.topArtists.first().name)
        assertEquals(1, result.newAlbums.size)
        assertEquals("新专辑", result.newAlbums.first().name)
        assertEquals(1, result.topPlaylists.size)
        assertEquals("飙升榜", result.topPlaylists.first().name)
    }

    @Test
    fun `fetchPodcastHome aggregates recommend top list and categories`() = runBlocking {
        val repository = SPlayerRemoteRepository(
            api = FakeApiService(
                responses = mapOf(
                    "netease/dj/recommend" to """
                    {
                      "djRadios": [
                        {
                          "id": 501,
                          "name": "推荐播客",
                          "picUrl": "https://example.com/r1.jpg",
                          "programCount": 12
                        }
                      ]
                    }
                    """.trimIndent(),
                    "netease/dj/toplist" to """
                    {
                      "toplist": [
                        {
                          "id": 502,
                          "name": "热门播客",
                          "picUrl": "https://example.com/r2.jpg",
                          "programCount": 6
                        }
                      ]
                    }
                    """.trimIndent(),
                    "netease/dj/category/recommend" to """
                    {
                      "data": [
                        {
                          "categoryId": 601,
                          "categoryName": "情感",
                          "radios": [
                            {
                              "id": 503,
                              "name": "情感电台",
                              "picUrl": "https://example.com/r3.jpg",
                              "programCount": 9
                            }
                          ]
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
            ),
        )

        val result = repository.fetchPodcastHome()

        assertEquals(1, result.recommendedRadios.size)
        assertEquals("推荐播客", result.recommendedRadios.first().name)
        assertEquals(1, result.hotRadios.size)
        assertEquals("热门播客", result.hotRadios.first().name)
        assertEquals(1, result.categories.size)
        assertEquals("情感", result.categories.first().name)
        assertEquals(1, result.categories.first().radios.size)
        assertEquals("情感电台", result.categories.first().radios.first().name)
    }

    @Test
    fun `fetchRadioDetail returns metadata and program list`() = runBlocking {
        val repository = SPlayerRemoteRepository(
            api = FakeApiService(
                responses = mapOf(
                    "netease/dj/detail" to """
                    {
                      "data": {
                        "id": 701,
                        "name": "深夜电台",
                        "picUrl": "https://example.com/radio.jpg",
                        "desc": "节目介绍",
                        "programCount": 2
                      }
                    }
                    """.trimIndent(),
                    "netease/dj/program" to """
                    {
                      "programs": [
                        {
                          "id": 801,
                          "name": "节目一",
                          "duration": 240000,
                          "coverUrl": "https://example.com/p1.jpg",
                          "mainSong": {
                            "name": "节目一",
                            "duration": 240000,
                            "album": { "name": "深夜电台", "picUrl": "https://example.com/p1.jpg" },
                            "artists": [{ "name": "主播甲" }]
                          },
                          "dj": { "nickname": "主播甲", "brand": "深夜电台" }
                        },
                        {
                          "id": 802,
                          "name": "节目二",
                          "duration": 260000,
                          "coverUrl": "https://example.com/p2.jpg",
                          "mainSong": {
                            "name": "节目二",
                            "duration": 260000,
                            "album": { "name": "深夜电台", "picUrl": "https://example.com/p2.jpg" },
                            "artists": [{ "name": "主播乙" }]
                          },
                          "dj": { "nickname": "主播乙", "brand": "深夜电台" }
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
            ),
        )

        val result = repository.fetchRadioDetail(701L)

        assertEquals(701L, result.id)
        assertEquals("深夜电台", result.name)
        assertEquals(2, result.programs.size)
        assertEquals("节目一", result.programs.first().name)
    }

    @Test
    fun `fetchMyMusicHome returns liked songs recent tracks and playlists`() = runBlocking {
        val repository = SPlayerRemoteRepository(
            api = FakeApiService(
                responses = mapOf(
                    "netease/login/status" to """
                    {
                      "data": {
                        "account": { "id": 9001 },
                        "profile": { "nickname": "原生用户", "avatarUrl": "https://example.com/u.jpg" }
                        }
                    }
                    """.trimIndent(),
                    "netease/user/detail" to """
                    {
                      "level": 9,
                      "listenSongs": 5759,
                      "profile": {
                        "userId": 9001,
                        "nickname": "原生用户",
                        "avatarUrl": "https://example.com/u.jpg",
                        "signature": "把喜欢的歌都收进这里",
                        "backgroundUrl": "https://example.com/bg.jpg",
                        "follows": 9,
                        "followeds": 9
                      }
                    }
                    """.trimIndent(),
                    "netease/likelist" to """
                    {
                      "ids": [11, 12, 13]
                    }
                    """.trimIndent(),
                    "netease/user/playlist" to """
                    {
                      "playlist": [
                        {
                          "id": 1001,
                          "name": "我喜欢的音乐",
                          "coverImgUrl": "https://example.com/l1.jpg",
                          "trackCount": 30,
                          "creator": { "userId": 9001 }
                        },
                        {
                          "id": 1002,
                          "name": "收藏歌单",
                          "coverImgUrl": "https://example.com/l2.jpg",
                          "trackCount": 20,
                          "creator": { "userId": 8001 }
                        }
                      ]
                    }
                    """.trimIndent(),
                    "netease/album/sublist" to """
                    {
                      "data": [
                        {
                          "id": 2001,
                          "name": "收藏专辑",
                          "picUrl": "https://example.com/al1.jpg",
                          "size": 12,
                          "artist": { "name": "歌手甲" }
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
            ),
        )

        val result = repository.fetchMyMusicHome(
            recentTracks = listOf(
                TrackItem(
                    id = 2001,
                    name = "最近播放",
                    artists = "歌手A",
                    album = "专辑A",
                    coverUrl = "https://example.com/recent.jpg",
                    durationMs = 180000,
                ),
            ),
        )

        assertEquals("原生用户", result.currentUser?.nickname)
        assertEquals(3, result.likedSongCount)
        assertEquals("我喜欢的音乐", result.likedPlaylist?.name)
        assertEquals(1, result.recentTracks.size)
        assertEquals(1, result.createdPlaylists.size)
        assertEquals(1, result.collectedPlaylists.size)
        assertEquals(1, result.albums.size)
        assertEquals("收藏专辑", result.albums.first().name)
    }

    @Test
    fun `fetchLoginState falls back to user account when login status account missing`() = runBlocking {
        val repository = SPlayerRemoteRepository(
            api = FakeApiService(
                responses = mapOf(
                    "netease/login/status" to """
                    {
                      "data": {
                        "code": 200,
                        "account": null,
                        "profile": null
                      }
                    }
                    """.trimIndent(),
                    "netease/user/account" to """
                    {
                      "code": 200,
                      "profile": {
                        "userId": 42,
                        "nickname": "测试账号",
                        "avatarUrl": "https://example.com/avatar.jpg"
                      }
                    }
                    """.trimIndent(),
                ),
            ),
        )

        val user = repository.fetchLoginState()

        assertEquals(42L, user?.userId)
        assertEquals("测试账号", user?.nickname)
        assertEquals("https://example.com/avatar.jpg", user?.avatarUrl)
    }

    @Test
    fun `checkQrState returns cookie string from response body`() = runBlocking {
        val repository = SPlayerRemoteRepository(
            api = FakeApiService(
                responses = mapOf(
                    "netease/login/qr/check" to """
                    {
                      "code": 803,
                      "message": "授权登录成功",
                      "cookie": "MUSIC_U=test_music_u; __csrf=test_csrf; NMTID=test_nmtid;"
                    }
                    """.trimIndent(),
                ),
            ),
        )

        val result = repository.checkQrState("test-key")

        assertEquals(803, result.code)
        assertEquals("MUSIC_U=test_music_u; __csrf=test_csrf; NMTID=test_nmtid;", result.cookieHeader)
    }

    @Test
    fun `fetchPlaylistDetail returns playlist meta and track list`() = runBlocking {
        val api = FakeApiService(
            responses = mapOf(
                "netease/playlist/detail" to """
                    {
                      "code": 200,
                      "playlist": {
                        "id": 3778678,
                        "name": "热歌榜",
                        "coverImgUrl": "https://example.com/cover.jpg",
                        "description": "测试歌单",
                        "trackCount": 2,
                        "playCount": 123456,
                        "subscribedCount": 54321
                      }
                    }
                    """.trimIndent(),
                "netease/playlist/track/all" to """
                    {
                      "code": 200,
                      "songs": [
                        {
                          "id": 1,
                          "name": "歌曲一",
                          "dt": 180000,
                          "ar": [{ "name": "歌手甲" }],
                          "al": {
                            "name": "专辑甲",
                            "picUrl": "https://example.com/a.jpg"
                          }
                        },
                        {
                          "id": 2,
                          "name": "歌曲二",
                          "dt": 200000,
                          "ar": [{ "name": "歌手乙" }],
                          "al": {
                            "name": "专辑乙",
                            "picUrl": "https://example.com/b.jpg"
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
            ),
        )
        val repository = SPlayerRemoteRepository(
            api = api,
        )

        val playlist = repository.fetchPlaylistDetail(3778678L)
        val detailCall = api.calls.first { it.url == "netease/playlist/detail" }
        val tracksCall = api.calls.first { it.url == "netease/playlist/track/all" }

        assertEquals(3778678L, playlist.id)
        assertEquals("热歌榜", playlist.name)
        assertEquals("测试歌单", playlist.description)
        assertEquals(2, playlist.trackCount)
        assertEquals(2, playlist.tracks.size)
        assertEquals("歌曲一", playlist.tracks.first().name)
        assertTrue(playlist.playCount > 0)
        assertEquals("0", detailCall.params["s"])
        assertEquals("true", detailCall.params["noCookie"])
        assertEquals("0", tracksCall.params["offset"])
        assertEquals("200", tracksCall.params["limit"])
    }

    @Test
    fun `fetchPlaylistTracksPage requests paged tracks instead of whole playlist`() = runBlocking {
        val api = FakeApiService(
            responses = mapOf(
                "netease/playlist/track/all" to """
                    {
                      "code": 200,
                      "songs": [
                        {
                          "id": 3,
                          "name": "第三首",
                          "dt": 210000,
                          "ar": [{ "name": "歌手丙" }],
                          "al": {
                            "name": "专辑丙",
                            "picUrl": "https://example.com/c.jpg"
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
            ),
        )
        val repository = SPlayerRemoteRepository(api = api)

        val tracks = repository.fetchPlaylistTracksPage(
            playlistId = 3778678L,
            offset = 200,
        )
        val call = api.calls.single()

        assertEquals(1, tracks.size)
        assertEquals("第三首", tracks.first().name)
        assertEquals("3778678", call.params["id"])
        assertEquals("200", call.params["offset"])
        assertEquals("200", call.params["limit"])
    }

    @Test
    fun `fetchPlaylistDetail tolerates duplicated json envelopes from remote api`() = runBlocking {
        val api = FakeApiService(
            responses = mapOf(
                "netease/playlist/detail" to """
                    {
                      "code": 200,
                      "playlist": {
                        "id": 3778678,
                        "name": "热歌榜",
                        "coverImgUrl": "https://example.com/cover.jpg",
                        "description": "测试歌单",
                        "trackCount": 1,
                        "playCount": 123456,
                        "subscribedCount": 54321
                      }
                    }{
                      "code": 200,
                      "playlist": {
                        "id": 3778678,
                        "name": "热歌榜",
                        "coverImgUrl": "https://example.com/cover.jpg",
                        "description": "测试歌单",
                        "trackCount": 1,
                        "playCount": 123456,
                        "subscribedCount": 54321
                      }
                    }
                    """.trimIndent(),
                "netease/playlist/track/all" to """
                    {
                      "code": 200,
                      "songs": [
                        {
                          "id": 1,
                          "name": "歌曲一",
                          "dt": 180000,
                          "ar": [{ "name": "歌手甲" }],
                          "al": {
                            "name": "专辑甲",
                            "picUrl": "https://example.com/a.jpg"
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
            ),
        )
        val repository = SPlayerRemoteRepository(api = api)

        val playlist = repository.fetchPlaylistDetail(3778678L)

        assertEquals(3778678L, playlist.id)
        assertEquals("热歌榜", playlist.name)
        assertEquals(1, playlist.tracks.size)
    }

    @Test
    fun `fetchPlaylistPreviewDetail returns inline preview tracks without waiting for paged track api`() = runBlocking {
        val api = FakeApiService(
            responses = mapOf(
                "netease/playlist/detail" to """
                    {
                      "code": 200,
                      "playlist": {
                        "id": 998877,
                        "name": "首屏歌单",
                        "coverImgUrl": "https://example.com/preview.jpg",
                        "description": "首屏预览",
                        "trackCount": 120,
                        "playCount": 777,
                        "subscribedCount": 66,
                        "tracks": [
                          {
                            "id": 11,
                            "name": "预览歌曲一",
                            "dt": 180000,
                            "ar": [{ "name": "预览歌手甲" }],
                            "al": {
                              "name": "预览专辑甲",
                              "picUrl": "https://example.com/cover-a.jpg"
                            }
                          },
                          {
                            "id": 12,
                            "name": "预览歌曲二",
                            "dt": 200000,
                            "ar": [{ "name": "预览歌手乙" }],
                            "al": {
                              "name": "预览专辑乙",
                              "picUrl": "https://example.com/cover-b.jpg"
                            }
                          }
                        ]
                      }
                    }
                """.trimIndent(),
            ),
        )
        val repository = SPlayerRemoteRepository(api = api)

        val playlist = repository.fetchPlaylistPreviewDetail(998877L)

        assertEquals(998877L, playlist.id)
        assertEquals("首屏歌单", playlist.name)
        assertEquals(2, playlist.tracks.size)
        assertEquals("预览歌曲一", playlist.tracks.first().name)
        assertEquals(1, api.calls.size)
        assertEquals("netease/playlist/detail", api.calls.single().url)
    }

    @Test
    fun `fetchPlaylistPreviewDetail coalesces concurrent remote requests`() = runBlocking {
        val api = FakeApiService(
            responses = mapOf(
                "netease/playlist/detail" to """
                    {
                      "code": 200,
                      "playlist": {
                        "id": 998878,
                        "name": "并发预览歌单",
                        "coverImgUrl": "https://example.com/preview.jpg",
                        "description": "并发预览",
                        "trackCount": 2,
                        "playCount": 888,
                        "subscribedCount": 88,
                        "tracks": [
                          {
                            "id": 21,
                            "name": "并发歌曲一",
                            "dt": 180000,
                            "ar": [{ "name": "并发歌手甲" }],
                            "al": {
                              "name": "并发专辑甲",
                              "picUrl": "https://example.com/cover-aa.jpg"
                            }
                          }
                        ]
                      }
                    }
                """.trimIndent(),
            ),
            responseDelayMillis = mapOf("netease/playlist/detail" to 120L),
        )
        val repository = SPlayerRemoteRepository(api = api)

        val results = coroutineScope {
            awaitAll(
                async { repository.fetchPlaylistPreviewDetail(998878L) },
                async { repository.fetchPlaylistPreviewDetail(998878L) },
            )
        }

        assertEquals(2, results.size)
        assertEquals(results.first(), results.last())
        assertEquals(1, api.calls.size)
    }

    @Test
    fun `extractFirstJsonEnvelope trims duplicated json payload before parsing`() {
        val rawBody = """{"msg":"参数错误","code":400}{"msg":"参数错误","code":400}"""

        val envelope = extractFirstJsonEnvelope(rawBody)

        assertEquals("""{"msg":"参数错误","code":400}""", envelope)
    }

    @Test
    fun `fetchLyrics falls back to ttml when official lyric is empty`() = runBlocking {
        val repository = SPlayerRemoteRepository(
            api = FakeApiService(
                responses = mapOf(
                    "netease/lyric/new" to """
                        {
                          "code": 200,
                          "lrc": { "lyric": "" },
                          "yrc": { "lyric": "" },
                          "tlyric": { "lyric": "" },
                          "romalrc": { "lyric": "" }
                        }
                    """.trimIndent(),
                    "netease/lyric/ttml" to """
                        <?xml version="1.0" encoding="utf-8"?>
                        <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
                          <body>
                            <div>
                              <p begin="00:00.000" end="00:03.000">
                                <span begin="00:00.000" end="00:03.000">主歌词</span>
                                <span ttm:role="x-bg">翻译歌词</span>
                                <span ttm:role="x-roman">roma lyric</span>
                              </p>
                            </div>
                          </body>
                        </tt>
                    """.trimIndent(),
                ),
            ),
        )

        val lyrics = repository.fetchLyrics(
            TrackItem(
                id = 347230L,
                name = "测试歌曲",
                artists = "测试歌手",
                album = "测试专辑",
                coverUrl = "",
                durationMs = 180000L,
            ),
        )

        assertEquals(1, lyrics.size)
        assertEquals("主歌词", lyrics.first().mainText)
        assertEquals("翻译歌词", lyrics.first().translation)
        assertEquals("roma lyric", lyrics.first().romanized)
    }

    @Test
    fun `fetchLyrics parses netease yrc markers into readable lines`() = runBlocking {
        val repository = SPlayerRemoteRepository(
            api = FakeApiService(
                responses = mapOf(
                    "netease/lyric/new" to """
                        {
                          "code": 200,
                          "yrc": {
                            "lyric": "{\"t\":0,\"c\":[{\"tx\":\"作词: 测试\"}]}\n[28590,9920](28590,30,0)30(28620,830,0)年(29450,1240,0)に(30690,430,0)一(31120,190,0)度(31310,270,0)の(31580,780,0)星(32360,350,0)座(32710,1230,0)が(33940,800,0)近(34740,260,0)づ(35000,500,0)い(35500,210,0)て(35710,2800,0)る"
                          },
                          "ytlrc": {
                            "lyric": "[00:28.590]30年一遇的星座正在靠近"
                          },
                          "yromalrc": {
                            "lyric": "[00:28.590]san juu nen ni ichido no seiza ga chikadzu iteru"
                          },
                          "lrc": { "lyric": "" },
                          "tlyric": { "lyric": "" },
                          "romalrc": { "lyric": "" }
                        }
                    """.trimIndent(),
                ),
            ),
        )

        val lyrics = repository.fetchLyrics(
            TrackItem(
                id = 424262994L,
                name = "風は予告なく吹く",
                artists = "ワルキューレ",
                album = "絶対零度θノヴァティック/破滅の純情",
                coverUrl = "",
                durationMs = 364093L,
            ),
        )

        assertFalse(lyrics.isEmpty())
        assertEquals(28590L, lyrics.first().startTimeMs)
        assertEquals("30年に一度の星座が近づいてる", lyrics.first().mainText)
        assertEquals("30年一遇的星座正在靠近", lyrics.first().translation)
        assertEquals("san juu nen ni ichido no seiza ga chikadzu iteru", lyrics.first().romanized)
    }

    @Test
    fun `fetchLyrics falls back to qq match when official and ttml are empty`() = runBlocking {
        val repository = SPlayerRemoteRepository(
            api = FakeApiService(
                responses = mapOf(
                    "netease/lyric/new" to """
                        {
                          "code": 200,
                          "lrc": { "lyric": "" },
                          "yrc": { "lyric": "" },
                          "tlyric": { "lyric": "" },
                          "romalrc": { "lyric": "" }
                        }
                    """.trimIndent(),
                    "netease/lyric/ttml" to "<tt></tt>",
                    "qqmusic/match" to """
                        {
                          "code": 200,
                          "song": { "duration": 180000 },
                          "qrc": "<QrcInfos><Lyric_1 LyricType=\"1\" LyricContent=\"[0,3000]主(0,1000)歌(1000,1000)词(2000,1000)\" /></QrcInfos>",
                          "trans": "[00:00.000]翻译"
                        }
                    """.trimIndent(),
                ),
            ),
        )

        val lyrics = repository.fetchLyrics(
            TrackItem(
                id = 347231L,
                name = "测试歌曲",
                artists = "测试歌手",
                album = "测试专辑",
                coverUrl = "",
                durationMs = 180000L,
            ),
        )

        assertFalse(lyrics.isEmpty())
        assertEquals("主歌词", lyrics.first().mainText)
        assertEquals("翻译", lyrics.first().translation)
    }

    @Test
    fun `fetchPlaylistTracksPage reuses cached page for identical playlist request`() = runBlocking {
        val api = FakeApiService(
            responses = mapOf(
                "netease/playlist/track/all" to """
                    {
                      "code": 200,
                      "songs": [
                        {
                          "id": 3,
                          "name": "第三首",
                          "dt": 210000,
                          "ar": [{ "name": "歌手丙" }],
                          "al": {
                            "name": "专辑丙",
                            "picUrl": "https://example.com/c.jpg"
                          }
                        }
                      ]
                    }
                """.trimIndent(),
            ),
        )
        val repository = SPlayerRemoteRepository(api = api)

        val first = repository.fetchPlaylistTracksPage(
            playlistId = 3778678L,
            offset = 200,
        )
        val second = repository.fetchPlaylistTracksPage(
            playlistId = 3778678L,
            offset = 200,
        )

        assertEquals(1, first.size)
        assertEquals(first, second)
        assertEquals(1, api.calls.size)
    }

    @Test
    fun `fetchPlaylistTracksPage coalesces concurrent paged requests`() = runBlocking {
        val api = FakeApiService(
            responses = mapOf(
                "netease/playlist/track/all" to """
                    {
                      "code": 200,
                      "songs": [
                        {
                          "id": 8,
                          "name": "并发分页歌曲",
                          "dt": 210000,
                          "ar": [{ "name": "并发歌手戊" }],
                          "al": {
                            "name": "并发专辑戊",
                            "picUrl": "https://example.com/e.jpg"
                          }
                        }
                      ]
                    }
                """.trimIndent(),
            ),
            responseDelayMillis = mapOf("netease/playlist/track/all" to 120L),
        )
        val repository = SPlayerRemoteRepository(api = api)

        val pages = coroutineScope {
            awaitAll(
                async { repository.fetchPlaylistTracksPage(playlistId = 3778678L, offset = 200) },
                async { repository.fetchPlaylistTracksPage(playlistId = 3778678L, offset = 200) },
            )
        }

        assertEquals(2, pages.size)
        assertEquals(pages.first(), pages.last())
        assertEquals(1, api.calls.size)
    }

    @Test
    fun `fetchLatestComments reuses cached page for identical song comment request`() = runBlocking {
        val api = FakeApiService(
            responses = mapOf(
                "netease/comment/new" to """
                    {
                      "code": 200,
                      "data": {
                        "totalCount": 100,
                        "hasMore": true,
                        "comments": [
                          {
                            "commentId": 1,
                            "content": "第一条评论",
                            "likedCount": 7,
                            "time": 1713000000000,
                            "user": {
                              "nickname": "评论用户",
                              "avatarUrl": "https://example.com/user.jpg"
                            }
                          }
                        ]
                      }
                    }
                """.trimIndent(),
            ),
        )
        val repository = SPlayerRemoteRepository(api = api)

        val first = repository.fetchLatestComments(songId = 5566L, pageNo = 1)
        val second = repository.fetchLatestComments(songId = 5566L, pageNo = 1)

        assertEquals(1, first.comments.size)
        assertEquals(first, second)
        assertEquals(1, api.calls.size)
    }

    @Test
    fun `fetchLatestComments coalesces concurrent song comment requests`() = runBlocking {
        val api = FakeApiService(
            responses = mapOf(
                "netease/comment/new" to """
                    {
                      "code": 200,
                      "data": {
                        "totalCount": 100,
                        "hasMore": true,
                        "comments": [
                          {
                            "commentId": 3,
                            "content": "并发最新评论",
                            "likedCount": 12,
                            "time": 1713000000200,
                            "user": {
                              "nickname": "最新评论用户",
                              "avatarUrl": "https://example.com/latest-user.jpg"
                            }
                          }
                        ]
                      }
                    }
                """.trimIndent(),
            ),
            responseDelayMillis = mapOf("netease/comment/new" to 120L),
        )
        val repository = SPlayerRemoteRepository(api = api)

        val results = coroutineScope {
            awaitAll(
                async { repository.fetchLatestComments(songId = 5567L, pageNo = 1) },
                async { repository.fetchLatestComments(songId = 5567L, pageNo = 1) },
            )
        }

        assertEquals(2, results.size)
        assertEquals(results.first(), results.last())
        assertEquals(1, api.calls.size)
    }

    @Test
    fun `fetchHotComments reuses cached result for identical song request`() = runBlocking {
        val api = FakeApiService(
            responses = mapOf(
                "netease/comment/hot" to """
                    {
                      "code": 200,
                      "total": 12,
                      "hasMore": false,
                      "hotComments": [
                        {
                          "commentId": 2,
                          "content": "热门评论",
                          "likedCount": 88,
                          "time": 1713000000100,
                          "user": {
                            "nickname": "热评用户",
                            "avatarUrl": "https://example.com/hot-user.jpg"
                          }
                        }
                      ]
                    }
                """.trimIndent(),
            ),
        )
        val repository = SPlayerRemoteRepository(api = api)

        val first = repository.fetchHotComments(songId = 7788L)
        val second = repository.fetchHotComments(songId = 7788L)

        assertEquals(1, first.comments.size)
        assertEquals(first, second)
        assertEquals(1, api.calls.size)
    }

    @Test
    fun `fetchHotComments coalesces concurrent hot comment requests`() = runBlocking {
        val api = FakeApiService(
            responses = mapOf(
                "netease/comment/hot" to """
                    {
                      "code": 200,
                      "total": 12,
                      "hasMore": false,
                      "hotComments": [
                        {
                          "commentId": 4,
                          "content": "并发热评",
                          "likedCount": 66,
                          "time": 1713000000300,
                          "user": {
                            "nickname": "并发热评用户",
                            "avatarUrl": "https://example.com/hot-user-2.jpg"
                          }
                        }
                      ]
                    }
                """.trimIndent(),
            ),
            responseDelayMillis = mapOf("netease/comment/hot" to 120L),
        )
        val repository = SPlayerRemoteRepository(api = api)

        val results = coroutineScope {
            awaitAll(
                async { repository.fetchHotComments(songId = 7789L) },
                async { repository.fetchHotComments(songId = 7789L) },
            )
        }

        assertEquals(2, results.size)
        assertEquals(results.first(), results.last())
        assertEquals(1, api.calls.size)
    }

    @Test
    fun `fetchLyrics reuses cached result for identical track request`() = runBlocking {
        val api = FakeApiService(
            responses = mapOf(
                "netease/lyric/new" to """
                    {
                      "code": 200,
                      "lrc": {
                        "lyric": "[00:00.000]第一句\n[00:05.000]第二句"
                      },
                      "tlyric": { "lyric": "" },
                      "romalrc": { "lyric": "" },
                      "yrc": { "lyric": "" }
                    }
                """.trimIndent(),
            ),
        )
        val repository = SPlayerRemoteRepository(api = api)
        val track = TrackItem(
            id = 9090L,
            name = "缓存测试歌曲",
            artists = "缓存测试歌手",
            album = "缓存测试专辑",
            coverUrl = "",
            durationMs = 180000,
        )

        val first = repository.fetchLyrics(track)
        val second = repository.fetchLyrics(track)

        assertEquals(2, first.size)
        assertEquals(first, second)
        assertEquals(1, api.calls.size)
    }

    @Test
    fun `fetchLyrics coalesces concurrent lyric requests`() = runBlocking {
        val api = FakeApiService(
            responses = mapOf(
                "netease/lyric/new" to """
                    {
                      "code": 200,
                      "lrc": {
                        "lyric": "[00:00.000]并发第一句\n[00:05.000]并发第二句"
                      },
                      "tlyric": { "lyric": "" },
                      "romalrc": { "lyric": "" },
                      "yrc": { "lyric": "" }
                    }
                """.trimIndent(),
            ),
            responseDelayMillis = mapOf("netease/lyric/new" to 120L),
        )
        val repository = SPlayerRemoteRepository(api = api)
        val track = TrackItem(
            id = 9091L,
            name = "并发歌词歌曲",
            artists = "并发歌词歌手",
            album = "并发歌词专辑",
            coverUrl = "",
            durationMs = 180000,
        )

        val results = coroutineScope {
            awaitAll(
                async { repository.fetchLyrics(track) },
                async { repository.fetchLyrics(track) },
            )
        }

        assertEquals(2, results.size)
        assertEquals(results.first(), results.last())
        assertEquals(1, api.calls.size)
    }

    @Test
    fun `fetchDiscoveryHome reuses session cache on repeated request`() = runBlocking {
        val api = FakeApiService(
            responses = mapOf(
                "netease/personalized" to """
                    {
                      "result": [
                        { "id": 1, "name": "推荐歌单", "picUrl": "https://example.com/p1.jpg", "trackCount": 20 }
                      ]
                    }
                """.trimIndent(),
                "netease/top/song" to """
                    {
                      "data": [
                        {
                          "id": 101,
                          "name": "新歌一",
                          "dt": 180000,
                          "artists": [{ "name": "歌手甲" }],
                          "album": { "name": "专辑甲", "picUrl": "https://example.com/a1.jpg" }
                        }
                      ]
                    }
                """.trimIndent(),
                "netease/top/artists" to """
                    {
                      "artists": [
                        { "id": 201, "name": "热门歌手", "img1v1Url": "https://example.com/ar1.jpg", "musicSize": 10 }
                      ]
                    }
                """.trimIndent(),
                "netease/album/new" to """
                    {
                      "albums": [
                        { "id": 301, "name": "新专辑", "picUrl": "https://example.com/al1.jpg", "size": 8 }
                      ]
                    }
                """.trimIndent(),
                "netease/toplist/detail" to """
                    {
                      "list": [
                        { "id": 401, "name": "飙升榜", "coverImgUrl": "https://example.com/t1.jpg", "trackCount": 100 }
                      ]
                    }
                """.trimIndent(),
            ),
        )
        val repository = SPlayerRemoteRepository(api = api)

        val first = repository.fetchDiscoveryHome()
        val second = repository.fetchDiscoveryHome()

        assertEquals(first, second)
        assertEquals(5, api.calls.size)
    }

    @Test
    fun `fetchAlbumDetail returns album meta dynamic counters and tracks`() = runBlocking {
        val repository = SPlayerRemoteRepository(
            api = FakeApiService(
                responses = mapOf(
                    "netease/album" to """
                    {
                      "code": 200,
                      "album": {
                        "id": 32311,
                        "name": "神的游戏",
                        "picUrl": "https://example.com/album.jpg",
                        "description": "测试专辑",
                        "size": 2
                      },
                      "songs": [
                        {
                          "id": 101,
                          "name": "专辑曲目一",
                          "dt": 180000,
                          "ar": [{ "name": "张悬" }],
                          "al": {
                            "id": 32311,
                            "name": "神的游戏",
                            "picUrl": "https://example.com/album.jpg"
                          }
                        },
                        {
                          "id": 102,
                          "name": "专辑曲目二",
                          "dt": 200000,
                          "ar": [{ "name": "张悬" }],
                          "al": {
                            "id": 32311,
                            "name": "神的游戏",
                            "picUrl": "https://example.com/album.jpg"
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
                    "netease/album/detail/dynamic" to """
                    {
                      "code": 200,
                      "commentCount": 1990,
                      "shareCount": 8757,
                      "subCount": 68386
                    }
                    """.trimIndent(),
                ),
            ),
        )

        val album = repository.fetchAlbumDetail(32311L)

        assertEquals(32311L, album.id)
        assertEquals("神的游戏", album.name)
        assertEquals("测试专辑", album.description)
        assertEquals(68386L, album.subscribedCount)
        assertEquals(8757L, album.shareCount)
        assertEquals(1990, album.commentCount)
        assertEquals(2, album.trackCount)
        assertEquals(2, album.tracks.size)
        assertEquals("专辑曲目一", album.tracks.first().name)
    }
}

private class FakeApiService(
    private val responses: Map<String, String>,
    private val responseDelayMillis: Map<String, Long> = emptyMap(),
) : SPlayerApiService {
    val calls = Collections.synchronizedList(mutableListOf<ApiCall>())

    override suspend fun get(url: String, params: Map<String, String>): okhttp3.ResponseBody {
        responseDelayMillis[url]?.takeIf { it > 0L }?.let { delay(it) }
        calls += ApiCall(url = url, params = params)
        return (responses[url] ?: error("missing response for $url")).toResponseBody()
    }

    override suspend fun post(url: String, data: Map<String, String>, params: Map<String, String>) =
        error("unused")
}

private data class ApiCall(
    val url: String,
    val params: Map<String, String>,
)
