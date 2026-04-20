package top.imsyy.splayer.nativeapp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.imsyy.splayer.nativeapp.ui.PodcastViewModel
import top.imsyy.splayer.nativeapp.ui.components.ErrorState
import top.imsyy.splayer.nativeapp.ui.components.LoadingState
import top.imsyy.splayer.nativeapp.ui.components.RadioRail
import top.imsyy.splayer.nativeapp.ui.components.SearchEntry
import top.imsyy.splayer.nativeapp.ui.components.SectionHeader

@Composable
fun PodcastScreen(
    onOpenSearch: () -> Unit,
    onOpenRadio: (Long) -> Unit,
    viewModel: PodcastViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            SearchEntry(
                hint = "搜索播客、电台和节目",
                onClick = onOpenSearch,
            )
        }

        if (state.loading) {
            item { LoadingState() }
        }

        state.errorMessage?.takeIf { it.isNotBlank() }?.let { error ->
            item { ErrorState(error) }
        }

        if (state.content.recommendedRadios.isNotEmpty()) {
            item { SectionHeader("推荐电台") }
            item {
                RadioRail(
                    items = state.content.recommendedRadios,
                    onClick = { radio -> onOpenRadio(radio.id) },
                )
            }
        }

        if (state.content.hotRadios.isNotEmpty()) {
            item { SectionHeader("热门播客") }
            item {
                RadioRail(
                    items = state.content.hotRadios,
                    onClick = { radio -> onOpenRadio(radio.id) },
                )
            }
        }

        state.content.categories.forEach { category ->
            if (category.radios.isNotEmpty()) {
                item { SectionHeader(category.name) }
                item {
                    RadioRail(
                        items = category.radios,
                        onClick = { radio -> onOpenRadio(radio.id) },
                    )
                }
            }
        }
    }
}
