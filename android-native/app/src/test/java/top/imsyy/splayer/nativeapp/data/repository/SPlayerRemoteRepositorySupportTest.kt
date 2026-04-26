package top.imsyy.splayer.nativeapp.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import top.imsyy.splayer.nativeapp.model.LyricLineUi

class SPlayerRemoteRepositorySupportTest {
    @Test
    fun `BoundedMemoryCache evicts least recently used entries`() {
        val cache = BoundedMemoryCache<Int, String>(maxEntries = 2)

        cache[1] = "one"
        cache[2] = "two"
        assertEquals("one", cache[1])
        cache[3] = "three"

        assertEquals(2, cache.size())
        assertEquals("one", cache[1])
        assertNull(cache[2])
        assertEquals("three", cache[3])
    }

    @Test
    fun `extractFirstJsonEnvelope trims duplicated object payloads before json parsing`() {
        val rawBody = """{"msg":"参数错误","code":400}{"msg":"参数错误","code":400}"""

        val result = extractFirstJsonEnvelope(rawBody)

        assertEquals("""{"msg":"参数错误","code":400}""", result)
    }

    @Test
    fun `stripLeadingAndTrailingLyricMetadata removes provider and credit lines while keeping lyric body`() {
        val result = stripLeadingAndTrailingLyricMetadata(
            listOf(
                LyricLineUi(startTimeMs = 0L, mainText = "Lyrics provider: 天雾绫斗"),
                LyricLineUi(startTimeMs = 1_000L, mainText = "Translation provider: SoryuY"),
                LyricLineUi(startTimeMs = 2_000L, mainText = "もう一度手にするまで", translation = "再度来临之前"),
                LyricLineUi(startTimeMs = 5_000L, mainText = "消さないで灯火", translation = "请别熄灭灯光"),
                LyricLineUi(startTimeMs = 9_000L, mainText = "Fromme 's Cloud Drive"),
            ),
        )

        assertEquals(2, result.size)
        assertEquals("もう一度手にするまで", result.first().mainText)
        assertEquals("消さないで灯火", result.last().mainText)
    }

    @Test
    fun `stripLeadingAndTrailingLyricMetadata also removes cloud drive lines leaked into lyric body`() {
        val result = stripLeadingAndTrailingLyricMetadata(
            listOf(
                LyricLineUi(startTimeMs = 0L, mainText = "もう一度手にするまで", translation = "再度来临之前"),
                LyricLineUi(startTimeMs = 2_000L, mainText = "Fromme 's Cloud Drive"),
                LyricLineUi(startTimeMs = 5_000L, mainText = "消さないで灯火", translation = "请别熄灭灯光"),
            ),
        )

        assertEquals(2, result.size)
        assertEquals("もう一度手にするまで", result.first().mainText)
        assertEquals("消さないで灯火", result.last().mainText)
    }

    @Test
    fun `looksLikeLyricMetadataLine only flags known metadata patterns`() {
        assertTrue(looksLikeLyricMetadataLine("作词：测试"))
        assertTrue(looksLikeLyricMetadataLine("Translation provider: SoryuY"))
        assertTrue(looksLikeLyricMetadataLine("Fromme 's Cloud Drive"))
        assertFalse(looksLikeLyricMetadataLine("空も飛べるはず"))
    }

    @Test
    fun `parseNeteaseYrcLines keeps absolute netease word timings for native word highlight`() {
        val result = parseNeteaseYrcLines(
            primaryLyric = "[28590,1800](28590,280,0)風(28870,240,0)さ(29110,320,0)そう",
            translationLyric = "[00:28.590]请随风摇曳",
        )

        assertEquals(1, result.size)
        assertTrue(result.first().hasWordTiming)
        assertEquals("風さそう", result.first().mainText)
        assertEquals("请随风摇曳", result.first().translation)
        assertEquals(3, result.first().words.size)
        assertEquals(30_390L, result.first().endTimeMs)
        assertEquals("風", result.first().words[0].text)
        assertEquals(28_590L, result.first().words[0].startTimeMs)
        assertEquals(28_870L, result.first().words[0].endTimeMs)
        assertEquals(29_110L, result.first().words[2].startTimeMs)
        assertEquals(29_430L, result.first().words[2].endTimeMs)
    }

    @Test
    fun `parseNeteaseYrcLines ignores lyric new metadata json rows and keeps timed words for 假如爱有天意`() {
        val result = parseNeteaseYrcLines(
            primaryLyric = """
                {"t":0,"c":[{"tx":"作词: "},{"tx":"李健"}]}
                {"t":1000,"c":[{"tx":"作曲: "},{"tx":"Yoo Young Seok"}]}
                [30550,5560](30550,1020,0)当(31570,620,0)天(32190,750,0)边(32940,360,0)那(33300,440,0)颗(33740,1140,0)星(34880,550,0)出(35430,680,0)现
            """.trimIndent(),
            translationLyric = "[00:30.550]当天边那颗星出现",
        )

        assertEquals(1, result.size)
        assertTrue(result.first().hasWordTiming)
        assertEquals("当天边那颗星出现", result.first().mainText)
        assertEquals("当天边那颗星出现", result.first().translation)
        assertEquals(8, result.first().words.size)
        assertEquals(36_110L, result.first().endTimeMs)
        assertEquals(30_550L, result.first().words.first().startTimeMs)
        assertEquals(31_570L, result.first().words[1].startTimeMs)
        assertEquals(35_430L, result.first().words.last().startTimeMs)
    }

    @Test
    fun `parsePlainLrcLines builds line level word timing like desktop fallback parser`() {
        val result = parsePlainLrcLines(
            """
            [00:00.000]第一句
            [00:05.000]第二句
            """.trimIndent(),
        )

        assertEquals(2, result.size)
        assertFalse(result.first().hasWordTiming)
        assertEquals(1, result.first().words.size)
        assertEquals(5_000L, result.first().endTimeMs)
        assertEquals("第一句", result.first().words.first().text)
        assertEquals(0L, result.first().words.first().startTimeMs)
        assertEquals(5_000L, result.first().words.first().endTimeMs)
        assertEquals(1, result.last().words.size)
        assertEquals(15_000L, result.last().endTimeMs)
        assertEquals(5_000L, result.last().words.first().startTimeMs)
        assertEquals(15_000L, result.last().words.first().endTimeMs)
    }

    @Test
    fun `parseTimedLyricLines keeps qrc word offsets for fallback providers`() {
        val result = parseTimedLyricLines(
            primaryLyric = "[2500,1400]空(0,220)も(220,180)飛べる(400,500)はず(900,320)",
            translationLyric = "[00:02.500]一定能展翅高飞",
        )

        assertEquals(1, result.size)
        assertTrue(result.first().hasWordTiming)
        assertEquals("空も飛べるはず", result.first().mainText)
        assertEquals(4, result.first().words.size)
        assertEquals(3_900L, result.first().endTimeMs)
        assertEquals(2500L, result.first().words[0].startTimeMs)
        assertEquals(2720L, result.first().words[0].endTimeMs)
        assertEquals(3400L, result.first().words[3].startTimeMs)
        assertEquals(3720L, result.first().words[3].endTimeMs)
    }

    @Test
    fun `parseTtmlLyricBody keeps timed spans for word level rendering`() {
        val result = parseTtmlLyricBody(
            """
            <tt xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body>
                <div>
                  <p begin="00:12.000" end="00:15.000">
                    <span begin="00:12.000" end="00:12.300">会</span>
                    <span begin="00:12.300" end="00:12.680">え</span>
                    <span begin="00:12.680" end="00:13.040">た</span>
                    <span ttm:role="x-translation">终于相遇</span>
                  </p>
                </div>
              </body>
            </tt>
            """.trimIndent(),
        )

        assertEquals(1, result.size)
        assertTrue(result.first().hasWordTiming)
        assertEquals("会えた", result.first().mainText)
        assertEquals("终于相遇", result.first().translation)
        assertEquals(3, result.first().words.size)
        assertEquals(15_000L, result.first().endTimeMs)
        assertEquals(12_000L, result.first().words.first().startTimeMs)
        assertEquals(12_300L, result.first().words.first().endTimeMs)
        assertEquals(12_680L, result.first().words.last().startTimeMs)
        assertEquals(13_040L, result.first().words.last().endTimeMs)
    }
}
