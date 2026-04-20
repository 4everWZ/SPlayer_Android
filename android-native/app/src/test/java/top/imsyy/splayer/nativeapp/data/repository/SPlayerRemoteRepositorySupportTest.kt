package top.imsyy.splayer.nativeapp.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.imsyy.splayer.nativeapp.model.LyricLineUi

class SPlayerRemoteRepositorySupportTest {
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
    fun `looksLikeLyricMetadataLine only flags known metadata patterns`() {
        assertTrue(looksLikeLyricMetadataLine("作词：测试"))
        assertTrue(looksLikeLyricMetadataLine("Translation provider: SoryuY"))
        assertTrue(looksLikeLyricMetadataLine("Fromme 's Cloud Drive"))
        assertFalse(looksLikeLyricMetadataLine("空も飛べるはず"))
    }

    @Test
    fun `parseNeteaseYrcLines keeps word timings for native word highlight`() {
        val result = parseNeteaseYrcLines(
            primaryLyric = "[1000,1800](0,280,0)風(280,240,0)さ(520,320,0)そう",
            translationLyric = "[00:01.000]请随风摇曳",
        )

        assertEquals(1, result.size)
        assertEquals("風さそう", result.first().mainText)
        assertEquals("请随风摇曳", result.first().translation)
        assertEquals(3, result.first().words.size)
        assertEquals("風", result.first().words[0].text)
        assertEquals(1000L, result.first().words[0].startTimeMs)
        assertEquals(1280L, result.first().words[0].endTimeMs)
        assertEquals(1520L, result.first().words[2].startTimeMs)
        assertEquals(1840L, result.first().words[2].endTimeMs)
    }

    @Test
    fun `parseTimedLyricLines keeps qrc word offsets for fallback providers`() {
        val result = parseTimedLyricLines(
            primaryLyric = "[2500,1400]空(0,220)も(220,180)飛べる(400,500)はず(900,320)",
            translationLyric = "[00:02.500]一定能展翅高飞",
        )

        assertEquals(1, result.size)
        assertEquals("空も飛べるはず", result.first().mainText)
        assertEquals(4, result.first().words.size)
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
        assertEquals("会えた", result.first().mainText)
        assertEquals("终于相遇", result.first().translation)
        assertEquals(3, result.first().words.size)
        assertEquals(12_000L, result.first().words.first().startTimeMs)
        assertEquals(12_300L, result.first().words.first().endTimeMs)
        assertEquals(12_680L, result.first().words.last().startTimeMs)
        assertEquals(13_040L, result.first().words.last().endTimeMs)
    }
}
