package top.imsyy.splayer.nativeapp.ui.screen

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.rounded.Favorite
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
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
import kotlinx.coroutines.isActive
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
        seekPreviewPosition = playbackState.positionMs.toFloat()
        seeking = false
    }
    DisposableEffect(viewModel) {
        onDispose {
            viewModel.setPlayerScreenCadence(
                playerScreenActive = false,
                lyricScreenActive = false,
                wordLevelLyricActive = false,
            )
        }
    }
    LaunchedEffect(track?.id, lyricMode) {
        viewModel.setPlayerScreenCadence(
            playerScreenActive = true,
            lyricScreenActive = lyricMode,
            wordLevelLyricActive = false,
        )
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

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val bottomSafeGapDp = remember(maxHeight) {
                resolvePlayerBottomSafeGapDp(maxHeight.value)
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 0.dp),
            ) {
                PlayerTopBar(
                    track = track,
                    lyricMode = lyricMode,
                    onClose = onClose,
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    if (lyricMode) {
                        LyricStage(
                            lyrics = screenState.lyrics,
                            currentPositionMs = resolveLyricDisplayPositionMs(playbackState.positionMs),
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
                            currentTrackLiked = screenState.currentTrackLiked,
                            likeLoading = screenState.likeLoading,
                            isPlaying = playbackState.isPlaying,
                            isBuffering = playbackState.isBuffering,
                            onOpenLyric = { lyricMode = true },
                            onOpenComments = viewModel::openCommentsSheet,
                            onToggleLike = viewModel::toggleLikeCurrentTrack,
                        )
                    }

                    if (lyricMode) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .height(resolvePlayerLyricToggleSlotHeightDp().dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            LyricDisplayToggleRow(
                                showTranslation = screenState.showTranslation,
                                showRomanized = screenState.showRomanized,
                                onToggleTranslation = { viewModel.setShowTranslation(!screenState.showTranslation) },
                                onToggleRomanized = { viewModel.setShowRomanized(!screenState.showRomanized) },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(resolvePlayerStageToProgressGapDp(lyricMode).dp))

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

                val statusMessage = when {
                    playbackState.isBuffering -> "缓冲中，系统进度已冻结"
                    else -> playbackState.errorMessage?.takeIf { it.isNotBlank() }
                }
                val statusColor = if (playbackState.isBuffering) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(resolvePlayerStatusSlotHeightDp().dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    statusMessage?.let { message ->
                        Text(
                            text = message,
                            color = statusColor,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 0.dp, bottom = 4.dp),
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
                Spacer(Modifier.height(bottomSafeGapDp.dp))
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
private fun LyricDisplayToggleRow(
    showTranslation: Boolean,
    showRomanized: Boolean,
    onToggleTranslation: () -> Unit,
    onToggleRomanized: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LyricDisplayToggleChip(
                text = "译",
                selected = showTranslation,
                onClick = onToggleTranslation,
            )
            LyricDisplayToggleChip(
                text = "音",
                selected = showRomanized,
                onClick = onToggleRomanized,
            )
        }
    }
}

@Composable
private fun LyricDisplayToggleChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.96f)
    } else {
        Color.Transparent
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .height(30.dp)
            .width(40.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun CoverStage(
    track: TrackItem,
    commentCount: Int,
    currentTrackLiked: Boolean,
    likeLoading: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onOpenLyric: () -> Unit,
    onOpenComments: () -> Unit,
    onToggleLike: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val layout = remember(maxWidth, maxHeight) {
            resolveCoverStageLayout(
                maxWidthDp = maxWidth.value,
                maxHeightDp = maxHeight.value,
            )
        }
        val animateDisc = shouldRunDiscRotation(
            isPlaying = isPlaying,
            isBuffering = isBuffering,
        )
        val discRotation = remember(track.id) { Animatable(0f) }
        LaunchedEffect(track.id, animateDisc) {
            if (!animateDisc) {
                discRotation.stop()
                return@LaunchedEffect
            }
            while (isActive) {
                val startRotation = normalizeDiscRotation(discRotation.value)
                discRotation.snapTo(startRotation)
                discRotation.animateTo(
                    targetValue = startRotation + 360f,
                    animationSpec = tween(
                        durationMillis = DISC_ROTATION_CYCLE_MS,
                        easing = LinearEasing,
                    ),
                )
            }
        }
        val discSize = layout.discSizeDp.dp
        val artworkSize = layout.artworkSizeDp.dp
        val toneArmSlotWidth = layout.toneArmSlotWidthDp.dp
        val toneArmSlotHeight = layout.toneArmSlotHeightDp.dp
        val compositionSize = layout.compositionSizeDp.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = layout.stageTopOffsetDp.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(min(layout.visualZoneHeightDp, this@BoxWithConstraints.maxHeight.value).dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    modifier = Modifier
                        .size(compositionSize)
                        .offset(y = layout.visualTopInsetDp.dp),
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
                            .rotate(normalizeDiscRotation(discRotation.value))
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
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = layout.infoBottomPaddingDp.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
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
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconButton(
                            enabled = !likeLoading,
                            onClick = onToggleLike,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                        ) {
                            if (likeLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    imageVector = if (currentTrackLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = if (currentTrackLiked) "取消喜欢" else "喜欢",
                                    tint = if (currentTrackLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
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
        val viewConfiguration = LocalViewConfiguration.current
        val initialLineIndex = remember(lyrics) {
            lyrics.indexOfCurrentLine(currentPositionMs).coerceAtLeast(0)
        }
        val lyricListState = rememberLazyListState(
            initialFirstVisibleItemIndex = (initialLineIndex + 1).coerceAtMost(lyrics.size),
        )
        val lineHeightsPx = remember(lyrics) { mutableStateMapOf<Int, Int>() }
        val viewportLayout = remember(maxHeight) {
            resolveLyricViewportLayout(
                viewportHeightDp = maxHeight.value,
            )
        }
        val lyricContentWidthDp = remember(maxWidth) {
            resolveLyricContentWidthDp(maxWidth.value)
        }
        val fallbackViewportHeightPx = remember(maxHeight, density) {
            with(density) { maxHeight.roundToPx() }
        }
        val viewportHeightPx by remember(lyricListState, fallbackViewportHeightPx) {
            derivedStateOf {
                resolveLyricViewportHeightPx(
                    viewportStart = lyricListState.layoutInfo.viewportStartOffset,
                    viewportEnd = lyricListState.layoutInfo.viewportEndOffset,
                    fallbackHeightPx = fallbackViewportHeightPx,
                )
            }
        }
        val edgeMinPaddingPx = remember(viewportLayout, density) {
            with(density) { viewportLayout.edgeMinPaddingDp.dp.roundToPx() }
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
        )
        val bottomSpacerPx = resolveLyricEdgeSpacerPx(
            viewportHeightPx = viewportHeightPx,
            lineHeightPx = lastLineHeightPx,
            minimumPaddingPx = edgeMinPaddingPx,
        )
        val currentLineIndex by remember(lyrics, currentPositionMs) {
            derivedStateOf { lyrics.indexOfCurrentLine(currentPositionMs) }
        }
        var scrollMode by remember(lyrics) { mutableStateOf(LyricScrollMode.AutoFollow) }
        var previewLineIndex by remember(lyrics) { mutableIntStateOf(-1) }
        var pendingPreviewSeekTargetLineIndex by remember(lyrics) { mutableIntStateOf(-1) }
        var lastAutoCenteredLineIndex by remember(lyrics) { mutableIntStateOf(-1) }
        var initialAutoCenterSettled by remember(lyrics) { mutableStateOf(false) }
        var autoScrolling by remember(lyrics) { mutableStateOf(false) }
        var userScrollActive by remember(lyrics) { mutableStateOf(false) }
        var ignoreScrollSignalsUntilMs by remember(lyrics) { mutableStateOf(0L) }
        val previewLine = lyrics.getOrNull(previewLineIndex)

        suspend fun centerLyricLine(lineIndex: Int, animate: Boolean): Boolean {
            if (lineIndex !in lyrics.indices) return false
            val targetItemIndex = lineIndex + 1
            if (lyricListState.layoutInfo.visibleItemsInfo.none { it.index == targetItemIndex }) {
                if (animate) {
                    lyricListState.animateScrollToItem(targetItemIndex)
                } else {
                    lyricListState.scrollToItem(targetItemIndex)
                }
                withFrameNanos { }
            }
            repeat(3) {
                val targetItem = lyricListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetItemIndex }
                    ?: return@repeat
                val centerDelta = resolveLyricScrollAdjustmentPx(
                    itemCenterY = targetItem.offset + targetItem.size / 2f,
                    viewportStart = lyricListState.layoutInfo.viewportStartOffset,
                    viewportEnd = lyricListState.layoutInfo.viewportEndOffset,
                )
                if (abs(centerDelta) <= 0.5f) return true
                if (animate) {
                    lyricListState.animateScrollBy(
                        value = centerDelta,
                        animationSpec = tween(durationMillis = LYRIC_AUTO_CENTER_ANIMATION_MS),
                    )
                } else {
                    lyricListState.scrollBy(centerDelta)
                }
                withFrameNanos { }
            }
            return lyricListState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == targetItemIndex }
                ?.let { targetItem ->
                    abs(
                        calculateLyricCenterDelta(
                            itemCenterY = targetItem.offset + targetItem.size / 2f,
                            viewportStart = lyricListState.layoutInfo.viewportStartOffset,
                            viewportEnd = lyricListState.layoutInfo.viewportEndOffset,
                        ),
                    ) <= 1f
                }
                ?: false
        }

        LaunchedEffect(lyricListState, lyrics) {
            snapshotFlow {
                Triple(
                    lyricListState.isScrollInProgress,
                    lyricListState.layoutInfo.centeredVisibleLyricIndex(
                        firstLyricItemIndex = 1,
                        lyricCount = lyrics.size,
                    ),
                    userScrollActive,
                )
            }
                .distinctUntilChanged()
                .collect { (isScrolling, centeredIndex, userDragging) ->
                    val ignoreAutoScrollSignals = SystemClock.elapsedRealtime() < ignoreScrollSignalsUntilMs
                    if (
                        shouldEnterManualPreview(
                            isScrollInProgress = isScrolling,
                            autoScrolling = autoScrolling,
                            userScrollActive = userDragging,
                            ignoreAutoScrollSignals = ignoreAutoScrollSignals,
                            centeredIndex = centeredIndex,
                            currentLineIndex = currentLineIndex,
                        )
                    ) {
                        scrollMode = LyricScrollMode.ManualPreview
                        previewLineIndex = centeredIndex ?: currentLineIndex
                    }
                }
        }

        LaunchedEffect(lyricListState, scrollMode, lyrics) {
            if (scrollMode == LyricScrollMode.AutoFollow) {
                previewLineIndex = currentLineIndex
                return@LaunchedEffect
            }
            snapshotFlow {
                Triple(
                    lyricListState.isScrollInProgress,
                    userScrollActive,
                    lyricListState.layoutInfo.centeredVisibleLyricIndex(
                        firstLyricItemIndex = 1,
                        lyricCount = lyrics.size,
                    ),
                )
            }
                .distinctUntilChanged()
                .collect { (isScrollInProgress, userDragging, centeredIndex) ->
                    if (shouldUpdateManualPreviewAnchor(
                            mode = scrollMode,
                            isScrollInProgress = isScrollInProgress,
                            userScrollActive = userDragging,
                            centeredIndex = centeredIndex,
                        )
                    ) {
                        previewLineIndex = centeredIndex ?: return@collect
                    }
                }
        }

        LaunchedEffect(scrollMode) {
            if (scrollMode != LyricScrollMode.AutoFollow) {
                lastAutoCenteredLineIndex = -1
            }
        }

        LaunchedEffect(currentLineIndex, scrollMode, topSpacerPx, bottomSpacerPx, pendingPreviewSeekTargetLineIndex) {
            if (!shouldBlockAutoCenterForPendingPreviewSeek(pendingPreviewSeekTargetLineIndex, currentLineIndex)) {
                pendingPreviewSeekTargetLineIndex = -1
            }
            if (shouldBlockAutoCenterForPendingPreviewSeek(pendingPreviewSeekTargetLineIndex, currentLineIndex)) {
                return@LaunchedEffect
            }
            if (shouldAutoCenterLyricLine(scrollMode, currentLineIndex, lastAutoCenteredLineIndex)) {
                ignoreScrollSignalsUntilMs = SystemClock.elapsedRealtime() + 640L
                autoScrolling = true
                userScrollActive = false
                val animateCenter = initialAutoCenterSettled
                try {
                    if (centerLyricLine(currentLineIndex, animate = animateCenter)) {
                        lastAutoCenteredLineIndex = currentLineIndex
                    }
                } finally {
                    initialAutoCenterSettled = true
                    autoScrolling = false
                    ignoreScrollSignalsUntilMs = SystemClock.elapsedRealtime() + 420L
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(
                        if (
                            shouldRevealLyricListBeforeFirstCenter(
                                initialAutoCenterSettled = initialAutoCenterSettled,
                                mode = scrollMode,
                            )
                        ) {
                            1f
                        } else {
                            0f
                        },
                    )
                    .pointerInput(lyrics, viewConfiguration) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            userScrollActive = false
                            var dragStarted = false
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val dragChange = event.changes.firstOrNull { it.id == down.id }
                                    ?: break
                                if (!dragChange.pressed) break
                                if (!dragStarted && abs(dragChange.position.y - down.position.y) >= viewConfiguration.touchSlop) {
                                    dragStarted = true
                                }
                                userScrollActive = dragStarted
                            } while (true)
                            userScrollActive = false
                        }
                    },
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
                    val previewActive = scrollMode == LyricScrollMode.ManualPreview && index == previewLineIndex
                    val currentHighlightActive = currentActive && !previewActive
                    val emphasizeLine = previewActive || currentHighlightActive
                    val lineHasWordTiming = remember(line) { usesWordLevelLyric(line) }
                    val highlightScale by animateFloatAsState(
                        targetValue = when {
                            previewActive -> 1.035f
                            currentHighlightActive && lineHasWordTiming -> 1.04f
                            currentHighlightActive -> 1.028f
                            else -> 1f
                        },
                        label = "lyric-line-scale",
                    )
                    val mainTextColor = when {
                        previewActive -> MaterialTheme.colorScheme.onSurface
                        currentHighlightActive -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
                    }
                    val upcomingWordColor = when {
                        previewActive -> MaterialTheme.colorScheme.onSurface
                        currentHighlightActive -> mainTextColor
                        else -> mainTextColor
                    }
                    val lyricText = remember(
                        line,
                        currentActive,
                        scrollMode,
                        previewActive,
                        mainTextColor,
                        upcomingWordColor,
                    ) {
                        buildLyricAnnotatedText(
                            line = line,
                            currentPositionMs = 0L,
                            wordHighlightEnabled = false,
                            emphasizedColor = mainTextColor,
                            upcomingColor = upcomingWordColor,
                        )
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            modifier = Modifier
                                .width(lyricContentWidthDp.dp)
                                .onSizeChanged { lineHeightsPx[index] = it.height }
                                .clip(RoundedCornerShape(if (previewActive) 24.dp else 0.dp))
                                .background(
                                    Color.Transparent,
                                )
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        when (resolveLyricTapAction(scrollMode, LyricTapTarget.Line)) {
                                            LyricTapAction.ExitToCover -> onExitLyric()
                                            LyricTapAction.SeekPreviewLine -> Unit
                                        }
                                    },
                                )
                                .padding(
                                    horizontal = viewportLayout.horizontalPaddingDp.dp,
                                    vertical = if (previewActive) viewportLayout.previewPaddingDp.dp else viewportLayout.linePaddingDp.dp,
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = lyricText,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .scale(highlightScale),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = MaterialTheme.typography.titleLarge.fontSize * resolveLyricMainTextScale(lyricFontScale),
                                    fontWeight = if (emphasizeLine) FontWeight.Bold else FontWeight.Medium,
                                ),
                                textAlign = TextAlign.Center,
                                color = if (previewActive) Color.Transparent else mainTextColor,
                            )
                            if (showTranslation && line.translation.isNotBlank()) {
                                Text(
                                    text = line.translation,
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = MaterialTheme.typography.titleMedium.fontSize *
                                            resolveLyricTranslationTextScale(lyricFontScale),
                                    ),
                                    textAlign = TextAlign.Center,
                                    color = when {
                                        previewActive -> Color.Transparent
                                        currentHighlightActive -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f)
                                    },
                                )
                            }
                            if (showRomanized && line.romanized.isNotBlank()) {
                                Text(
                                    text = line.romanized,
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = MaterialTheme.typography.bodyMedium.fontSize *
                                            resolveLyricRomanizedTextScale(lyricFontScale),
                                    ),
                                    textAlign = TextAlign.Center,
                                    color = when {
                                        previewActive -> Color.Transparent
                                        currentHighlightActive -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.46f)
                                    },
                                )
                            }
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

            if (scrollMode == LyricScrollMode.ManualPreview && previewLine != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(lyricContentWidthDp.dp)
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
                        .clickable {
                            when (resolveLyricTapAction(scrollMode, LyricTapTarget.PreviewAnchor)) {
                                LyricTapAction.ExitToCover -> onExitLyric()
                                LyricTapAction.SeekPreviewLine -> {
                                    val resumeState = resolvePreviewSeekResumeState(previewLineIndex)
                                    scrollMode = LyricScrollMode.AutoFollow
                                    pendingPreviewSeekTargetLineIndex = previewLineIndex
                                    previewLineIndex = resumeState.previewLineIndex
                                    lastAutoCenteredLineIndex = resumeState.lastAutoCenteredLineIndex
                                    ignoreScrollSignalsUntilMs = SystemClock.elapsedRealtime() + 1_100L
                                    onSeekToLine(previewLine)
                                }
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatDuration(previewLine.startTimeMs),
                        modifier = Modifier.width(54.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = previewLine.mainText,
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = MaterialTheme.typography.titleLarge.fontSize *
                                    resolvePreviewAnchorMainTextScale(lyricFontScale),
                                fontWeight = FontWeight.Bold,
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        if (showTranslation && previewLine.translation.isNotBlank()) {
                            Text(
                                text = previewLine.translation,
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = MaterialTheme.typography.titleMedium.fontSize *
                                        resolveLyricTranslationTextScale(lyricFontScale),
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = "跳到这一句",
                        modifier = Modifier.size(34.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
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

internal fun List<LyricLineUi>.indexOfCurrentLine(positionMs: Long): Int {
    if (isEmpty()) return -1
    val boundedCandidate = indexOfLast { line ->
        line.startTimeMs <= positionMs &&
            (
                line.endTimeMs <= line.startTimeMs ||
                    positionMs < line.endTimeMs
            )
    }
    if (boundedCandidate >= 0) return boundedCandidate
    val trailingCandidate = indexOfLast { it.startTimeMs <= positionMs }
    return if (trailingCandidate >= 0) trailingCandidate else 0
}

internal const val LYRIC_DISPLAY_ADVANCE_MS = 500L

internal fun resolveLyricDisplayPositionMs(positionMs: Long): Long {
    return (positionMs + LYRIC_DISPLAY_ADVANCE_MS).coerceAtLeast(0L)
}

internal fun hasWordLevelLyricAtPosition(
    lyrics: List<LyricLineUi>,
    positionMs: Long,
): Boolean {
    val currentLineIndex = lyrics.indexOfCurrentLine(positionMs)
    return currentLineIndex in lyrics.indices &&
        resolveLyricHighlightMode(lyrics[currentLineIndex]) == LyricHighlightMode.Word
}

internal fun hasWordLevelLyric(
    lyrics: List<LyricLineUi>,
): Boolean {
    return lyrics.any { line -> usesWordLevelLyric(line) }
}

internal const val DISC_ROTATION_CYCLE_MS = 18_000
internal const val LYRIC_AUTO_CENTER_ANIMATION_MS = 260

internal fun shouldRunDiscRotation(
    isPlaying: Boolean,
    isBuffering: Boolean,
): Boolean {
    return isPlaying && !isBuffering
}

internal fun normalizeDiscRotation(
    degrees: Float,
): Float {
    return ((degrees % 360f) + 360f) % 360f
}

internal fun resolveLyricMainTextScale(
    lyricFontScale: Float,
): Float {
    return lyricFontScale
}

internal fun resolvePreviewAnchorMainTextScale(
    lyricFontScale: Float,
): Float {
    return resolveLyricMainTextScale(lyricFontScale)
}

internal fun resolvePlayerStageToProgressGapDp(
    lyricMode: Boolean,
): Float {
    val fixedGap = 4f
    return if (lyricMode) fixedGap else fixedGap
}

internal fun resolvePlayerLyricToggleSlotHeightDp(): Float = 32f

internal fun resolvePlayerStatusSlotHeightDp(): Float = 22f

internal fun resolveLyricTranslationTextScale(
    lyricFontScale: Float,
): Float {
    return lyricFontScale * 0.9f
}

internal fun resolveLyricRomanizedTextScale(
    lyricFontScale: Float,
): Float {
    return lyricFontScale * 0.86f
}

internal data class CenteredLyricCandidate(
    val index: Int,
    val centerY: Float,
)

internal data class PreviewSeekResumeState(
    val previewLineIndex: Int,
    val lastAutoCenteredLineIndex: Int,
)

internal enum class LyricScrollMode {
    AutoFollow,
    ManualPreview,
}

internal enum class LyricTapTarget {
    Background,
    Line,
    PreviewAnchor,
}

internal enum class LyricTapAction {
    ExitToCover,
    SeekPreviewLine,
}

internal enum class LyricHighlightMode {
    Word,
    Sentence,
}

internal fun resolveLyricTapAction(
    mode: LyricScrollMode,
    tapTarget: LyricTapTarget,
): LyricTapAction {
    return when {
        mode == LyricScrollMode.ManualPreview && tapTarget == LyricTapTarget.PreviewAnchor -> LyricTapAction.SeekPreviewLine
        else -> LyricTapAction.ExitToCover
    }
}

internal fun resolvePreviewSeekResumeState(
    previewLineIndex: Int,
): PreviewSeekResumeState {
    return PreviewSeekResumeState(
        previewLineIndex = previewLineIndex,
        lastAutoCenteredLineIndex = previewLineIndex,
    )
}

internal fun shouldEnterManualPreview(
    isScrollInProgress: Boolean,
    autoScrolling: Boolean,
    userScrollActive: Boolean,
    ignoreAutoScrollSignals: Boolean,
    centeredIndex: Int?,
    currentLineIndex: Int,
): Boolean {
    return isScrollInProgress &&
        !autoScrolling &&
        userScrollActive &&
        !ignoreAutoScrollSignals &&
        centeredIndex != null &&
        centeredIndex != currentLineIndex
}

internal fun shouldAutoCenterLyricLine(
    mode: LyricScrollMode,
    currentLineIndex: Int,
    lastAutoCenteredLineIndex: Int,
): Boolean {
    return mode == LyricScrollMode.AutoFollow &&
        currentLineIndex >= 0 &&
        currentLineIndex != lastAutoCenteredLineIndex
}

internal fun shouldBlockAutoCenterForPendingPreviewSeek(
    pendingPreviewSeekTargetLineIndex: Int,
    currentLineIndex: Int,
): Boolean {
    return pendingPreviewSeekTargetLineIndex >= 0 &&
        currentLineIndex >= 0 &&
        pendingPreviewSeekTargetLineIndex != currentLineIndex
}

internal fun shouldUpdateManualPreviewAnchor(
    mode: LyricScrollMode,
    isScrollInProgress: Boolean,
    userScrollActive: Boolean,
    centeredIndex: Int?,
): Boolean {
    return mode == LyricScrollMode.ManualPreview &&
        isScrollInProgress &&
        userScrollActive &&
        centeredIndex != null
}

internal fun shouldRevealLyricListBeforeFirstCenter(
    initialAutoCenterSettled: Boolean,
    mode: LyricScrollMode,
): Boolean {
    return initialAutoCenterSettled || mode == LyricScrollMode.ManualPreview
}

internal data class CoverStageLayout(
    val discSizeDp: Float,
    val compositionSizeDp: Float,
    val stageTopOffsetDp: Float,
    val visualZoneHeightDp: Float,
    val visualTopInsetDp: Float,
    val infoBottomPaddingDp: Float,
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
    val verticalDiscLimit = maxHeightDp * 0.58f
    val discFloor = minOf(maxWidthDp * 0.88f, verticalDiscLimit, 384f)
    val discSize = minOf(
        maxWidthDp * 1.02f,
        verticalDiscLimit,
        500f,
    ).coerceAtLeast(discFloor).roundToInt().toFloat()
    val toneArmSlotWidth = (discSize * 0.54f).roundToInt().toFloat()
    val toneArmSlotHeight = (discSize * 0.48f).roundToInt().toFloat()
    val compositionSize = max(
        discSize + 10f,
        toneArmSlotWidth + 54f,
    ).roundToInt().toFloat()
    val stageTopOffset = (maxHeightDp * 0.012f).coerceIn(4f, 12f).roundToInt().toFloat()
    val visualTopInset = (maxHeightDp * 0.06f).coerceIn(20f, 72f).roundToInt().toFloat()
    val infoBottomPadding = (maxHeightDp * 0.02f).coerceIn(6f, 16f).roundToInt().toFloat()
    val visualZoneHeight = (compositionSize + visualTopInset).roundToInt().toFloat()
    return CoverStageLayout(
        discSizeDp = discSize,
        compositionSizeDp = compositionSize,
        stageTopOffsetDp = stageTopOffset,
        visualZoneHeightDp = visualZoneHeight,
        visualTopInsetDp = visualTopInset,
        infoBottomPaddingDp = infoBottomPadding,
        artworkSizeDp = (discSize * 0.69f).roundToInt().toFloat(),
        toneArmSlotWidthDp = toneArmSlotWidth,
        toneArmSlotHeightDp = toneArmSlotHeight,
        toneArmOffsetXDp = -(discSize * 0.024f).roundToInt().toFloat(),
        toneArmOffsetYDp = (discSize * 0.014f).roundToInt().toFloat(),
    )
}

internal fun resolvePlayerStageHeightDp(
    viewportHeightDp: Float,
    lyricMode: Boolean,
): Float {
    val reservedForControls = resolvePlayerControlsReserveDp(lyricMode) +
        resolvePlayerBottomSafeGapDp(viewportHeightDp)
    val minHeight = 360f
    return (viewportHeightDp - reservedForControls)
        .coerceAtLeast(minHeight.coerceAtMost(viewportHeightDp))
        .roundToInt()
        .toFloat()
}

internal fun resolvePlayerControlsReserveDp(
    lyricMode: Boolean,
): Float {
    val fixedReserve = 236f
    return if (lyricMode) fixedReserve else fixedReserve
}

internal fun resolvePlayerBottomSafeGapDp(
    viewportHeightDp: Float,
): Float {
    return if (viewportHeightDp > 0f) 0f else 0f
}

internal fun resolveLyricViewportLayout(
    viewportHeightDp: Float,
): LyricViewportLayout {
    return LyricViewportLayout(
        topPaddingDp = 0f,
        bottomPaddingDp = 0f,
        lineSpacingDp = 14f,
        horizontalPaddingDp = if (viewportHeightDp >= 560f) 6f else 4f,
        linePaddingDp = 2f,
        previewPaddingDp = 8f,
        edgeMinPaddingDp = 14f,
    )
}

internal fun resolveLyricContentWidthDp(
    viewportWidthDp: Float,
): Float {
    return minOf(680f, viewportWidthDp * 0.92f)
}

internal fun resolveLyricViewportHeightPx(
    viewportStart: Int,
    viewportEnd: Int,
    fallbackHeightPx: Int,
): Int {
    val measuredHeight = viewportEnd - viewportStart
    return if (measuredHeight > 0) measuredHeight else fallbackHeightPx
}

internal fun resolveLyricEdgeSpacerPx(
    viewportHeightPx: Int,
    lineHeightPx: Int,
    minimumPaddingPx: Int,
): Int {
    if (viewportHeightPx <= 0) return minimumPaddingPx
    val centeredPadding = ((viewportHeightPx - lineHeightPx) / 2f).roundToInt()
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
        val mainHeight = (58.dp * lyricFontScale).roundToPx()
        val translationHeight = if (showTranslation && line.translation.isNotBlank()) {
            (34.dp * lyricFontScale).roundToPx()
        } else {
            0
        }
        val romanizedHeight = if (showRomanized && line.romanized.isNotBlank()) {
            (30.dp * lyricFontScale).roundToPx()
        } else {
            0
        }
        val extraSpacing = when {
            translationHeight > 0 && romanizedHeight > 0 -> 10.dp.roundToPx()
            translationHeight > 0 || romanizedHeight > 0 -> 6.dp.roundToPx()
            else -> 2.dp.roundToPx()
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
    return calculateLyricCenterDelta(
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

internal fun resolveLyricLineProgress(
    line: LyricLineUi,
    positionMs: Long,
): Float {
    if (positionMs <= line.startTimeMs) return 0f
    if (line.endTimeMs <= line.startTimeMs) return if (positionMs > line.startTimeMs) 1f else 0f
    val durationMs = (line.endTimeMs - line.startTimeMs).toFloat()
    return ((positionMs - line.startTimeMs) / durationMs).coerceIn(0f, 1f)
}

internal fun usesWordLevelLyric(
    line: LyricLineUi,
): Boolean {
    return resolveLyricHighlightMode(line) == LyricHighlightMode.Word
}

internal fun resolveLyricHighlightMode(
    line: LyricLineUi,
): LyricHighlightMode {
    return if (hasDistinctWordTimeline(line)) {
        LyricHighlightMode.Word
    } else {
        LyricHighlightMode.Sentence
    }
}

internal fun hasDistinctWordTimeline(
    line: LyricLineUi,
): Boolean {
    val timedWords = line.words.filter { word ->
        word.text.any { char -> !char.isWhitespace() } &&
            word.endTimeMs > word.startTimeMs
    }
    if (timedWords.size <= 1) return false
    return timedWords
        .map { word -> word.startTimeMs to word.endTimeMs }
        .distinct()
        .size > 1
}

internal fun resolveLyricGlyphProgress(
    glyphIndex: Int,
    glyphCount: Int,
    wordProgress: Float,
): Float {
    if (glyphCount <= 0 || glyphIndex !in 0 until glyphCount) return 0f
    return (wordProgress.coerceIn(0f, 1f) * glyphCount - glyphIndex).coerceIn(0f, 1f)
}

@Suppress("UNUSED_PARAMETER")
internal fun buildLyricAnnotatedText(
    line: LyricLineUi,
    currentPositionMs: Long,
    wordHighlightEnabled: Boolean,
    emphasizedColor: Color,
    upcomingColor: Color,
): AnnotatedString {
    return AnnotatedString(line.mainText)
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
