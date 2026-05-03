# Android Native V2 架构

## 工程边界

- 原生工程目录：`android-native/`
- 单 Activity：`MainActivity`
- 后台播放服务：`SPlayerPlaybackService`
- 当前包名：
  - debug：`top.imsyy.splayer.native`
  - release：`top.imsyy.splayer`

## 分层

### UI

- `ui/`
- Compose 导航承接：首页、搜索、播放页、设置页
- 小播放条位于全局 Scaffold bottom bar

### 状态

- `ViewModels.kt`
- `HomeViewModel`
- `SearchViewModel`
- `PlayerViewModel`
- `SettingsViewModel`
- `AppChromeViewModel`

### 数据

- 远程接口：`data/api/SPlayerApiService.kt`
- 远程仓库：`data/repository/SPlayerRemoteRepository.kt`
- 队列仓库：`data/repository/QueueRepository.kt`
- 歌词偏好：`data/repository/LyricsPreferences.kt`

### 持久化

- `DataStore`：`AppSettingsStore`
- `Room`：`SPlayerDatabase`
- 持久化内容：
  - 登录 Cookie
  - 播放模式
  - 歌词偏好
  - 自动播放
  - 队列徽标显示
  - 歌词字号
  - 最近播放
  - 失败音源记忆

### 播放

- 播放协调：`PlaybackCoordinator`
- 音源解析：`TrackSourceResolver`
- 媒体会话服务：`SPlayerPlaybackService`

## 数据流

1. Compose 页面触发 ViewModel 行为
2. ViewModel 调仓库拉取数据；默认 LOCAL 模式走原生 Kotlin/OkHttp，本地不启动 Node/Electron server
3. 播放请求进入 `PlaybackCoordinator`
4. `TrackSourceResolver` 按桌面语义选择官方源或解锁源
5. ExoPlayer 实际播放
6. 播放状态回流到 UI 与媒体会话

## 当前约束

- 默认实现 LOCAL 本地 API / 本地 unlock；REMOTE 仅在用户显式配置外部 API Root 后启用
- 评论、歌词、队列、设置已进入原生 UI
- 歌单/专辑/歌手详情页尚未进入首批交付范围
