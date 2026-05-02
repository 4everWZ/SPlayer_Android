package top.imsyy.splayer.nativeapp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import top.imsyy.splayer.nativeapp.model.TrackItem
import top.imsyy.splayer.nativeapp.ui.DiscoveryViewModel
import top.imsyy.splayer.nativeapp.ui.components.AlbumRail
import top.imsyy.splayer.nativeapp.ui.components.ErrorState
import top.imsyy.splayer.nativeapp.ui.components.LoadingState
import top.imsyy.splayer.nativeapp.ui.components.PlaylistRail
import top.imsyy.splayer.nativeapp.ui.components.SectionHeader

@Composable
fun DiscoveryScreen(
    onOpenSearch: () -> Unit,
    onOpenSearchKeyword: (String) -> Unit,
    onPlayTrack: (TrackItem) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    viewModel: DiscoveryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "发现音乐",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "搜歌",
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(onClick = onOpenSearch)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    IconButton(
                        onClick = { viewModel.refresh(forceRefresh = true, showLoading = false) },
                        enabled = !state.refreshing,
                    ) {
                        if (state.refreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp))
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = "刷新发现")
                        }
                    }
                }
            }
        }

        if (state.content.quickEntries.isNotEmpty()) {
            item { SectionHeader(title = "分类入口") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.content.quickEntries, key = { it.label }) { entry ->
                        AssistChip(
                            onClick = { onOpenSearchKeyword(entry.keyword) },
                            label = { Text(entry.label) },
                        )
                    }
                }
            }
        }

        if (state.loading) {
            item { LoadingState() }
        }

        state.errorMessage?.takeIf { it.isNotBlank() }?.let { error ->
            item { ErrorState(error) }
        }

        if (state.content.featuredPlaylists.isNotEmpty()) {
            item { SectionHeader("分类歌单") }
            item {
                PlaylistRail(
                    items = state.content.featuredPlaylists,
                    onClick = { playlist -> onOpenPlaylist(playlist.id) },
                )
            }
        }

        if (state.content.topPlaylists.isNotEmpty()) {
            item { SectionHeader("榜单聚合") }
            items(state.content.topPlaylists, key = { it.id }) { playlist ->
                DiscoveryRowCard(
                    title = playlist.name,
                    subtitle = "${playlist.trackCount} 首",
                    coverUrl = playlist.coverUrl,
                    onClick = { onOpenPlaylist(playlist.id) },
                )
            }
        }

        if (state.content.newSongs.isNotEmpty()) {
            item { SectionHeader("新歌 / 新碟") }
            items(state.content.newSongs, key = { it.id }) { track ->
                DiscoveryTrackRow(track = track, onClick = { onPlayTrack(track) })
            }
        }

        if (state.content.newAlbums.isNotEmpty()) {
            item { SectionHeader("热门专辑") }
            item {
                AlbumRail(
                    items = state.content.newAlbums,
                    onClick = { album -> onOpenAlbum(album.id) },
                )
            }
        }

        if (state.content.topArtists.isNotEmpty()) {
            item { SectionHeader("热门歌手") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.content.topArtists, key = { it.id }) { artist ->
                        Card(
                            modifier = Modifier.size(width = 144.dp, height = 196.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
                            ),
                            shape = RoundedCornerShape(24.dp),
                        ) {
                            androidx.compose.foundation.layout.Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                AsyncImage(
                                    model = artist.coverUrl,
                                    contentDescription = artist.name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .size(120.dp)
                                        .clip(RoundedCornerShape(18.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                                Text(
                                    text = artist.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${artist.musicSize} 首作品",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveryTrackRow(
    track: TrackItem,
    onClick: () -> Unit,
) {
    DiscoveryRowCard(
        title = track.name,
        subtitle = listOf(track.artists, track.album).filter { it.isNotBlank() }.joinToString(" · "),
        coverUrl = track.coverUrl,
        trailingPlay = true,
        onClick = onClick,
    )
}

@Composable
private fun DiscoveryRowCard(
    title: String,
    subtitle: String,
    coverUrl: String,
    trailingPlay: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = coverUrl,
            contentDescription = title,
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(18.dp)),
            contentScale = ContentScale.Crop,
        )
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailingPlay) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowOutward,
                    contentDescription = "播放",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
