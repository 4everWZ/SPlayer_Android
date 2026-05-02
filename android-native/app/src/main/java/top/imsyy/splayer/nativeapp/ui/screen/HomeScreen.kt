package top.imsyy.splayer.nativeapp.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import top.imsyy.splayer.nativeapp.model.AlbumItem
import top.imsyy.splayer.nativeapp.model.PlaylistItem
import top.imsyy.splayer.nativeapp.model.RecommendChannel
import top.imsyy.splayer.nativeapp.model.RecommendHeroCardUi
import top.imsyy.splayer.nativeapp.model.TrackItem
import top.imsyy.splayer.nativeapp.ui.HomeViewModel
import top.imsyy.splayer.nativeapp.ui.components.AlbumRail
import top.imsyy.splayer.nativeapp.ui.components.ErrorState
import top.imsyy.splayer.nativeapp.ui.components.LoadingState
import top.imsyy.splayer.nativeapp.ui.components.PlaylistRail
import top.imsyy.splayer.nativeapp.ui.components.SearchEntry
import top.imsyy.splayer.nativeapp.ui.components.SectionHeader

@Composable
fun HomeScreen(
    onOpenMenu: () -> Unit,
    onOpenSearch: () -> Unit,
    onPlayTrack: (TrackItem) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenMy: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh(showLoading = false)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                IconButton(onClick = onOpenMenu) {
                    Icon(Icons.Rounded.Menu, contentDescription = "打开菜单")
                }
                SearchEntry(
                    hint = state.searchHint,
                    onClick = onOpenSearch,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onOpenSearch) {
                    Icon(Icons.Rounded.Search, contentDescription = "打开搜索")
                }
                IconButton(
                    onClick = { viewModel.refresh(forceRefresh = true, showLoading = false) },
                    enabled = !state.refreshing,
                ) {
                    if (state.refreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    } else {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新推荐")
                    }
                }
            }
        }

        item {
            ScrollableTabRow(
                selectedTabIndex = state.selectedChannel.ordinal,
                edgePadding = 0.dp,
                divider = {},
            ) {
                RecommendChannel.entries.forEach { channel ->
                    Tab(
                        selected = state.selectedChannel == channel,
                        onClick = { viewModel.setChannel(channel) },
                        text = {
                            Text(
                                text = channel.label,
                                fontWeight = if (state.selectedChannel == channel) FontWeight.Bold else FontWeight.Medium,
                            )
                        },
                    )
                }
            }
        }

        if (state.loading) {
            item { LoadingState() }
        }

        state.errorMessage?.takeIf { it.isNotBlank() }?.let { error ->
            item { ErrorState(error) }
        }

        when (state.selectedChannel) {
            RecommendChannel.Recommend -> {
                if (state.feed.heroCards.isNotEmpty()) {
                    item {
                        HeroCardRail(
                            items = state.feed.heroCards,
                            onOpenPlaylist = onOpenPlaylist,
                        )
                    }
                }
                if (state.feed.guessTracks.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = state.currentUser?.nickname?.takeIf { it.isNotBlank() }?.let { "继续听你会喜欢的歌" } ?: "猜你喜欢的好歌",
                            actionLabel = if (state.currentUser == null) "去登录" else null,
                            onAction = if (state.currentUser == null) onOpenMy else null,
                        )
                    }
                    items(state.feed.guessTracks, key = { it.id }) { track ->
                        GuessTrackRow(
                            track = track,
                            onClick = { onPlayTrack(track) },
                        )
                    }
                }
                if (state.feed.recommendedPlaylists.isNotEmpty()) {
                    item { SectionHeader(title = "推荐歌单") }
                    item {
                        PlaylistRail(
                            items = state.feed.recommendedPlaylists,
                            onClick = { playlist -> onOpenPlaylist(playlist.id) },
                        )
                    }
                }
                if (state.feed.newAlbums.isNotEmpty()) {
                    item { SectionHeader(title = "新碟 / 热门专辑") }
                    item {
                        AlbumRail(
                            items = state.feed.newAlbums,
                            onClick = { album -> onOpenAlbum(album.id) },
                        )
                    }
                }
                if (state.feed.topPlaylists.isNotEmpty()) {
                    item { SectionHeader(title = "排行榜") }
                    items(state.feed.topPlaylists, key = { it.id }) { playlist ->
                        MusicCoverRow(
                            title = playlist.name,
                            subtitle = "${playlist.trackCount} 首",
                            coverUrl = playlist.coverUrl,
                            onClick = { onOpenPlaylist(playlist.id) },
                        )
                    }
                }
            }

            RecommendChannel.NewSong -> {
                item { SectionHeader(title = "新歌速递") }
                items(state.feed.newSongs, key = { it.id }) { track ->
                    GuessTrackRow(track = track, onClick = { onPlayTrack(track) })
                }
            }

            RecommendChannel.Playlist -> {
                if (state.feed.heroCards.isNotEmpty()) {
                    item {
                        HeroCardRail(
                            items = state.feed.heroCards,
                            onOpenPlaylist = onOpenPlaylist,
                        )
                    }
                }
                item { SectionHeader(title = "精选歌单") }
                item {
                    PlaylistRail(
                        items = state.feed.recommendedPlaylists.ifEmpty { state.feed.topPlaylists },
                        onClick = { playlist -> onOpenPlaylist(playlist.id) },
                    )
                }
                if (state.feed.topPlaylists.isNotEmpty()) {
                    item { SectionHeader(title = "榜单歌单") }
                    item {
                        PlaylistRail(
                            items = state.feed.topPlaylists,
                            onClick = { playlist -> onOpenPlaylist(playlist.id) },
                        )
                    }
                }
            }

            RecommendChannel.Album -> {
                item { SectionHeader(title = "新碟上架") }
                item {
                    AlbumRail(
                        items = state.feed.newAlbums,
                        onClick = { album -> onOpenAlbum(album.id) },
                    )
                }
                items(state.feed.newAlbums, key = { it.id }) { album ->
                    AlbumCoverRow(
                        album = album,
                        onClick = { onOpenAlbum(album.id) },
                    )
                }
            }

            RecommendChannel.Toplist -> {
                item { SectionHeader(title = "排行榜") }
                items(state.feed.topPlaylists, key = { it.id }) { playlist ->
                    MusicCoverRow(
                        title = playlist.name,
                        subtitle = "${playlist.trackCount} 首",
                        coverUrl = playlist.coverUrl,
                        onClick = { onOpenPlaylist(playlist.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroCardRail(
    items: List<RecommendHeroCardUi>,
    onOpenPlaylist: (Long) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(items, key = { it.playlistId }) { item ->
            Card(
                modifier = Modifier
                    .size(width = 274.dp, height = 306.dp)
                    .clickable { onOpenPlaylist(item.playlistId) },
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = item.coverUrl,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.04f),
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                                    ),
                                ),
                            ),
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (item.badge.isNotBlank()) {
                            Text(
                                text = item.badge,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.88f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = item.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuessTrackRow(
    track: TrackItem,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsyncImage(
                model = track.coverUrl,
                contentDescription = track.name,
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(18.dp)),
                contentScale = ContentScale.Crop,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOf(track.artists, track.album).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "播放",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun MusicCoverRow(
    title: String,
    subtitle: String,
    coverUrl: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
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
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AlbumCoverRow(
    album: AlbumItem,
    onClick: () -> Unit,
) {
    MusicCoverRow(
        title = album.name,
        subtitle = listOf(album.artistName, album.trackCount.takeIf { it > 0 }?.let { "${it} 首" }.orEmpty())
            .filter { it.isNotBlank() }
            .joinToString(" · "),
        coverUrl = album.coverUrl,
        onClick = onClick,
    )
}
