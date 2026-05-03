# Android Native V2 当前状态

## Current Objective

- 收敛 2026-05-03 这一轮 Android Native 播放、登录、推荐和 unlock 问题，先保证文档与当前实现不再互相冲突，再进入本地测试和 ADB 真机覆盖。

## Accepted Scope

### In Scope

- 登录继续走远程二维码链路，不做 Android 原生网易云登录。
- Unlock 默认走 Android 原生本地低功耗路径；外部 unlock 只在用户显式配置 API Root 并切换后启用。
- 推荐页刷新遵守“冷启动或重新打开 App 自动刷新一次，锁屏解锁不自动强刷，手动刷新必须强刷”。
- 歌单开始播放后，同一歌单后续分页加载的新歌曲必须动态补进当前播放列表。
- ADB 真机安装启动和崩溃日志检查。

### Explicitly Out of Scope

- 手机号登录、短信验证码登录、国家码列表、本地网易云登录协议移植。
- 在 APK 内嵌私有 API Root。
- 在 Android 内启动桌面端 Node/Electron 本地 API runtime。
- 功耗对比网易云官方 App 的一分钟量化测试，用户已取消。

## Current State

### Implemented

- 设置页二维码登录路径已改回 `SPlayerRemoteRepository` 远程 API。
- 设置页已移除手机号、验证码和国家码 UI。
- `checkQrState()` 已兼容远程响应外包一层 `{ code, body }` 的形态。
- API Root 从空变为非空时，Home 和 Discovery 会触发一次非全屏强刷。
- 推荐刷新协调器已避免锁屏解锁触发自动强刷。
- Unlock 设置默认本地；外部模式没有 API Root 时会回退本地。
- 我的页头图按用户最终确认直接使用头像 URL，避免继续追网易云背景图 API。
- 歌单详情已有当前歌曲高亮和“定位当前播放”按钮。
- 播放模式切换会先预览 UI 状态，心动模式会拉取 `playmode/intelligence/list`。
- 歌单播放启动时会读取同一歌单当前已缓存 tracks，并在播放源登记后立即同步一次播放队列，用于兜住“分页先完成、播放稍后登记来源”的竞态。
- 设置页新增“允许与其他应用同时播放”，默认关闭；关闭时 Media3 请求音频焦点，开启后不抢占其他应用音频。
- 设置页二维码改为居中放大显示，避免扫码图过小。

### Partially Implemented

- 歌单分页动态补播放队列已补竞态修复，但尚未 ADB 真机回归，不能宣称完全完成。
- 推荐页手动刷新已有 force refresh 入口和 timestamp，但用户仍反馈刷新无效；需要真机抓请求和响应确认是客户端未发、服务缓存还是接口内容本身不变。
- Remote 登录后推荐页仍提示缺 API Root 的问题已有 apiRoot 监听修复，但需要真机验证设置保存、登录和推荐页状态是否收敛。
- 心动模式从代码看已经有链路，但仍需真机确认登录态、喜欢歌单 id 和接口响应是否有效。

### Deferred / Not Implemented

- Android 原生网易云登录：不实现。
- 本地 API server / Node runtime：不实现。
- 网易云个人背景图抓取：不继续追，头图直接用头像。

## Active Issues

1. 歌单播放开始后，播放列表没有随着已加载歌曲动态改变，已补竞态修复，仍需真机确认。
2. Remote 登录后，推荐页仍可能显示“请先在设置中填写 API 根路径”。
3. 推荐页手动刷新用户侧仍看不到内容变化。
4. 本地 unlock 扫码后登录状态曾显示未知，需要确认远程 QR check 和 login/status 收敛。
5. 播放模式切换仍有卡顿，虽然功能能成功。
6. 心动模式用户侧曾反馈无效，需要真机确认接口与错误提示。
7. 原生登录残留已清理；只保留废弃计划和测试禁止项中的说明。
8. Git 工作区仍有大量 Android 与文档改动，最终提交前必须按范围整理。

## Validation Snapshot

- 已知目标单测曾通过：`SettingsViewModelLoginTest`、`SPlayerRemoteRepositoryTest.checkQrState*`、`ViewModelSupportTest`、`PlaybackCoordinatorSupportTest`。
- 本状态文档更新前已通过新增目标测试：`AppSettingsContractTest`、`PlaybackCoordinatorSupportTest`、`QrCodeSupportTest`、`SettingsViewModelLoginTest`。
- ADB 已由用户打开，但本轮尚未安装覆盖。

## Recommended Next Steps

1. 补歌单队列动态扩展的失败测试，覆盖“播放源来自同一歌单，后台分页补齐后队列数量和当前 index 立即更新”。
2. 根据失败测试修正队列来源生命周期或分页同步触发点。
3. 搜索并删除原生登录残留代码与文档描述。
4. 跑 targeted tests：`SettingsViewModelLoginTest`、`SPlayerRemoteRepositoryTest.checkQrState*`、`ViewModelSupportTest`、`PlaybackCoordinatorSupportTest`。
5. 跑 `:app:testDebugUnitTest :app:assembleDebug`。
6. 跑 `corepack pnpm lint`、`corepack pnpm build`、`corepack pnpm format`、`git diff --check --ignore-space-at-eol`。
7. 检查源码和 APK 中不得出现用户私有 API 地址。
8. ADB 覆盖安装、启动、logcat 崩溃检查；重点手测设置保存 API Root、二维码登录、推荐刷新、歌单分页播放队列补齐。

## Key References

- Overview: `docs/specs/00_android_native_v2.md`
- API: `docs/specs/dev_android_api.md`
- Player: `docs/specs/dev_android_player.md`
- UI: `docs/specs/dev_android_ui.md`
- Matrix: `docs/specs/matrix_android_native_v2.md`
- Integration: `docs/specs/integration_android_native_v2.md`
- Superseded Plan: `docs/plans/2026-05-03-native-netease-login.md`

## Notes for Next Thread

- 不要重新实现 Android 原生网易云登录。
- 不要把 `.env.mobile` 或用户局域网 API Root 写进 APK 默认配置。
- 不要启动本地 Node/Electron runtime。
- 歌单队列问题必须先证伪/证实 `PlaybackQueueSource` 生命周期和分页同步触发，不要只调整 UI 文案。
