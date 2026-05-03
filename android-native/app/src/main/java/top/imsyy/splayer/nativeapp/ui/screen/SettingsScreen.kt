package top.imsyy.splayer.nativeapp.ui.screen

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import top.imsyy.splayer.nativeapp.model.ThemeMode
import top.imsyy.splayer.nativeapp.model.UnlockServerMode
import top.imsyy.splayer.nativeapp.ui.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var apiRootInput by rememberSaveable { mutableStateOf(state.apiRoot) }
    val qrImageSize = resolveSettingsQrImageSizeDp(LocalConfiguration.current.screenWidthDp).dp
    LaunchedEffect(state.apiRoot) {
        apiRootInput = state.apiRoot
    }
    DisposableEffect(viewModel) {
        onDispose {
            viewModel.stopQrLogin()
        }
    }

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
                description = "登录沿用 API Root 下的 /netease/* 二维码链路；本地原生能力只用于 unlock",
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
                if (state.currentUser != null) {
                    Button(
                        onClick = viewModel::logout,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("退出登录")
                    }
                }
                if (state.qrVisible) {
                    if (state.qrLoading) {
                        CircularProgressIndicator()
                    } else {
                        when {
                            qrBitmap != null -> {
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Image(
                                        bitmap = qrBitmap,
                                        contentDescription = "登录二维码",
                                        modifier = Modifier.size(qrImageSize),
                                    )
                                }
                            }
                            !state.qrImageUrl.isNullOrBlank() -> {
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    AsyncImage(
                                        model = state.qrImageUrl,
                                        contentDescription = "登录二维码",
                                        modifier = Modifier.size(qrImageSize),
                                    )
                                }
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
                SettingsSwitchRow(
                    title = "允许与其他应用同时播放",
                    checked = state.allowConcurrentPlayback,
                    onCheckedChange = viewModel::setAllowConcurrentPlayback,
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
                description = "默认使用原生本地解锁；选择外部服务器时只走 API 根路径提供的解锁服务",
            ) {
                Text("模式：${state.appMode}")
                OutlinedTextField(
                    value = apiRootInput,
                    onValueChange = { apiRootInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API 根路径") },
                    placeholder = { Text("https://your-domain.com/splayer") },
                    singleLine = true,
                    isError = state.apiRootError != null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                Text(
                    "外部服务器请填写完整根路径。端口按你的服务实际监听端口填写，应用不会自动补端口。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.apiRootError?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error)
                }
                state.apiRootSavedMessage?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.primary)
                }
                Button(
                    onClick = { viewModel.setApiRoot(apiRootInput) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("保存 API 根路径")
                }
                SettingsSwitchRow(
                    title = "使用外部 unlock 服务器",
                    checked = state.unlockServerMode == UnlockServerMode.EXTERNAL,
                    onCheckedChange = { enabled ->
                        viewModel.setUnlockServerMode(
                            if (enabled) UnlockServerMode.EXTERNAL else UnlockServerMode.LOCAL,
                        )
                    },
                )
                Text(
                    if (state.unlockServerMode == UnlockServerMode.EXTERNAL) {
                        "当前播放源解析会先请求 API 根路径下的 /unblock/*；netease 无结果时回退到原生直连解锁。"
                    } else {
                        "当前播放源解析使用原生本地解锁，不加载桌面本地 unlock 服务。登录和推荐、发现、歌单、搜索仍需要 API 根路径。"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                Text(
                    "API 根路径：${state.apiRoot.ifBlank { "未配置" }}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Unlock：${if (state.unlockServerMode == UnlockServerMode.EXTERNAL) "外部服务器" else "原生本地"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

internal fun resolveSettingsQrImageSizeDp(screenWidthDp: Int): Int {
    val approximateCardContentWidth = screenWidthDp - 64
    return approximateCardContentWidth.coerceIn(196, 280)
}
