package top.imsyy.splayer.nativeapp.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerScreenSupportTest {
    @Test
    fun `resolveCoverStageLayout keeps disc large and shifts visual focus downward on common portrait phones`() {
        val layout = resolveCoverStageLayout(
            maxWidthDp = 360f,
            maxHeightDp = 430f,
        )

        assertTrue(layout.discSizeDp >= 420f)
        assertTrue(layout.visualZoneHeightDp >= layout.discSizeDp)
        assertTrue(layout.visualOffsetYDp >= 28f)
        assertTrue(layout.artworkSizeDp > 275f)
    }

    @Test
    fun `resolveCoverStageLayout keeps tall portrait screens focused on disc instead of stretching whitespace`() {
        val layout = resolveCoverStageLayout(
            maxWidthDp = 412f,
            maxHeightDp = 560f,
        )

        assertTrue(layout.discSizeDp >= 520f)
        assertTrue(layout.visualZoneHeightDp >= layout.discSizeDp + 12f)
        assertTrue(layout.visualOffsetYDp >= 36f)
        assertTrue(layout.artworkSizeDp > 340f)
    }

    @Test
    fun `resolveCoverStageLayout keeps tone arm anchored to disc composition`() {
        val layout = resolveCoverStageLayout(
            maxWidthDp = 360f,
            maxHeightDp = 640f,
        )

        assertTrue(layout.toneArmSlotWidthDp > 220f)
        assertTrue(layout.toneArmSlotHeightDp > 180f)
        assertTrue(layout.toneArmOffsetXDp < 0f)
        assertTrue(layout.toneArmOffsetYDp <= 0f)
    }

    @Test
    fun `resolveLyricViewportLayout keeps lyric layout dense while leaving edge spacer to viewport model`() {
        val layout = resolveLyricViewportLayout(
            viewportHeightDp = 520f,
        )

        assertEquals(0f, layout.topPaddingDp, 0.01f)
        assertEquals(0f, layout.bottomPaddingDp, 0.01f)
        assertEquals(10f, layout.lineSpacingDp, 0.01f)
        assertEquals(20f, layout.edgeMinPaddingDp, 0.01f)
    }

    @Test
    fun `resolveLyricEdgeSpacerPx centers first and last lyric lines against the same viewport anchor`() {
        assertEquals(
            208,
            resolveLyricEdgeSpacerPx(
                viewportHeightPx = 520,
                lineHeightPx = 84,
                minimumPaddingPx = 20,
                betweenItemSpacingPx = 10,
            ),
        )
        assertEquals(
            170,
            resolveLyricEdgeSpacerPx(
                viewportHeightPx = 520,
                lineHeightPx = 160,
                minimumPaddingPx = 20,
                betweenItemSpacingPx = 10,
            ),
        )
    }

    @Test
    fun `estimateLyricLineHeightPx shrinks single line fallback and expands when translation is visible`() {
        val singleLine = estimateLyricLineHeightPx(
            line = sampleLyricLine(mainText = "第一句"),
            showTranslation = true,
            showRomanized = false,
            lyricFontScale = 1f,
            density = androidx.compose.ui.unit.Density(1f),
        )
        val translated = estimateLyricLineHeightPx(
            line = sampleLyricLine(mainText = "第二句", translation = "翻译"),
            showTranslation = true,
            showRomanized = false,
            lyricFontScale = 1f,
            density = androidx.compose.ui.unit.Density(1f),
        )

        assertEquals(50, singleLine)
        assertEquals(84, translated)
    }

    @Test
    fun `calculateLyricCenterDelta returns the distance from lyric center to viewport anchor`() {
        val delta = calculateLyricCenterDelta(
            itemCenterY = 302f,
            viewportStart = 0,
            viewportEnd = 520,
        )

        assertEquals(42f, delta, 0.01f)
    }

    @Test
    fun `resolveLyricScrollAdjustment moves target line upward when it sits below viewport center`() {
        val delta = resolveLyricScrollAdjustmentPx(
            itemCenterY = 302f,
            viewportStart = 0,
            viewportEnd = 520,
        )

        assertEquals(-42f, delta, 0.01f)
    }

    @Test
    fun `resolveLyricScrollAdjustment moves target line downward when it sits above viewport center`() {
        val delta = resolveLyricScrollAdjustmentPx(
            itemCenterY = 188f,
            viewportStart = 0,
            viewportEnd = 520,
        )

        assertEquals(72f, delta, 0.01f)
    }

    @Test
    fun `findCenteredLyricIndex returns nearest line to viewport center`() {
        val result = findCenteredLyricIndex(
            candidates = listOf(
                CenteredLyricCandidate(index = 3, centerY = 220f),
                CenteredLyricCandidate(index = 4, centerY = 480f),
                CenteredLyricCandidate(index = 5, centerY = 760f),
            ),
            viewportCenter = 510f,
        )

        assertEquals(4, result)
    }

    @Test
    fun `findCenteredLyricIndex returns null when there is no visible lyric`() {
        val result = findCenteredLyricIndex(
            candidates = emptyList(),
            viewportCenter = 520f,
        )

        assertNull(result)
    }

    @Test
    fun `resolveLyricWordProgress only highlights after word timing starts`() {
        val word = top.imsyy.splayer.nativeapp.model.LyricWordUi(
            text = "風",
            startTimeMs = 1_000L,
            endTimeMs = 1_300L,
        )

        assertEquals(0f, resolveLyricWordProgress(word, 900L), 0.001f)
        assertEquals(0.5f, resolveLyricWordProgress(word, 1_150L), 0.001f)
        assertEquals(1f, resolveLyricWordProgress(word, 1_500L), 0.001f)
    }

    @Test
    fun `resolveLyricGlyphProgress distributes word highlight across visible glyphs`() {
        assertEquals(1f, resolveLyricGlyphProgress(glyphIndex = 0, glyphCount = 3, wordProgress = 0.4f), 0.001f)
        assertEquals(0.2f, resolveLyricGlyphProgress(glyphIndex = 1, glyphCount = 3, wordProgress = 0.4f), 0.001f)
        assertEquals(0f, resolveLyricGlyphProgress(glyphIndex = 2, glyphCount = 3, wordProgress = 0.4f), 0.001f)
    }

    private fun sampleLyricLine(
        mainText: String,
        translation: String = "",
    ) = top.imsyy.splayer.nativeapp.model.LyricLineUi(
        startTimeMs = 0L,
        mainText = mainText,
        translation = translation,
        romanized = "",
    )
}
