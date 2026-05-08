# Android Native V2 当前状态

## Current Objective

- 收敛 2026-05-03 这一轮 Android Native 播放、登录、推荐和 unlock/API 问题，先保证文档与当前实现不再互相冲突，再进入本地测试和 ADB 真机覆盖。

## Accepted Scope

### In Scope

- 登录继续走二维码链路；LOCAL 模式由 Android 原生 Kotlin/OkHttp 实现，REMOTE 模式走外部 API Root。
- Unlock/API 默认走 Android 原生本地低功耗路径；远程 API/unlock 只在用户显式切换后启用。
- 推荐页刷新遵守“冷启动或重新打开 App 自动刷新一次，锁屏解锁不自动强刷，手动刷新必须强刷”。
- 歌单开始播放后，同一歌单后续分页加载的新歌曲必须动态补进当前播放列表。
- ADB 真机安装启动和崩溃日志检查。

### Explicitly Out of Scope

- 手机号登录、短信验证码登录、国家码列表。
- 在 APK 内嵌私有 API Root。
- 在 Android 内启动桌面端 Node/Electron 本地 API runtime。
- 功耗对比网易云官方 App 的一分钟量化测试，用户已取消。

## Current State

### Implemented

- 设置页二维码登录路径仍统一经 `SPlayerRemoteRepository`，底层由 `SwitchingNeteaseApiClient` 按 LOCAL/REMOTE 分发。
- 设置页已移除手机号、验证码和国家码 UI。
- LOCAL 模式默认无需 API Root；`NativeNeteaseApiClient` 覆盖 Android 当前已用 `/netease/*` endpoint。
- LOCAL 与 REMOTE 的播放解锁候选顺序已按 desktop 默认启用优先级统一为 `bodian -> gequbao -> netease -> kuwo`；LOCAL 由 Kotlin/OkHttp 原生执行，不启动 Node/Electron runtime。
- `checkQrState()` 已兼容远程响应外包一层 `{ code, body }` 的形态。
- API Root 从空变为非空时，Home 和 Discovery 会触发一次非全屏强刷。
- 推荐刷新协调器已避免锁屏解锁触发自动强刷。
- API 模式默认本地；切到远程 API 后若 API Root 为空，保留远程选择并给明确提示。
- 我的页头图按用户最终确认直接使用头像 URL，避免继续追网易云背景图 API。
- 歌单详情已有当前歌曲高亮和“定位当前播放”按钮。
- 播放模式切换会先预览 UI 状态，心动模式会拉取 `playmode/intelligence/list`。
- 歌单播放启动时会读取同一歌单当前已缓存 tracks，并在播放源登记后立即同步一次播放队列，用于兜住“分页先完成、播放稍后登记来源”的竞态。
- 设置页新增“允许与其他应用同时播放”，默认关闭；关闭时 Media3 请求音频焦点，开启后不抢占其他应用音频。
- 设置页二维码改为居中放大显示，避免扫码图过小。
- Room 数据库已升到 v3，新增 `playback_snapshot` 单行表保存当前歌曲、索引、进度、时长和保存时间。
- 播放器冷启动会恢复上次播放条、队列、进度和播放模式；默认暂停态，不自动解析音源或启动播放服务。
- 小播放条已增加细进度条，使用恢复态或播放中的秒级进度。

### Partially Implemented

- 歌单分页动态补播放队列已补竞态修复，但尚未 ADB 真机回归，不能宣称完全完成。
- 推荐页手动刷新已有 force refresh 入口和 timestamp，但用户仍反馈刷新无效；需要真机抓请求和响应确认是客户端未发、服务缓存还是接口内容本身不变。
- Remote 登录后推荐页仍提示缺 API Root 的问题已有 apiRoot 监听修复，但需要真机验证设置保存、登录和推荐页状态是否收敛。
- 心动模式从代码看已经有链路，但仍需真机确认登录态、喜欢歌单 id 和接口响应是否有效。

### Deferred / Not Implemented

- 本地 API server / Node runtime：不实现。
- 网易云个人背景图抓取：不继续追，头图直接用头像。
- QQMusic 当前只作为歌词增强 fallback，不属于播放 unlock 候选；LOCAL 未命中网易云歌词/TTML 时允许该 fallback 静默降级为空。

## Active Issues

1. 歌单播放开始后，播放列表没有随着已加载歌曲动态改变，已补竞态修复，仍需真机确认。
2. Remote 模式缺 API Root 时应显示“远程 API 模式需要先填写 API 根路径，或切回本地 API 模式登录/使用”，不得显示旧的通用 API Root 提示。
3. 推荐页手动刷新用户侧仍看不到内容变化。
4. 本地 API 扫码后登录状态需真机确认 QR check、Cookie 持久化和 login/status 收敛。
5. 播放模式切换仍有卡顿，虽然功能能成功。
6. 心动模式用户侧曾反馈无效，需要真机确认接口与错误提示。
7. 原生 LOCAL API 已接入；仍需真机扫码验证网易云协议响应。
8. Git 工作区仍有大量 Android 与文档改动，最终提交前必须按范围整理。

## Validation Snapshot

- 已知目标单测曾通过：`SettingsViewModelLoginTest`、`SPlayerRemoteRepositoryTest.checkQrState*`、`ViewModelSupportTest`、`PlaybackCoordinatorSupportTest`。
- 本状态文档更新前已通过新增目标测试：`AppSettingsContractTest`、`PlaybackCoordinatorSupportTest`、`QrCodeSupportTest`、`SettingsViewModelLoginTest`。
- 本轮播放会话恢复已通过目标测试：`PlaybackCoordinatorSupportTest`、`QueueRepositoryTest`。
- ADB 已由用户打开，但本轮尚未安装覆盖。

## Desktop 高频功耗修复 (2026-05-08)

已排查并修复 desktop Electron 播放期间的高频循环问题：

1. **TaskbarLyric RAF 循环降频**：从 ~60fps 降到 ~15fps（66ms 间隔），歌词文本更新不需要 60fps
2. **IPC 补齐 throttle**：`sendTaskbarProgressData` 和 `sendMacStatusBarProgress` 补齐 250ms throttle，与其他 IPC 节奏一致
3. **VitePress 侧边栏对齐**：Android Native V2 规约、架构、tradeoffs 文档已加入侧边栏导航
4. **Tradeoffs 文档更新**：新增 AT-012 记录 desktop 高频降频决策

验证状态：`pnpm typecheck:web` 通过，`pnpm build` 编译成功。

## Recommended Next Steps

1. 跑 `:app:testDebugUnitTest :app:assembleDebug`。
2. 跑 `corepack pnpm lint`、`corepack pnpm format`、`git diff --check --ignore-space-at-eol`。
3. 检查源码和 APK 中不得出现用户私有 API 地址。
4. ADB 覆盖安装、启动、logcat 崩溃检查；重点手测播放一首歌到中间，划掉重开后确认小播放条、唱片页、进度和播放模式恢复，且不会自动播放。
5. 继续回归设置保存 API Root、二维码登录、推荐刷新、歌单分页播放队列补齐。
6. macOS 真机验证：状态栏歌词 150ms 定时器功耗、MPV time-pos 转发链路频率。

## Key References

- Overview: `docs/specs/00_android_native_v2.md`
- API: `docs/specs/dev_android_api.md`
- Player: `docs/specs/dev_android_player.md`
- UI: `docs/specs/dev_android_ui.md`
- Matrix: `docs/specs/matrix_android_native_v2.md`
- Integration: `docs/specs/integration_android_native_v2.md`
- Current Plan: `docs/plans/2026-05-03-native-netease-login.md`

## Notes for Next Thread

- 不要引入手机号/验证码登录，二维码链路已经是本地 API 与远程 API 的统一入口。
- 不要把 `.env.mobile` 或用户局域网 API Root 写进 APK 默认配置。
- 不要启动本地 Node/Electron runtime。
- 歌单队列问题必须先证伪/证实 `PlaybackQueueSource` 生命周期和分页同步触发，不要只调整 UI 文案。
