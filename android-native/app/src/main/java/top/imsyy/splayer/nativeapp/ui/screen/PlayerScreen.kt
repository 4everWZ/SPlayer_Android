package top.imsyy.splayer.nativeapp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Reorder
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import top.imsyy.splayer.nativeapp.R
import top.imsyy.splayer.nativeapp.model.CommentItem
import top.imsyy.splayer.nativeapp.model.LyricLineUi
import top.imsyy.splayer.nativeapp.model.LyricWordUi
import top.imsyy.splayer.nativeapp.model.PlayMode
import top.imsyy.splayer.nativeapp.model.TrackItem
import top.imsyy.splayer.nativeapp.ui.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onClose: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    val overlayState by viewModel.overlayState.collectAsStateWithLifecycle()
    val track = playbackState.currentTrack
    var lyricMode by rememberSaveable(track?.id) { mutableStateOf(false) }
    var seekPreviewPosition by remember(track?.id) { mutableFloatStateOf(playbackState.positionMs.toFloat()) }
    var seeking by remember(track?.id) { mutableStateOf(false) }

    LaunchedEffect(track?.id) {
        viewModel.loadTrackMeta(track)
        lyricMode = false
    }
    LaunchedEffect(playbackState.positionMs, track?.id, seeking) {
        if (!seeking) {
            seekPreviewPosition = playbackState.positionMs.toFloat()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f),
                    ),
                ),
            )
            .systemBarsPadding(),
    ) {
        if (track == null) {
            Text(
                text = "当前没有正在播放的歌曲",
                modifier = Modifier.align(Alignment.Center),
            )
            return
        }

        val sliderValue = if (seeking) seekPreviewPosition else playbackState.positionMs.toFloat()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            PlayerTopBar(
                track = track,
                lyricMode = lyricMode,
                onClose = onClose,
            )

            Spacer(Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (lyricMode) {
                    LyricStage(
                        lyrics = screenState.lyrics,
                        currentPositionMs = playbackState.positionMs,
                        loading = screenState.lyricLoading,
                        showTranslation = screenState.showTranslation,
                        showRomanized = screenState.showRomanized,
                        lyricFontScale = screenState.lyricFontScale,
                        onExitLyric = { lyricMode = false },
                        onSeekToLine = { line -> viewModel.seekTo(line.startTimeMs) },
                    )
                } else {
                    CoverStage(
                        track = track,
                        commentCount = screenState.totalCommentCount,
                        onOpenLyric = { lyricMode = true },
                        onOpenComments = viewModel::openCommentsSheet,
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Slider(
                value = sliderValue.coerceIn(0f, playbackState.durationMs.coerceAtLeast(1L).toFloat()),
                onValueChange = {
                    seeking = true
                    seekPreviewPosition = it
                },
                onValueChangeFinished = {
                    viewModel.seekTo(seekPreviewPosition.toLong())
                    seeking = false
                },
                valueRange = 0f..playbackState.durationMs.coerceAtLeast(1L).toFloat(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(formatDuration(sliderValue.toLong()))
                Text(formatDuration(playbackState.durationMs))
            }

            if (playbackState.isBuffering) {
                Text(
                    text = "缓冲中，系统进度已冻结",
                    modifier = Modifier.padding(top = 6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            } else {
                playbackState.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier.padding(top = 6.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp)
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                IconButton(onClick = viewModel::cyclePlayMode) {
                    PlayModeIcon(playbackState.playMode)
                }
                IconButton(onClick = viewModel::skipPrevious) {
                    Icon(
                        Icons.AutoMirrored.Rounded.NavigateBefore,
                        contentDescription = "上一首",
                        modifier = Modifier.size(34.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(78.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { viewModel.togglePlayback() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "播放暂停",
                        modifier = Modifier.size(34.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                IconButton(onClick = viewModel::skipNext) {
                    Icon(
                        Icons.AutoMirrored.Rounded.NavigateNext,
                        contentDescription = "下一首",
                        modifier = Modifier.size(34.dp),
                    )
                }
                IconButton(onClick = viewModel::openQueueSheet) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "播放队列")
                }
            }
        }

        if (overlayState.showQueue) {
            ModalBottomSheet(onDismissRequest = viewModel::dismissQueueSheet) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Text(
                            "当前播放 ${playbackState.queue.size} 首",
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    itemsIndexed(playbackState.queue, key = { _, item -> item.id }) { index, queueTrack ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.playQueueIndex(index)
                                    viewModel.dismissQueueSheet()
                                }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(queueTrack.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    queueTrack.artists,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(if (queueTrack.id == track.id) "正在播放" else "")
                        }
                    }
                }
            }
        }

        if (overlayState.showComments) {
            ModalBottomSheet(onDismissRequest = viewModel::dismissCommentsSheet) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            AsyncImage(
                                model = track.coverUrl,
                                contentDescription = track.name,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(18.dp)),
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(track.name, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                                Text(
                                    track.artists,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "评论 ${screenState.totalCommentCount}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (screenState.hotComments.isNotEmpty()) {
                        item {
                            Text(
                                "热门评论",
                                modifier = Modifier.padding(horizontal = 20.dp),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        itemsIndexed(screenState.hotComments, key = { _, item -> "hot-${item.id}" }) { _, comment ->
                            CommentCard(comment)
                        }
                    }
                    item {
                        Text(
                            "最新评论",
                            modifier = Modifier.padding(horizontal = 20.dp),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    itemsIndexed(screenState.latestComments, key = { _, item -> "latest-${item.id}" }) { _, comment ->
                        CommentCard(comment)
                    }
                    if (screenState.commentLoading || screenState.loadingMoreComments) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    if (!screenState.commentLoading && screenState.hotComments.isEmpty() && screenState.latestComments.isEmpty()) {
                        item {
                            Text(
                                text = "这首歌还没有拿到评论",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    if (screenState.latestCommentHasMore && !screenState.loadingMoreComments) {
                        item {
                            Button(
                                onClick = viewModel::loadMoreLatestComments,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                            ) {
                                Text("加载更多评论")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerTopBar(
    track: TrackItem,
    lyricMode: Boolean,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "关闭")
            }
            Spacer(Modifier.width(48.dp))
        }
        if (lyricMode) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    track.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                )
                Text(
                    track.artists,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        } else {
            Text(
                text = track.album.ifBlank { "播放中" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CoverStage(
    track: TrackItem,
    commentCount: Int,
    onOpenLyric: () -> Unit,
    onOpenComments: () -> Unit,
    ) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val layout = remember(maxWidth, maxHeight) {
            resolveCoverStageLayout(
                maxWidthDp = maxWidth.value,
                maxHeightDp = maxHeight.value,
            )
        }
        val discSize = layout.discSizeDp.dp
        val artworkSize = layout.artworkSizeDp.dp
        val toneArmSlotWidth = layout.toneArmSlotWidthDp.dp
        val toneArmSlotHeight = layout.toneArmSlotHeightDp.dp
        val compositionSize = max(
            layout.discSizeDp + 28f,
            layout.toneArmSlotWidthDp + 72f,
        ).dp

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = min(layout.visualZoneHeightDp, this@BoxWithConstraints.maxHeight.value).dp)
                    .weight(1f, fill = true),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(compositionSize)
                        .offset(y = layout.visualOffsetYDp.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ToneArmDecoration(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(
                                x = layout.toneArmOffsetXDp.dp,
                                y = layout.toneArmOffsetYDp.dp,
                            ),
                        slotWidth = toneArmSlotWidth,
                        slotHeight = toneArmSlotHeight,
                    )
                    Box(
                        modifier = Modifier
                            .size(discSize)
                            .clip(CircleShape)
                            .background(Color(0xFF0F131B))
                            .align(Alignment.BottomCenter)
                            .clickable(onClick = onOpenLyric),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size((layout.discSizeDp * 0.92f).dp)
                                .clip(CircleShape)
                                .background(Color(0xFF181D27)),
                        )
                        Box(
                            modifier = Modifier
                                .size((layout.discSizeDp * 0.73f).dp)
                                .clip(CircleShape)
                                .background(Color(0xFF090B10)),
                        )
                        AsyncImage(
                            model = track.coverUrl,
                            contentDescription = track.name,
                            modifier = Modifier
                                .size(artworkSize)
                                .clip(CircleShape),
                        )
                        Box(
                            modifier = Modifier
                                .size((layout.discSizeDp * 0.062f).dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2E3441)),
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artists,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(onClick = onOpenComments)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Rounded.ChatBubble, contentDescription = null)
                        Text("评论 ${commentCount.coerceAtLeast(0)}")
                    }
                }
                Text(
                    text = "轻触黑胶进入歌词",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun LyricStage(
    lyrics: List<LyricLineUi>,
    currentPositionMs: Long,
    loading: Boolean,
    showTranslation: Boolean,
    showRomanized: Boolean,
    lyricFontScale: Float,
    onExitLyric: () -> Unit,
    onSeekToLine: (LyricLineUi) -> Unit,
) {
    if (loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (lyrics.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "这首歌暂时没有拿到歌词",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onExitLyric,
            ),
    ) {
        val density = LocalDensity.current
        val lyricListState = rememberLazyListState()
        val lineHeightsPx = remember(lyrics) { mutableStateMapOf<Int, Int>() }
        val viewportLayout = remember(maxHeight) {
            resolveLyricViewportLayout(
                viewportHeightDp = maxHeight.value,
            )
        }
        val viewportHeightPx = remember(maxHeight, density) {
            with(density) { maxHeight.roundToPx() }
        }
        val edgeMinPaddingPx = remember(viewportLayout, density) {
            with(density) { viewportLayout.edgeMinPaddingDp.dp.roundToPx() }
        }
        val lineSpacingPx = remember(viewportLayout, density) {
            with(density) { viewportLayout.lineSpacingDp.dp.roundToPx() }
        }
        val firstLineHeightPx = lineHeightsPx[0] ?: estimateLyricLineHeightPx(
            line = lyrics.first(),
            showTranslation = showTranslation,
            showRomanized = showRomanized,
            lyricFontScale = lyricFontScale,
            density = density,
        )
        val lastLineHeightPx = lineHeightsPx[lyrics.lastIndex] ?: estimateLyricLineHeightPx(
            line = lyrics.last(),
            showTranslation = showTranslation,
            showRomanized = showRomanized,
            lyricFontScale = lyricFontScale,
            density = density,
        )
        val topSpacerPx = resolveLyricEdgeSpacerPx(
            viewportHeightPx = viewportHeightPx,
            lineHeightPx = firstLineHeightPx,
            minimumPaddingPx = edgeMinPaddingPx,
            betweenItemSpacingPx = lineSpacingPx,
        )
        val bottomSpacerPx = resolveLyricEdgeSpacerPx(
            viewportHeightPx = viewportHeightPx,
            lineHeightPx = lastLineHeightPx,
            minimumPaddingPx = edgeMinPaddingPx,
            betweenItemSpacingPx = lineSpacingPx,
        )
        val currentLineIndex by remember(lyrics, currentPositionMs) {
            derivedStateOf { lyrics.indexOfCurrentLine(currentPositionMs) }
        }
        var manualPreviewMode by remember(lyrics) { mutableStateOf(false) }
        var previewLineIndex by remember(lyrics) { mutableIntStateOf(-1) }
        var autoScrolling by remember(lyrics) { mutableStateOf(false) }

        suspend fun centerLyricLine(lineIndex: Int) {
            if (lineIndex !in lyrics.indices) return
            val targetItemIndex = lineIndex + 1
            if (lyricListState.layoutInfo.visibleItemsInfo.none { it.index == targetItemIndex }) {
                lyricListState.scrollToItem(targetItemIndex)
                withFrameNanos { }
            }
            val firstTargetItem = lyricListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetItemIndex }
                ?: return
            val firstCenterDelta = resolveLyricScrollAdjustmentPx(
                itemCenterY = firstTargetItem.offset + firstTargetItem.size / 2f,
                viewportStart = lyricListState.layoutInfo.viewportStartOffset,
                viewportEnd = lyricListState.layoutInfo.viewportEndOffset,
            )
            if (abs(firstCenterDelta) > 1f) {
                lyricListState.animateScrollBy(firstCenterDelta)
                withFrameNanos { }
            }
            val settledTargetItem = lyricListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetItemIndex }
                ?: return
            val settledCenterDelta = resolveLyricScrollAdjustmentPx(
                itemCenterY = settledTargetItem.offset + settledTargetItem.size / 2f,
                viewportStart = lyricListState.layoutInfo.viewportStartOffset,
                viewportEnd = lyricListState.layoutInfo.viewportEndOffset,
            )
            if (abs(settledCenterDelta) > 0.5f) {
                lyricListState.scrollBy(settledCenterDelta)
            }
        }

        LaunchedEffect(lyricListState) {
            snapshotFlow { lyricListState.isScrollInProgress }
                .distinctUntilChanged()
                .collect { isScrolling ->
                    if (isScrolling && !autoScrolling) {
                        manualPreviewMode = true
                        previewLineIndex = lyricListState.layoutInfo.centeredVisibleLyricIndex(
                            firstLyricItemIndex = 1,
                            lyricCount = lyrics.size,
                        ) ?: currentLineIndex
                    }
                }
        }

        LaunchedEffect(lyricListState, manualPreviewMode, currentLineIndex) {
            if (!manualPreviewMode) {
                previewLineIndex = currentLineIndex
                return@LaunchedEffect
            }
            snapshotFlow {
                lyricListState.layoutInfo.centeredVisibleLyricIndex(
                    firstLyricItemIndex = 1,
                    lyricCount = lyrics.size,
                )
            }
                .distinctUntilChanged()
                .collect { centeredIndex ->
                    previewLineIndex = centeredIndex ?: currentLineIndex
                }
        }

        LaunchedEffect(currentLineIndex, manualPreviewMode, lyrics, viewportLayout, topSpacerPx, bottomSpacerPx) {
            if (!manualPreviewMode && currentLineIndex >= 0) {
                autoScrolling = true
                try {
                    centerLyricLine(currentLineIndex)
                } finally {
                    autoScrolling = false
                }
            }
        }

        LaunchedEffect(
            manualPreviewMode,
            previewLineIndex,
            topSpacerPx,
            bottomSpacerPx,
            lyricListState.isScrollInProgress,
        ) {
            if (!manualPreviewMode || lyricListState.isScrollInProgress || previewLineIndex !in lyrics.indices) {
                return@LaunchedEffect
            }
            autoScrolling = true
            try {
                withFrameNanos { }
                centerLyricLine(previewLineIndex)
            } finally {
                autoScrolling = false
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = lyricListState,
            contentPadding = PaddingValues(
                top = viewportLayout.topPaddingDp.dp,
                bottom = viewportLayout.bottomPaddingDp.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(viewportLayout.lineSpacingDp.dp),
        ) {
            item(key = "lyric-top-spacer") {
                Spacer(
                    modifier = Modifier.height(
                        with(density) { topSpacerPx.toDp() },
                    ),
                )
            }
            itemsIndexed(lyrics, key = { _, line -> "${line.startTimeMs}-${line.mainText}" }) { index, line ->
                val currentActive = index == currentLineIndex
                val previewActive = manualPreviewMode && index == previewLineIndex
                val emphasizeLine = previewActive || (!manualPreviewMode && currentActive)
                val mainTextColor = when {
                    previewActive -> MaterialTheme.colorScheme.onSurface
                    currentActive && !manualPreviewMode -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.34f)
                }
                val upcomingWordColor = when {
                    previewActive -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    currentActive && !manualPreviewMode -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.36f)
                    else -> mainTextColor
                }
                val onLineClick = onExitLyric
                val onPreviewSeek = {
                    manualPreviewMode = false
                    previewLineIndex = currentLineIndex
                    onSeekToLine(line)
                }
                val lyricText = remember(
                    line,
                    currentPositionMs,
                    currentActive,
                    manualPreviewMode,
                    previewActive,
                    mainTextColor,
                    upcomingWordColor,
                ) {
                    buildLyricAnnotatedText(
                        line = line,
                        currentPositionMs = currentPositionMs,
                        wordHighlightEnabled = currentActive && !manualPreviewMode && !previewActive,
                        emphasizedColor = mainTextColor,
                        upcomingColor = upcomingWordColor,
                    )
                }

                Column(
                    modifier = Modifier
                        .widthIn(max = 620.dp)
                        .fillMaxWidth()
                        .onSizeChanged { lineHeightsPx[index] = it.height }
                        .clip(RoundedCornerShape(if (previewActive) 24.dp else 0.dp))
                        .background(
                            if (previewActive) {
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                            } else {
                                Color.Transparent
                            },
                        )
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                            onClick = onLineClick,
                        )
                        .padding(
                            horizontal = viewportLayout.horizontalPaddingDp.dp,
                            vertical = if (previewActive) viewportLayout.previewPaddingDp.dp else viewportLayout.linePaddingDp.dp,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (previewActive) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(999.dp))
                                .clickable(onClick = onPreviewSeek)
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = formatDuration(line.startTimeMs),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = "跳到这一句",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        text = lyricText,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = MaterialTheme.typography.titleLarge.fontSize * lyricFontScale,
                            fontWeight = if (emphasizeLine) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        textAlign = TextAlign.Center,
                        color = mainTextColor,
                    )
                    if (showTranslation && line.translation.isNotBlank()) {
                        Text(
                            text = line.translation,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = MaterialTheme.typography.titleMedium.fontSize * lyricFontScale * 0.82f,
                            ),
                            textAlign = TextAlign.Center,
                            color = when {
                                previewActive -> MaterialTheme.colorScheme.onSurfaceVariant
                                currentActive && !manualPreviewMode -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
                            },
                        )
                    }
                    if (showRomanized && line.romanized.isNotBlank()) {
                        Text(
                            text = line.romanized,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize * lyricFontScale * 0.78f,
                            ),
                            textAlign = TextAlign.Center,
                            color = when {
                                previewActive -> MaterialTheme.colorScheme.onSurfaceVariant
                                currentActive && !manualPreviewMode -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            },
                        )
                    }
                }
            }
            item(key = "lyric-bottom-spacer") {
                Spacer(
                    modifier = Modifier.height(
                        with(density) { bottomSpacerPx.toDp() },
                    ),
                )
            }
        }
    }
}

@Composable
private fun CommentCard(comment: CommentItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AsyncImage(
            model = comment.userAvatar,
            contentDescription = comment.userName,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(comment.userName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        formatCommentTime(comment.time),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "赞 ${comment.likedCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = comment.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ToneArmDecoration(
    modifier: Modifier = Modifier,
    slotWidth: Dp,
    slotHeight: Dp,
) {
    val scale = minOf(slotWidth.value / 158f, slotHeight.value / 150f)
    Box(
        modifier = modifier.size(width = slotWidth, height = slotHeight),
    ) {
        Box(
            modifier = Modifier
                .size((28f * scale).dp)
                .clip(CircleShape)
                .background(Color(0xFF4C5361))
                .align(Alignment.TopEnd),
        )
        Box(
            modifier = Modifier
                .width((122f * scale).dp)
                .height((10f * scale).dp)
                .offset(x = (18f * scale).dp, y = (28f * scale).dp)
                .rotate(33f)
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xFFF2F3F6)),
        )
        Box(
            modifier = Modifier
                .width((56f * scale).dp)
                .height((8f * scale).dp)
                .offset(x = (76f * scale).dp, y = (90f * scale).dp)
                .rotate(58f)
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xFFF8F8FB)),
        )
        Box(
            modifier = Modifier
                .size((18f * scale).dp)
                .offset(x = (105f * scale).dp, y = (112f * scale).dp)
                .rotate(58f)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFE9EBEF)),
        )
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1_000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun formatCommentTime(timestampMs: Long): String {
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(timestampMs))
    }.getOrDefault("")
}

private fun List<LyricLineUi>.indexOfCurrentLine(positionMs: Long): Int {
    if (isEmpty()) return -1
    val candidate = indexOfLast { it.startTimeMs <= positionMs }
    return if (candidate >= 0) candidate else 0
}

internal data class CenteredLyricCandidate(
    val index: Int,
    val centerY: Float,
)

internal data class CoverStageLayout(
    val discSizeDp: Float,
    val visualZoneHeightDp: Float,
    val visualOffsetYDp: Float,
    val artworkSizeDp: Float,
    val toneArmSlotWidthDp: Float,
    val toneArmSlotHeightDp: Float,
    val toneArmOffsetXDp: Float,
    val toneArmOffsetYDp: Float,
)

internal data class LyricViewportLayout(
    val topPaddingDp: Float,
    val bottomPaddingDp: Float,
    val lineSpacingDp: Float,
    val horizontalPaddingDp: Float,
    val linePaddingDp: Float,
    val previewPaddingDp: Float,
    val edgeMinPaddingDp: Float,
)

internal fun resolveCoverStageLayout(
    maxWidthDp: Float,
    maxHeightDp: Float,
): CoverStageLayout {
    val discCap = minOf(584f, maxWidthDp * 1.34f)
    val discSize = minOf(
        maxWidthDp * 1.28f,
        (maxHeightDp - 8f).coerceAtLeast(0f) * 1.02f,
        discCap,
    ).roundToInt().toFloat()
    val visualOffset = (maxHeightDp * 0.085f).coerceIn(28f, 48f)
    val visualZoneHeight = maxOf(
        discSize + visualOffset + 12f,
        maxHeightDp * 0.64f,
    ).roundToInt().toFloat()
    return CoverStageLayout(
        discSizeDp = discSize,
        visualZoneHeightDp = visualZoneHeight,
        visualOffsetYDp = visualOffset.roundToInt().toFloat(),
        artworkSizeDp = (discSize * 0.66f).roundToInt().toFloat(),
        toneArmSlotWidthDp = (discSize * 0.56f).roundToInt().toFloat(),
        toneArmSlotHeightDp = (discSize * 0.48f).roundToInt().toFloat(),
        toneArmOffsetXDp = -(discSize * 0.028f).roundToInt().toFloat(),
        toneArmOffsetYDp = -(discSize * 0.018f).roundToInt().toFloat(),
    )
}

internal fun resolveLyricViewportLayout(
    viewportHeightDp: Float,
): LyricViewportLayout {
    return LyricViewportLayout(
        topPaddingDp = 0f,
        bottomPaddingDp = 0f,
        lineSpacingDp = 10f,
        horizontalPaddingDp = if (viewportHeightDp >= 560f) 6f else 4f,
        linePaddingDp = 2f,
        previewPaddingDp = 8f,
        edgeMinPaddingDp = 20f,
    )
}

internal fun resolveLyricEdgeSpacerPx(
    viewportHeightPx: Int,
    lineHeightPx: Int,
    minimumPaddingPx: Int,
    betweenItemSpacingPx: Int,
): Int {
    if (viewportHeightPx <= 0) return minimumPaddingPx
    val centeredPadding = ((viewportHeightPx - lineHeightPx) / 2f).roundToInt() - betweenItemSpacingPx
    return centeredPadding.coerceAtLeast(minimumPaddingPx)
}

internal fun estimateLyricLineHeightPx(
    line: LyricLineUi,
    showTranslation: Boolean,
    showRomanized: Boolean,
    lyricFontScale: Float,
    density: Density,
): Int {
    return with(density) {
        val mainHeight = (46.dp * lyricFontScale).roundToPx()
        val translationHeight = if (showTranslation && line.translation.isNotBlank()) {
            (28.dp * lyricFontScale).roundToPx()
        } else {
            0
        }
        val romanizedHeight = if (showRomanized && line.romanized.isNotBlank()) {
            (24.dp * lyricFontScale).roundToPx()
        } else {
            0
        }
        val extraSpacing = when {
            translationHeight > 0 && romanizedHeight > 0 -> 16.dp.roundToPx()
            translationHeight > 0 || romanizedHeight > 0 -> 10.dp.roundToPx()
            else -> 4.dp.roundToPx()
        }
        mainHeight + translationHeight + romanizedHeight + extraSpacing
    }
}

internal fun calculateLyricCenterDelta(
    itemCenterY: Float,
    viewportStart: Int,
    viewportEnd: Int,
): Float {
    return itemCenterY - ((viewportStart + viewportEnd) / 2f)
}

internal fun resolveLyricScrollAdjustmentPx(
    itemCenterY: Float,
    viewportStart: Int,
    viewportEnd: Int,
): Float {
    return -calculateLyricCenterDelta(
        itemCenterY = itemCenterY,
        viewportStart = viewportStart,
        viewportEnd = viewportEnd,
    )
}

internal fun findCenteredLyricIndex(
    candidates: List<CenteredLyricCandidate>,
    viewportCenter: Float,
): Int? {
    if (candidates.isEmpty()) return null
    return candidates.minByOrNull { candidate ->
        abs(candidate.centerY - viewportCenter)
    }?.index
}

internal fun resolveLyricWordProgress(
    word: LyricWordUi,
    positionMs: Long,
): Float {
    if (positionMs <= word.startTimeMs) return 0f
    if (word.endTimeMs <= word.startTimeMs) return if (positionMs > word.startTimeMs) 1f else 0f
    val durationMs = (word.endTimeMs - word.startTimeMs).toFloat()
    return ((positionMs - word.startTimeMs) / durationMs).coerceIn(0f, 1f)
}

internal fun resolveLyricGlyphProgress(
    glyphIndex: Int,
    glyphCount: Int,
    wordProgress: Float,
): Float {
    if (glyphCount <= 0 || glyphIndex !in 0 until glyphCount) return 0f
    return (wordProgress.coerceIn(0f, 1f) * glyphCount - glyphIndex).coerceIn(0f, 1f)
}

private fun buildLyricAnnotatedText(
    line: LyricLineUi,
    currentPositionMs: Long,
    wordHighlightEnabled: Boolean,
    emphasizedColor: Color,
    upcomingColor: Color,
): AnnotatedString {
    if (!wordHighlightEnabled || line.words.isEmpty()) {
        return AnnotatedString(line.mainText)
    }
    return buildAnnotatedString {
        line.words.forEach { word ->
            val progress = resolveLyricWordProgress(word, currentPositionMs)
            val highlightableGlyphs = word.text.indices.filter { index ->
                !word.text[index].isWhitespace()
            }
            if (highlightableGlyphs.isEmpty()) {
                append(word.text)
                return@forEach
            }
            var highlightCursor = 0
            word.text.forEachIndexed { _, char ->
                val charText = char.toString()
                if (char.isWhitespace()) {
                    pushStyle(SpanStyle(color = upcomingColor))
                    append(charText)
                    pop()
                    return@forEachIndexed
                }
                val glyphProgress = resolveLyricGlyphProgress(
                    glyphIndex = highlightCursor,
                    glyphCount = highlightableGlyphs.size,
                    wordProgress = progress,
                )
                val glyphColor = when {
                    glyphProgress >= 1f -> emphasizedColor
                    glyphProgress > 0f -> lerp(upcomingColor, emphasizedColor, glyphProgress)
                    else -> upcomingColor
                }
                pushStyle(
                    SpanStyle(
                        color = glyphColor,
                        fontWeight = if (glyphProgress > 0f) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                )
                append(charText)
                pop()
                highlightCursor += 1
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListLayoutInfo.centeredVisibleLyricIndex(
    firstLyricItemIndex: Int,
    lyricCount: Int,
): Int? {
    return findCenteredLyricIndex(
        candidates = visibleItemsInfo
            .filter { item -> item.index in firstLyricItemIndex until (firstLyricItemIndex + lyricCount) }
            .map { item ->
            CenteredLyricCandidate(
                index = item.index - firstLyricItemIndex,
                centerY = item.offset + item.size / 2f,
            )
        },
        viewportCenter = (viewportStartOffset + viewportEndOffset) / 2f,
    )
}

@Composable
private fun PlayModeIcon(playMode: PlayMode) {
    when (playMode) {
        PlayMode.HEART -> Icon(
            painter = painterResource(R.drawable.ic_heartbit),
            contentDescription = "播放模式",
        )
        PlayMode.SEQUENCE -> Icon(Icons.Rounded.Reorder, contentDescription = "播放模式")
        PlayMode.LIST_LOOP -> Icon(Icons.Rounded.Repeat, contentDescription = "播放模式")
        PlayMode.SINGLE_LOOP -> Icon(Icons.Rounded.RepeatOne, contentDescription = "播放模式")
        PlayMode.SHUFFLE -> Icon(Icons.Rounded.Shuffle, contentDescription = "播放模式")
    }
}
