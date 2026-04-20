package top.imsyy.splayer.nativeapp.ui.screen

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import top.imsyy.splayer.nativeapp.model.ThemeMode
import top.imsyy.splayer.nativeapp.ui.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("设置", style = MaterialTheme.typography.headlineMedium)
        }

        item {
            SettingsSectionCard(
                title = "账号",
                description = "Native V2 首阶段继续复用远程 /splayer/* 登录链路",
            ) {
                val qrBitmap = remember(state.qrImageUrl) {
                    extractQrBase64Payload(state.qrImageUrl)?.let { payload ->
                        runCatching {
                            val bytes = Base64.decode(payload, Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        }.getOrNull()
                    }
                }
                state.currentUser?.let { user ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (user.avatarUrl.isNotBlank()) {
                            AsyncImage(
                                model = user.avatarUrl,
                                contentDescription = "账号头像",
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        Text(user.nickname)
                    }
                } ?: Text("未登录")
                Button(onClick = viewModel::startQrLogin) {
                    Text(if (state.currentUser == null) "二维码登录" else "重新登录")
                }
                if (state.qrVisible) {
                    if (state.qrLoading) {
                        CircularProgressIndicator()
                    } else {
                        when {
                            qrBitmap != null -> {
                                Image(
                                    bitmap = qrBitmap,
                                    contentDescription = "登录二维码",
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            !state.qrImageUrl.isNullOrBlank() -> {
                                AsyncImage(
                                    model = state.qrImageUrl,
                                    contentDescription = "登录二维码",
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    Text(state.qrStatusText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            SettingsSectionCard(
                title = "播放",
                description = "播放模式记忆、自动播放、队列徽标都走原生 DataStore 持久化",
            ) {
                SettingsSwitchRow(
                    title = "进入详情后自动播放",
                    checked = state.autoPlay,
                    onCheckedChange = viewModel::setAutoPlay,
                )
                SettingsSwitchRow(
                    title = "小播放条显示队列数量",
                    checked = state.showQueueCount,
                    onCheckedChange = viewModel::setShowQueueCount,
                )
                Button(onClick = viewModel::clearQueue, modifier = Modifier.fillMaxWidth()) {
                    Text("清空播放队列")
                }
            }
        }

        item {
            SettingsSectionCard(
                title = "歌词",
                description = "默认不显示音译，歌词字号和翻译显示都可直接调节",
            ) {
                SettingsSwitchRow(
                    title = "显示翻译",
                    checked = state.showTranslation,
                    onCheckedChange = viewModel::setShowTranslation,
                )
                SettingsSwitchRow(
                    title = "显示音译",
                    checked = state.showRomanized,
                    onCheckedChange = viewModel::setShowRomanized,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("歌词字号 ${state.lyricFontScale}%")
                    Slider(
                        value = state.lyricFontScale.toFloat(),
                        onValueChange = { viewModel.setLyricFontScale(it.toInt()) },
                        valueRange = 85f..135f,
                    )
                }
            }
        }

        item {
            SettingsSectionCard(
                title = "网络",
                description = "首阶段固定 remote 模式，和你的 /splayer/* 服务直接对接",
            ) {
                Text("模式：${state.appMode}")
                Text(state.apiRoot, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            SettingsSectionCard(
                title = "外观",
                description = "默认暗色，保留沉浸式听歌氛围，并允许手动切换明暗主题",
            ) {
                SettingsSwitchRow(
                    title = "暗色主题",
                    checked = state.themeMode == ThemeMode.DARK,
                    onCheckedChange = { enabled ->
                        viewModel.setThemeMode(if (enabled) ThemeMode.DARK else ThemeMode.LIGHT)
                    },
                )
                Text(
                    if (state.themeMode == ThemeMode.DARK) "当前使用暗色主题" else "当前使用亮色主题",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "当前版本默认关闭高成本背景模糊与持续 Canvas 动画，优先保证播放稳定和能效。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SettingsSectionCard(
                title = "缓存 / 本地资源",
                description = "用于清理最近播放和失败音源记忆，避免排障时历史状态干扰",
            ) {
                Button(onClick = viewModel::clearRecentTracks, modifier = Modifier.fillMaxWidth()) {
                    Text("清空最近播放")
                }
                Button(onClick = viewModel::clearFailedSourceMemory, modifier = Modifier.fillMaxWidth()) {
                    Text("清空失败音源记忆")
                }
            }
        }

        item {
            SettingsSectionCard(
                title = "关于 / 诊断",
                description = "性能验收将按 batterystats、gfxinfo、cpuinfo 与 Perfetto 留痕",
            ) {
                Text("包模式：${state.appMode}")
                Text("API 根路径：${state.apiRoot}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "桌面专属项不在 Android 显示。后续会补真机功耗 trace 导出入口。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

internal fun extractQrBase64Payload(value: String?): String? {
    if (value.isNullOrBlank()) return null
    val prefix = "data:image/"
    if (!value.startsWith(prefix)) return null
    val marker = "base64,"
    val index = value.indexOf(marker)
    if (index < 0) return null
    return value.substring(index + marker.length).takeIf { it.isNotBlank() }
}
