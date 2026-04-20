package top.imsyy.splayer.nativeapp.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import top.imsyy.splayer.nativeapp.model.TrackItem
import top.imsyy.splayer.nativeapp.ui.AlbumDetailViewModel

@Composable
fun AlbumDetailScreen(
    onBack: () -> Unit,
    onPlayAll: (List<TrackItem>) -> Unit,
    onPlayTrack: (List<TrackItem>, Int) -> Unit,
    viewModel: AlbumDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val album = state.album

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
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
                    Text("专辑详情", style = MaterialTheme.typography.titleMedium)
                    album?.let {
                        Text(
                            text = listOf(it.artistName, "${it.trackCount} 首").filter { value -> value.isNotBlank() }.joinToString(" · "),
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

        state.errorMessage?.takeIf { it.isNotBlank() }?.let { error ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                    Button(onClick = viewModel::refresh) {
                        Text("重新加载")
                    }
                }
            }
        }

        album?.let { data ->
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
                    Text(
                        listOf(data.artistName, "${data.subscribedCount} 收藏", "${data.commentCount} 评论")
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(
                        onClick = { onPlayAll(data.tracks) },
                        enabled = data.tracks.isNotEmpty(),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Text("播放全部")
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

            items(data.tracks, key = { it.id }) { track ->
                val queueIndex = data.tracks.indexOfFirst { it.id == track.id }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlayTrack(data.tracks, queueIndex) },
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(track.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text(
                            text = listOf(track.artists, track.album).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
