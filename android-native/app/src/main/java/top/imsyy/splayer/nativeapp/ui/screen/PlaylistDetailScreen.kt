package top.imsyy.splayer.nativeapp.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import top.imsyy.splayer.nativeapp.model.TrackItem
import top.imsyy.splayer.nativeapp.ui.PlaylistDetailViewModel
import top.imsyy.splayer.nativeapp.ui.resolvePlaylistCurrentTrackLazyIndex
import top.imsyy.splayer.nativeapp.ui.resolvePlaylistPlaybackRequest

@Composable
fun PlaylistDetailScreen(
    onBack: () -> Unit,
    onPlayAll: (List<TrackItem>) -> Unit,
    onPlayTrack: (List<TrackItem>, Int) -> Unit,
    onOpenPlayer: () -> Unit,
    currentTrackId: Long? = null,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playlist = state.playlist
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val shouldLoadMore by remember(state.hasMore, state.isAppending, playlist?.tracks?.size) {
        derivedStateOf {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            val totalItems = listState.layoutInfo.totalItemsCount
            state.hasMore && !state.isAppending && totalItems > 0 && lastVisibleIndex >= totalItems - 4
        }
    }
    val playlistLeadingItemCount = remember(state.loading, state.errorMessage, playlist) {
        1 +
            (if (state.loading) 1 else 0) +
            (if (!state.errorMessage.isNullOrBlank()) 1 else 0) +
            (if (playlist != null) 1 else 0)
    }
    val currentTrackLazyIndex = remember(currentTrackId, playlist?.tracks, playlistLeadingItemCount) {
        resolvePlaylistCurrentTrackLazyIndex(
            currentTrackId = currentTrackId,
            loadedTracks = playlist?.tracks.orEmpty(),
            leadingItemCount = playlistLeadingItemCount,
        )
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadMore()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("歌单详情", style = MaterialTheme.typography.titleMedium)
                        playlist?.let {
                            Text(
                                text = "${it.trackCount} 首 · ${it.subscribedCount} 收藏",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (state.loading) {
                item { CircularProgressIndicator() }
            }

            state.errorMessage?.takeIf { it.isNotBlank() }?.let {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("歌单加载失败，请重试", color = MaterialTheme.colorScheme.error)
                        Button(onClick = viewModel::refresh) {
                            Text("重新加载")
                        }
                    }
                }
            }

            playlist?.let { data ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AsyncImage(
                            model = data.coverUrl,
                            contentDescription = data.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp),
                        )
                        Text(data.name, style = MaterialTheme.typography.headlineSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FilledTonalButton(
                                onClick = {
                                    if (data.tracks.isNotEmpty()) {
                                        onPlayAll(data.tracks)
                                    }
                                },
                                enabled = data.tracks.isNotEmpty(),
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Text("播放全部")
                            }
                            Text(
                                text = "已加载 ${data.tracks.size}/${data.trackCount}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                        if (state.primingFirstPage) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp))
                                Text(
                                    text = "首屏歌曲继续补齐中",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (data.description.isNotBlank()) {
                            Text(
                                text = data.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text("歌曲列表", style = MaterialTheme.typography.titleLarge)
                    }
                }

                itemsIndexed(data.tracks, key = { _, track -> track.id }) { index, track ->
                    val isCurrentTrack = currentTrackId == track.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val request = resolvePlaylistPlaybackRequest(
                                    clickedTrackId = track.id,
                                    loadedTracks = data.tracks,
                                    clickedIndex = index,
                                    currentTrackId = currentTrackId,
                                )
                                if (!request.shouldStartPlayback) {
                                    onOpenPlayer()
                                } else if (request.tracks.isNotEmpty()) {
                                    onPlayTrack(request.tracks, request.startIndex)
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrentTrack) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        ),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                track.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isCurrentTrack) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                            )
                            Text(
                                text = "${track.artists} · ${track.album}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isCurrentTrack) {
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            if (state.isAppending) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            state.appendErrorMessage?.takeIf { it.isNotBlank() }?.let {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "更多歌曲加载失败，请重试",
                            color = MaterialTheme.colorScheme.error,
                        )
                        Button(onClick = viewModel::loadMore) {
                            Text("继续加载")
                        }
                    }
                }
            }
        }

        currentTrackLazyIndex?.let { targetIndex ->
            SmallFloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        listState.animateScrollToItem(targetIndex)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 20.dp),
            ) {
                Icon(Icons.Rounded.MyLocation, contentDescription = "定位当前播放")
            }
        }
    }
}
