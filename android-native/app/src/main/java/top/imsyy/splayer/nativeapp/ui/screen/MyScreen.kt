package top.imsyy.splayer.nativeapp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import top.imsyy.splayer.nativeapp.model.AlbumItem
import top.imsyy.splayer.nativeapp.model.ListeningRankItem
import top.imsyy.splayer.nativeapp.model.MyMusicPanelTab
import top.imsyy.splayer.nativeapp.model.PlaylistItem
import top.imsyy.splayer.nativeapp.model.TrackItem
import top.imsyy.splayer.nativeapp.ui.MyViewModel
import top.imsyy.splayer.nativeapp.ui.components.ErrorState
import top.imsyy.splayer.nativeapp.ui.components.LoadingState

@Composable
fun MyScreen(
    onOpenPlaylist: (Long) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onPlayTrack: (TrackItem) -> Unit,
    viewModel: MyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            MyHeroHeader(
                nickname = state.panel.header.nickname,
                avatarUrl = state.panel.header.avatarUrl,
                backgroundUrl = state.panel.header.backgroundUrl,
                signature = state.panel.header.signature.ifBlank { "把喜欢的音乐都收进这里" },
                likedSongCount = state.panel.likedSongCount,
                createdCount = state.panel.createdPlaylists.size,
                level = state.panel.header.level,
                listenCount = state.panel.header.listenCount,
            )
        }

        if (state.loading) {
            item { LoadingState() }
        }

        state.errorMessage?.takeIf { it.isNotBlank() }?.let { error ->
            item { ErrorState(error) }
        }

        item {
            ScrollableTabRow(
                selectedTabIndex = state.selectedTab.ordinal,
                edgePadding = 0.dp,
                divider = {},
            ) {
                MyMusicPanelTab.entries.forEach { tab ->
                    Tab(
                        selected = state.selectedTab == tab,
                        onClick = { viewModel.setTab(tab) },
                        text = {
                            Text(
                                text = tab.label,
                                fontWeight = if (state.selectedTab == tab) FontWeight.Bold else FontWeight.Medium,
                            )
                        },
                    )
                }
            }
        }

        state.panel.likedPlaylist?.let { playlist ->
            item {
                PinnedPlaylistCard(
                    playlist = playlist,
                    likedSongCount = state.panel.likedSongCount,
                    onClick = { onOpenPlaylist(playlist.id) },
                )
            }
        }

        when (state.selectedTab) {
            MyMusicPanelTab.Recent -> {
                if (state.panel.albums.isNotEmpty()) {
                    item { AssetSectionTitle("专辑") }
                    items(state.panel.albums, key = { album -> "recent-album-${album.id}" }) { album ->
                        AlbumAssetCard(
                            album = album,
                            onClick = { onOpenAlbum(album.id) },
                        )
                    }
                }
                if (state.panel.recentPlaylists.isNotEmpty()) {
                    item { AssetSectionTitle("歌单") }
                    items(state.panel.recentPlaylists, key = { playlist -> "recent-playlist-${playlist.id}" }) { playlist ->
                        AssetRowCard(
                            title = playlist.name,
                            subtitle = playlist.trackCount.takeIf { it > 0 }?.let { "${it} 首" } ?: "音乐歌单",
                            coverUrl = playlist.coverUrl,
                            icon = {
                                Icon(
                                    if (playlist.id == state.panel.likedPlaylist?.id) Icons.Rounded.Favorite else Icons.Rounded.LibraryMusic,
                                    contentDescription = null,
                                )
                            },
                            onClick = { onOpenPlaylist(playlist.id) },
                        )
                    }
                }
                if (state.panel.listeningRanks.isNotEmpty()) {
                    item { AssetSectionTitle("听歌排行") }
                    itemsIndexed(
                        state.panel.listeningRanks,
                        key = { _, rank -> "listening-rank-${rank.track.id}" },
                    ) { index, rank ->
                        ListeningRankCard(
                            rank = rank,
                            position = index + 1,
                            onClick = { onPlayTrack(rank.track) },
                        )
                    }
                }
            }

            MyMusicPanelTab.Created -> {
                items(state.panel.createdPlaylists, key = { it.id }) { playlist ->
                    AssetRowCard(
                        title = playlist.name,
                        subtitle = "${playlist.trackCount} 首",
                        coverUrl = playlist.coverUrl,
                        icon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = null) },
                        onClick = { onOpenPlaylist(playlist.id) },
                    )
                }
            }

            MyMusicPanelTab.Collected -> {
                items(state.panel.collectedPlaylists, key = { it.id }) { playlist ->
                    AssetRowCard(
                        title = playlist.name,
                        subtitle = "${playlist.trackCount} 首",
                        coverUrl = playlist.coverUrl,
                        icon = { Icon(Icons.Rounded.Star, contentDescription = null) },
                        onClick = { onOpenPlaylist(playlist.id) },
                    )
                }
            }

            MyMusicPanelTab.Album -> {
                items(state.panel.albums, key = { it.id }) { album ->
                    AlbumAssetCard(
                        album = album,
                        onClick = { onOpenAlbum(album.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AssetSectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun MyHeroHeader(
    nickname: String,
    avatarUrl: String,
    backgroundUrl: String,
    signature: String,
    likedSongCount: Int,
    createdCount: Int,
    level: Int,
    listenCount: Int,
) {
    val context = LocalContext.current
    val heroBackgroundUrl = resolveMyHeroBackgroundImageUrl(
        avatarUrl = avatarUrl,
        backgroundUrl = backgroundUrl,
    )
    val backgroundModel = rememberProfileImageRequest(
        context = context,
        imageUrl = heroBackgroundUrl,
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(292.dp)
            .clip(RoundedCornerShape(34.dp)),
    ) {
        if (heroBackgroundUrl.isNotBlank()) {
            AsyncImage(
                model = backgroundModel,
                contentDescription = nickname,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.10f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        ),
                    ),
                ),
        )
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(22.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = nickname,
                modifier = Modifier
                    .size(92.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Text(
                text = nickname,
                modifier = Modifier.padding(top = 14.dp),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = signature,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HeroMetric(text = "喜欢 $likedSongCount")
                HeroMetric(text = "创建 $createdCount")
                HeroMetric(text = "Lv.$level")
                if (listenCount > 0) {
                    HeroMetric(text = "听歌 $listenCount")
                }
            }
        }
    }
}

@Composable
private fun rememberProfileImageRequest(
    context: android.content.Context,
    imageUrl: String,
): ImageRequest {
    return androidx.compose.runtime.remember(context, imageUrl) {
        val identity = resolveProfileBackgroundImageRequestIdentity(
            imageUrl = imageUrl,
        )
        ImageRequest.Builder(context)
            .data(identity.imageUrl)
            .memoryCacheKey(identity.memoryCacheKey)
            .diskCacheKey(identity.diskCacheKey)
            .crossfade(true)
            .build()
    }
}

internal data class ProfileBackgroundImageRequestIdentity(
    val imageUrl: String,
    val memoryCacheKey: String,
    val diskCacheKey: String,
)

internal fun resolveProfileBackgroundImageRequestIdentity(
    imageUrl: String,
): ProfileBackgroundImageRequestIdentity {
    val normalizedUrl = imageUrl.trim()
    val key = "my-profile-bg:$normalizedUrl"
    return ProfileBackgroundImageRequestIdentity(
        imageUrl = normalizedUrl,
        memoryCacheKey = key,
        diskCacheKey = key,
    )
}

internal fun resolveMyHeroBackgroundImageUrl(
    avatarUrl: String,
    backgroundUrl: String,
): String {
    return avatarUrl.trim()
}

@Composable
private fun HeroMetric(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.34f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun PinnedPlaylistCard(
    playlist: PlaylistItem,
    likedSongCount: Int,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AsyncImage(
                model = playlist.coverUrl,
                contentDescription = playlist.name,
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(22.dp)),
                contentScale = ContentScale.Crop,
            )
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "$likedSongCount 首 · 你的默认喜欢歌单入口",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AssetRowCard(
    title: String,
    subtitle: String,
    coverUrl: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.76f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = coverUrl,
            contentDescription = title,
            modifier = Modifier
                .size(64.dp)
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
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
    }
}

@Composable
private fun AlbumAssetCard(
    album: AlbumItem,
    onClick: () -> Unit,
) {
    AssetRowCard(
        title = album.name,
        subtitle = listOf(album.artistName, album.trackCount.takeIf { it > 0 }?.let { "${it} 首" }.orEmpty())
            .filter { it.isNotBlank() }
            .joinToString(" · "),
        coverUrl = album.coverUrl,
        icon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = null) },
        onClick = onClick,
    )
}

@Composable
private fun ListeningRankCard(
    rank: ListeningRankItem,
    position: Int,
    onClick: () -> Unit,
) {
    AssetRowCard(
        title = rank.track.name,
        subtitle = "第 ${position} 名 · 播放 ${rank.playCount} 次",
        coverUrl = rank.track.coverUrl,
        icon = { Icon(Icons.Rounded.QueryStats, contentDescription = null) },
        onClick = onClick,
    )
}
