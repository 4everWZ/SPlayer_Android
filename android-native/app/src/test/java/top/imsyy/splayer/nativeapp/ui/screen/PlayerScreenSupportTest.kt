package top.imsyy.splayer.nativeapp.ui.screen

import top.imsyy.splayer.nativeapp.model.LyricLineUi
import top.imsyy.splayer.nativeapp.model.LyricWordUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        assertTrue(layout.discSizeDp in 248f..270f)
        assertTrue(layout.stageTopOffsetDp in 4f..12f)
        assertTrue(layout.visualZoneHeightDp >= layout.discSizeDp)
        assertTrue(layout.visualZoneHeightDp <= layout.compositionSizeDp + layout.visualTopInsetDp + 12f)
        assertTrue(layout.visualTopInsetDp in 20f..32f)
        assertTrue(layout.artworkSizeDp >= 170f)
        assertTrue(layout.infoBottomPaddingDp in 6f..16f)
        assertTrue(layout.visualZoneHeightDp >= layout.compositionSizeDp + layout.visualTopInsetDp)
        assertTrue(layout.visualZoneHeightDp <= 300f)
    }

    @Test
    fun `resolveCoverStageLayout keeps tall portrait screens focused on disc instead of stretching whitespace`() {
        val layout = resolveCoverStageLayout(
            maxWidthDp = 412f,
            maxHeightDp = 560f,
        )

        assertTrue(layout.discSizeDp in 320f..340f)
        assertTrue(layout.stageTopOffsetDp in 4f..12f)
        assertTrue(layout.visualZoneHeightDp <= layout.compositionSizeDp + layout.visualTopInsetDp + 12f)
        assertTrue(layout.visualTopInsetDp in 28f..42f)
        assertTrue(layout.artworkSizeDp >= 220f)
        assertTrue(layout.visualZoneHeightDp <= 370f)
    }

    @Test
    fun `resolveCoverStageLayout keeps tone arm anchored to disc composition`() {
        val layout = resolveCoverStageLayout(
            maxWidthDp = 360f,
            maxHeightDp = 640f,
        )

        assertTrue(layout.toneArmSlotWidthDp >= 170f)
        assertTrue(layout.toneArmSlotHeightDp >= 150f)
        assertTrue(layout.toneArmOffsetXDp < 0f)
        assertTrue(layout.toneArmOffsetYDp >= 0f)
    }

    @Test
    fun `resolveLyricViewportLayout uses compact netease style lyric rhythm`() {
        val layout = resolveLyricViewportLayout(
            viewportHeightDp = 520f,
        )

        assertEquals(0f, layout.topPaddingDp, 0.01f)
        assertEquals(0f, layout.bottomPaddingDp, 0.01f)
        assertEquals(14f, layout.lineSpacingDp, 0.01f)
        assertEquals(2f, layout.linePaddingDp, 0.01f)
        assertEquals(8f, layout.previewPaddingDp, 0.01f)
        assertEquals(14f, layout.edgeMinPaddingDp, 0.01f)
    }

    @Test
    fun `lyric preview anchor keeps the same main text scale as lyric lines`() {
        assertEquals(1f, resolveLyricMainTextScale(1f), 0.01f)
        assertEquals(resolveLyricMainTextScale(1.2f), resolvePreviewAnchorMainTextScale(1.2f), 0.01f)
        assertTrue(resolveLyricTranslationTextScale(1f) < 1f)
    }

    @Test
    fun `resolvePlayerStageHeightDp keeps bottom chrome stable across cover and lyric pages`() {
        assertEquals(392f, resolvePlayerStageHeightDp(viewportHeightDp = 628f, lyricMode = true), 0.01f)
        assertEquals(392f, resolvePlayerStageHeightDp(viewportHeightDp = 628f, lyricMode = false), 0.01f)
        assertEquals(1044f, resolvePlayerStageHeightDp(viewportHeightDp = 1280f, lyricMode = true), 0.01f)
        assertEquals(1044f, resolvePlayerStageHeightDp(viewportHeightDp = 1280f, lyricMode = false), 0.01f)
        assertEquals(1264f, resolvePlayerStageHeightDp(viewportHeightDp = 1500f, lyricMode = true), 0.01f)
        assertEquals(1264f, resolvePlayerStageHeightDp(viewportHeightDp = 1500f, lyricMode = false), 0.01f)
    }

    @Test
    fun `player controls reserve is mode neutral and overlay slots are fixed`() {
        assertEquals(236f, resolvePlayerControlsReserveDp(lyricMode = false), 0.01f)
        assertEquals(236f, resolvePlayerControlsReserveDp(lyricMode = true), 0.01f)
        assertEquals(32f, resolvePlayerLyricToggleSlotHeightDp(), 0.01f)
        assertEquals(22f, resolvePlayerStatusSlotHeightDp(), 0.01f)
    }

    @Test
    fun `player bottom safe gap adds a small visual reserve above navigation keys`() {
        assertEquals(0f, resolvePlayerBottomSafeGapDp(360f), 0.01f)
        assertEquals(0f, resolvePlayerBottomSafeGapDp(628f), 0.01f)
        assertEquals(0f, resolvePlayerBottomSafeGapDp(1280f), 0.01f)
    }

    @Test
    fun `player keeps stage to progress gap identical so progress bar does not jump`() {
        assertEquals(4f, resolvePlayerStageToProgressGapDp(lyricMode = true), 0.01f)
        assertEquals(4f, resolvePlayerStageToProgressGapDp(lyricMode = false), 0.01f)
    }

    @Test
    fun `lyric list is hidden until initial auto center has landed`() {
        assertFalse(
            shouldRevealLyricListBeforeFirstCenter(
                initialAutoCenterSettled = false,
                mode = LyricScrollMode.AutoFollow,
            ),
        )
        assertTrue(
            shouldRevealLyricListBeforeFirstCenter(
                initialAutoCenterSettled = true,
                mode = LyricScrollMode.AutoFollow,
            ),
        )
        assertTrue(
            shouldRevealLyricListBeforeFirstCenter(
                initialAutoCenterSettled = false,
                mode = LyricScrollMode.ManualPreview,
            ),
        )
    }

    @Test
    fun `resolveLyricContentWidthDp keeps lyric block centered with stable side margins on phone widths`() {
        assertEquals(331.2f, resolveLyricContentWidthDp(360f), 0.01f)
        assertEquals(379.04f, resolveLyricContentWidthDp(412f), 0.01f)
        assertEquals(680f, resolveLyricContentWidthDp(760f), 0.01f)
    }

    @Test
    fun `resolveLyricViewportHeightPx prefers measured lazy list viewport when available`() {
        assertEquals(
            520,
            resolveLyricViewportHeightPx(
                viewportStart = 0,
                viewportEnd = 520,
                fallbackHeightPx = 480,
            ),
        )
        assertEquals(
            480,
            resolveLyricViewportHeightPx(
                viewportStart = 0,
                viewportEnd = 0,
                fallbackHeightPx = 480,
            ),
        )
    }

    @Test
    fun `hasWordLevelLyric only turns on song level cadence when at least one line has distinct timed words`() {
        val plainOnly = listOf(
            LyricLineUi(
                startTimeMs = 0L,
                endTimeMs = 2_000L,
                mainText = "整句高亮",
                words = listOf(
                    LyricWordUi(
                        text = "整句高亮",
                        startTimeMs = 0L,
                        endTimeMs = 2_000L,
                    ),
                ),
                hasWordTiming = false,
            ),
        )
        val wordLevel = plainOnly + LyricLineUi(
            startTimeMs = 2_000L,
            endTimeMs = 4_000L,
            mainText = "假如爱有天意",
            words = listOf(
                LyricWordUi("假如", 2_000L, 2_400L),
                LyricWordUi("爱有", 2_400L, 2_850L),
                LyricWordUi("天意", 2_850L, 3_300L),
            ),
            hasWordTiming = true,
        )

        assertFalse(hasWordLevelLyric(plainOnly))
        assertTrue(hasWordLevelLyric(wordLevel))
    }

    @Test
    fun `hasWordLevelLyric stays false when timed lyric source degrades to sentence highlight`() {
        val timedSourceButSentenceRender = listOf(
            LyricLineUi(
                startTimeMs = 0L,
                endTimeMs = 5_000L,
                mainText = "假如爱有天意",
                words = listOf(
                    LyricWordUi("假如", 0L, 5_000L),
                    LyricWordUi("爱有", 0L, 5_000L),
                ),
                hasWordTiming = true,
            ),
        )

        assertFalse(hasWordLevelLyric(timedSourceButSentenceRender))
        assertEquals(LyricHighlightMode.Sentence, resolveLyricHighlightMode(timedSourceButSentenceRender.first()))
    }

    @Test
    fun `disc rotation runs only during active playback`() {
        assertTrue(shouldRunDiscRotation(isPlaying = true, isBuffering = false))
        assertFalse(shouldRunDiscRotation(isPlaying = false, isBuffering = false))
        assertFalse(shouldRunDiscRotation(isPlaying = true, isBuffering = true))
    }

    @Test
    fun `normalizeDiscRotation keeps animation value inside one circle`() {
        assertEquals(0f, normalizeDiscRotation(0f), 0.01f)
        assertEquals(12f, normalizeDiscRotation(372f), 0.01f)
        assertEquals(350f, normalizeDiscRotation(-10f), 0.01f)
    }

    @Test
    fun `profile background image request keeps cache key stable when url stays same`() {
        val first = resolveProfileBackgroundImageRequestIdentity(
            imageUrl = "https://example.com/user-bg.jpg",
        )
        val second = resolveProfileBackgroundImageRequestIdentity(
            imageUrl = "https://example.com/user-bg.jpg",
        )

        assertEquals("https://example.com/user-bg.jpg", first.imageUrl)
        assertEquals(first.imageUrl, second.imageUrl)
        assertEquals(first.memoryCacheKey, second.memoryCacheKey)
        assertEquals(first.diskCacheKey, second.diskCacheKey)
    }

    @Test
    fun `my hero background follows avatar because profile background can be stale`() {
        val resolved = resolveMyHeroBackgroundImageUrl(
            avatarUrl = "https://example.com/new-avatar.jpg",
            backgroundUrl = "https://example.com/stale-background.jpg",
        )

        assertEquals("https://example.com/new-avatar.jpg", resolved)
    }

    @Test
    fun `my hero background stays blank when avatar is blank`() {
        val resolved = resolveMyHeroBackgroundImageUrl(
            avatarUrl = " ",
            backgroundUrl = "https://example.com/profile-background.jpg",
        )

        assertEquals("", resolved)
    }

    @Test
    fun `disc rotation keeps netease style long play cycle`() {
        assertEquals(18_000, DISC_ROTATION_CYCLE_MS)
    }

    @Test
    fun `resolveLyricEdgeSpacerPx centers first and last lyric lines against the same viewport anchor`() {
        assertEquals(
            218,
            resolveLyricEdgeSpacerPx(
                viewportHeightPx = 520,
                lineHeightPx = 84,
                minimumPaddingPx = 20,
            ),
        )
        assertEquals(
            180,
            resolveLyricEdgeSpacerPx(
                viewportHeightPx = 520,
                lineHeightPx = 160,
                minimumPaddingPx = 20,
            ),
        )
    }

    @Test
    fun `estimateLyricLineHeightPx keeps lyric rows compact while reserving translation space`() {
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

        assertEquals(60, singleLine)
        assertEquals(98, translated)
    }

    @Test
    fun `lyric auto center uses a short animation instead of immediate jumps`() {
        assertEquals(260, LYRIC_AUTO_CENTER_ANIMATION_MS)
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
    fun `resolveLyricScrollAdjustment uses positive scroll when target line sits below viewport center`() {
        val delta = resolveLyricScrollAdjustmentPx(
            itemCenterY = 302f,
            viewportStart = 0,
            viewportEnd = 520,
        )

        assertEquals(42f, delta, 0.01f)
    }

    @Test
    fun `resolveLyricScrollAdjustment uses negative scroll when target line sits above viewport center`() {
        val delta = resolveLyricScrollAdjustmentPx(
            itemCenterY = 188f,
            viewportStart = 0,
            viewportEnd = 520,
        )

        assertEquals(-72f, delta, 0.01f)
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
    fun `resolveLyricLineProgress falls back to line level progress when no real word timing exists`() {
        val progress = resolveLyricLineProgress(
            line = top.imsyy.splayer.nativeapp.model.LyricLineUi(
                startTimeMs = 1_000L,
                endTimeMs = 5_000L,
                mainText = "整句歌词",
            ),
            positionMs = 3_000L,
        )

        assertEquals(0.5f, progress, 0.001f)
    }

    @Test
    fun `resolveLyricGlyphProgress distributes word highlight across visible glyphs`() {
        assertEquals(1f, resolveLyricGlyphProgress(glyphIndex = 0, glyphCount = 3, wordProgress = 0.4f), 0.001f)
        assertEquals(0.2f, resolveLyricGlyphProgress(glyphIndex = 1, glyphCount = 3, wordProgress = 0.4f), 0.001f)
        assertEquals(0f, resolveLyricGlyphProgress(glyphIndex = 2, glyphCount = 3, wordProgress = 0.4f), 0.001f)
    }

    @Test
    fun `hasWordLevelLyricAtPosition only enables high cadence on active timed lines`() {
        val lyrics = listOf(
            top.imsyy.splayer.nativeapp.model.LyricLineUi(
                startTimeMs = 0L,
                mainText = "前奏",
            ),
            top.imsyy.splayer.nativeapp.model.LyricLineUi(
                startTimeMs = 1_000L,
                mainText = "会えた",
                hasWordTiming = true,
                words = listOf(
                    top.imsyy.splayer.nativeapp.model.LyricWordUi(
                        text = "会",
                        startTimeMs = 1_000L,
                        endTimeMs = 1_200L,
                    ),
                    top.imsyy.splayer.nativeapp.model.LyricWordUi(
                        text = "えた",
                        startTimeMs = 1_200L,
                        endTimeMs = 1_450L,
                    ),
                ),
            ),
        )

        assertFalse(hasWordLevelLyricAtPosition(lyrics, 500L))
        assertTrue(hasWordLevelLyricAtPosition(lyrics, 1_050L))
    }

    @Test
    fun `hasWordLevelLyricAtPosition ignores synthetic lrc fallback words`() {
        val lyrics = listOf(
            top.imsyy.splayer.nativeapp.model.LyricLineUi(
                startTimeMs = 0L,
                mainText = "整句歌词",
                hasWordTiming = true,
                words = listOf(
                    top.imsyy.splayer.nativeapp.model.LyricWordUi(
                        text = "整句歌词",
                        startTimeMs = 0L,
                        endTimeMs = 5_000L,
                    ),
                ),
            ),
        )

        assertFalse(hasWordLevelLyricAtPosition(lyrics, 1_500L))
    }

    @Test
    fun `usesWordLevelLyric keeps real word mode when words carry distinct timings even without compatibility flag`() {
        val line = top.imsyy.splayer.nativeapp.model.LyricLineUi(
            startTimeMs = 0L,
            mainText = "假如爱有天意",
            hasWordTiming = false,
            words = listOf(
                top.imsyy.splayer.nativeapp.model.LyricWordUi("假", 0L, 120L),
                top.imsyy.splayer.nativeapp.model.LyricWordUi("如", 120L, 240L),
            ),
        )

        assertTrue(usesWordLevelLyric(line))
        assertEquals(LyricHighlightMode.Word, resolveLyricHighlightMode(line))
    }

    @Test
    fun `usesWordLevelLyric ignores compatibility flag when every word shares the same sentence timeline`() {
        val line = top.imsyy.splayer.nativeapp.model.LyricLineUi(
            startTimeMs = 0L,
            endTimeMs = 5_000L,
            mainText = "整句歌词",
            hasWordTiming = true,
            words = listOf(
                top.imsyy.splayer.nativeapp.model.LyricWordUi("整句", 0L, 5_000L),
                top.imsyy.splayer.nativeapp.model.LyricWordUi("歌词", 0L, 5_000L),
            ),
        )

        assertFalse(usesWordLevelLyric(line))
        assertEquals(LyricHighlightMode.Sentence, resolveLyricHighlightMode(line))
    }

    @Test
    fun `usesWordLevelLyric falls back to sentence mode when multi word line has no distinct timings`() {
        val line = top.imsyy.splayer.nativeapp.model.LyricLineUi(
            startTimeMs = 0L,
            endTimeMs = 5_000L,
            mainText = "整句歌词",
            hasWordTiming = false,
            words = listOf(
                top.imsyy.splayer.nativeapp.model.LyricWordUi("整句", 0L, 5_000L),
                top.imsyy.splayer.nativeapp.model.LyricWordUi("歌词", 0L, 5_000L),
            ),
        )

        assertFalse(hasDistinctWordTimeline(line))
        assertFalse(usesWordLevelLyric(line))
        assertEquals(LyricHighlightMode.Sentence, resolveLyricHighlightMode(line))
    }

    @Test
    fun `usesWordLevelLyric keeps single line fallback on sentence highlight mode`() {
        val line = top.imsyy.splayer.nativeapp.model.LyricLineUi(
            startTimeMs = 0L,
            endTimeMs = 5_000L,
            mainText = "整句歌词",
            hasWordTiming = false,
            words = listOf(
                top.imsyy.splayer.nativeapp.model.LyricWordUi("整句歌词", 0L, 5_000L),
            ),
        )

        assertFalse(usesWordLevelLyric(line))
        assertEquals(LyricHighlightMode.Sentence, resolveLyricHighlightMode(line))
    }

    @Test
    fun `buildLyricAnnotatedText keeps current word line as a whole highlighted sentence`() {
        val annotated = buildLyricAnnotatedText(
            line = top.imsyy.splayer.nativeapp.model.LyricLineUi(
                startTimeMs = 0L,
                mainText = "风吹",
                hasWordTiming = true,
                words = listOf(
                    top.imsyy.splayer.nativeapp.model.LyricWordUi(
                        text = "风",
                        startTimeMs = 0L,
                        endTimeMs = 300L,
                    ),
                    top.imsyy.splayer.nativeapp.model.LyricWordUi(
                        text = "吹",
                        startTimeMs = 300L,
                        endTimeMs = 600L,
                    ),
                ),
            ),
            currentPositionMs = 360L,
            wordHighlightEnabled = true,
            emphasizedColor = androidx.compose.ui.graphics.Color.White,
            upcomingColor = androidx.compose.ui.graphics.Color.Gray,
        )

        assertEquals("风吹", annotated.text)
        assertTrue(annotated.spanStyles.isEmpty())
    }

    @Test
    fun `buildLyricAnnotatedText keeps sentence lyric as whole line highlight without glyph polling`() {
        val annotated = buildLyricAnnotatedText(
            line = top.imsyy.splayer.nativeapp.model.LyricLineUi(
                startTimeMs = 0L,
                endTimeMs = 5_000L,
                mainText = "整句歌词",
                hasWordTiming = false,
                words = listOf(
                    top.imsyy.splayer.nativeapp.model.LyricWordUi(
                        text = "整句歌词",
                        startTimeMs = 0L,
                        endTimeMs = 5_000L,
                    ),
                ),
            ),
            currentPositionMs = 2_000L,
            wordHighlightEnabled = true,
            emphasizedColor = androidx.compose.ui.graphics.Color.White,
            upcomingColor = androidx.compose.ui.graphics.Color.Gray,
        )

        assertEquals("整句歌词", annotated.text)
        assertTrue(annotated.spanStyles.isEmpty())
    }

    @Test
    fun `resolvePreviewSeekResumeState keeps current line latched until playback reaches the preview target`() {
        val state = resolvePreviewSeekResumeState(previewLineIndex = 7)

        assertEquals(7, state.previewLineIndex)
        assertEquals(7, state.lastAutoCenteredLineIndex)
    }

    @Test
    fun `resolveLyricTapAction only seeks when preview anchor is tapped`() {
        assertEquals(
            LyricTapAction.ExitToCover,
            resolveLyricTapAction(LyricScrollMode.AutoFollow, LyricTapTarget.Line),
        )
        assertEquals(
            LyricTapAction.ExitToCover,
            resolveLyricTapAction(LyricScrollMode.ManualPreview, LyricTapTarget.Background),
        )
        assertEquals(
            LyricTapAction.ExitToCover,
            resolveLyricTapAction(LyricScrollMode.ManualPreview, LyricTapTarget.Line),
        )
        assertEquals(
            LyricTapAction.SeekPreviewLine,
            resolveLyricTapAction(LyricScrollMode.ManualPreview, LyricTapTarget.PreviewAnchor),
        )
    }

    @Test
    fun `shouldEnterManualPreview only reacts to real user driven lyric scrolling`() {
        assertFalse(
            shouldEnterManualPreview(
                isScrollInProgress = true,
                autoScrolling = true,
                userScrollActive = true,
                ignoreAutoScrollSignals = false,
                centeredIndex = 3,
                currentLineIndex = 2,
            ),
        )
        assertFalse(
            shouldEnterManualPreview(
                isScrollInProgress = true,
                autoScrolling = false,
                userScrollActive = false,
                ignoreAutoScrollSignals = false,
                centeredIndex = 3,
                currentLineIndex = 2,
            ),
        )
        assertTrue(
            shouldEnterManualPreview(
                isScrollInProgress = true,
                autoScrolling = false,
                userScrollActive = true,
                ignoreAutoScrollSignals = false,
                centeredIndex = 3,
                currentLineIndex = 2,
            ),
        )
        assertFalse(
            shouldEnterManualPreview(
                isScrollInProgress = true,
                autoScrolling = false,
                userScrollActive = true,
                ignoreAutoScrollSignals = true,
                centeredIndex = 3,
                currentLineIndex = 2,
            ),
        )
        assertFalse(
            shouldEnterManualPreview(
                isScrollInProgress = true,
                autoScrolling = false,
                userScrollActive = true,
                ignoreAutoScrollSignals = false,
                centeredIndex = 2,
                currentLineIndex = 2,
            ),
        )
        assertFalse(
            shouldEnterManualPreview(
                isScrollInProgress = true,
                autoScrolling = false,
                userScrollActive = true,
                ignoreAutoScrollSignals = false,
                centeredIndex = null,
                currentLineIndex = 2,
            ),
        )
    }

    @Test
    fun `shouldAutoCenterLyricLine only recenters when auto follow sees a new current line`() {
        assertTrue(
            shouldAutoCenterLyricLine(
                mode = LyricScrollMode.AutoFollow,
                currentLineIndex = 4,
                lastAutoCenteredLineIndex = 3,
            ),
        )
        assertFalse(
            shouldAutoCenterLyricLine(
                mode = LyricScrollMode.AutoFollow,
                currentLineIndex = 4,
                lastAutoCenteredLineIndex = 4,
            ),
        )
        assertFalse(
            shouldAutoCenterLyricLine(
                mode = LyricScrollMode.ManualPreview,
                currentLineIndex = 5,
                lastAutoCenteredLineIndex = 3,
            ),
        )
        assertFalse(
            shouldAutoCenterLyricLine(
                mode = LyricScrollMode.AutoFollow,
                currentLineIndex = -1,
                lastAutoCenteredLineIndex = 3,
            ),
        )
    }

    @Test
    fun `shouldBlockAutoCenterWhilePreviewSeekTargetHasNotBeenReached`() {
        assertTrue(
            shouldBlockAutoCenterForPendingPreviewSeek(
                pendingPreviewSeekTargetLineIndex = 8,
                currentLineIndex = 7,
            ),
        )
        assertFalse(
            shouldBlockAutoCenterForPendingPreviewSeek(
                pendingPreviewSeekTargetLineIndex = 8,
                currentLineIndex = 8,
            ),
        )
        assertFalse(
            shouldBlockAutoCenterForPendingPreviewSeek(
                pendingPreviewSeekTargetLineIndex = -1,
                currentLineIndex = 7,
            ),
        )
    }

    @Test
    fun `shouldUpdateManualPreviewAnchor only reacts while user is actively dragging in preview mode`() {
        assertTrue(
            shouldUpdateManualPreviewAnchor(
                mode = LyricScrollMode.ManualPreview,
                isScrollInProgress = true,
                userScrollActive = true,
                centeredIndex = 5,
            ),
        )
        assertFalse(
            shouldUpdateManualPreviewAnchor(
                mode = LyricScrollMode.ManualPreview,
                isScrollInProgress = false,
                userScrollActive = true,
                centeredIndex = 5,
            ),
        )
        assertFalse(
            shouldUpdateManualPreviewAnchor(
                mode = LyricScrollMode.ManualPreview,
                isScrollInProgress = true,
                userScrollActive = false,
                centeredIndex = 5,
            ),
        )
        assertFalse(
            shouldUpdateManualPreviewAnchor(
                mode = LyricScrollMode.AutoFollow,
                isScrollInProgress = true,
                userScrollActive = true,
                centeredIndex = 5,
            ),
        )
        assertFalse(
            shouldUpdateManualPreviewAnchor(
                mode = LyricScrollMode.ManualPreview,
                isScrollInProgress = true,
                userScrollActive = true,
                centeredIndex = null,
            ),
        )
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
