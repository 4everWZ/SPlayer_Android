# Tradeoffs

## AT-001

- 主题：Native V2 首阶段只做 `remote` 模式
- 原因：先把原生 UI、播放链、媒体会话和回归样本收口，再决定是否继续推进本地 API runtime
- 影响：当前 APK 依赖 `/splayer/*` 远程服务

## AT-002

- 主题：旧 `Capacitor` Android 线转为 legacy
- 原因：轻壳路线不再适合承接听歌主线的交互、功耗和稳定性目标
- 影响：`android/` 只保留应急修复，新功能进入 `android-native/`

## AT-003

- 主题：网易云对齐只承接听歌主路径，不承接商业化层
- 原因：当前 `/splayer/*` 服务已经承担解锁和故障转移语义，客户端不需要复制会员和广告入口
- 影响：Native V2 不展示 VIP、广告、商城、票务、装扮等内容

## AT-004

- 主题：设置入口收口到首页左上角抽屉
- 原因：底部一级导航和“我的”页主内容都应优先服务听歌资产
- 影响：设置不再作为底部一级 tab，也不再作为“我的”页主内容大卡

## AT-005

- 主题：播客和相关代码先冻结，不在这轮物理删除
- 原因：先缩交付面，避免为了清理旧代码扩大无关回归面
- 影响：播客能力从主导航、文档和 smoke 中移除，但仓库里仍保留冻结实现

## AT-006

- 主题：推荐页主卡当前继续复用 `personalized / toplist / album/new / top/song`
- 原因：这批接口已经与当前 remote 语义对齐，能稳定承接“推荐 / 新歌 / 歌单 / 专辑 / 排行”五个频道
- 影响：首阶段推荐页还没有专门接入“每日推荐”或独立“心动模式”专属接口，先用现有个性化歌单语义承接主卡位

## AT-007

- 主题：我的页专辑标签首阶段只接入收藏专辑
- 原因：首阶段目标是先补齐听歌资产主路径，不扩展到歌手、MV、电台等非主线资产
- 影响：`专辑` 标签当前代表已收藏专辑，不额外引入歌手、MV 和电台标签

## AT-008

- 主题：本轮功耗先按代码级空转收口和模拟器排查交付
- 原因：当前 adb 主目标是模拟器，无法拿模拟器结果替代真机热量与耗电结论
- 影响：本轮只确认高频循环生命周期已收口，真机 `batterystats / cpuinfo / gfxinfo / Perfetto` 仍保留为后续专项验收

## AT-009

- 主题：歌词高亮按真实时序分层，不伪造逐字
- 原因：部分歌曲有 `yrc / qrc / TTML` 真字级时间，部分只有普通 `lrc`
- 影响：Native V2 保留真实逐字；没有真字级时间时只退回逐句高亮，不用伪造逐字动画换取表面一致

## AT-010

- 主题：我的页头图直接使用头像 URL
- 原因：公开资料接口长期返回旧背景图，用户最终确认可以直接用头像作为个人卡片背景，避免继续追逐不可用背景 API
- 影响：Native V2 的“我的”页头图背景和圆形头像使用同一头像 URL；资料背景字段保留但不驱动头图区；图片缓存按实际头像 URL 失效，不按刷新时间戳或轮询版本号失效

## AT-011

- 主题：Android unlock 默认使用原生按需解析
- 原因：移动端不应为了默认播放链启动桌面端 Node/Electron 本地 unlock 服务，避免额外常驻进程和功耗
- 影响：默认 `unlockServerMode=LOCAL` 通过 `NativeUnblockApiClient` 原生实现 desktop local 同款公开 provider，候选顺序与 REMOTE 的 `API_ROOT/unblock/*` 一致；APK 允许包含公开 provider URL，但仍不得包含用户私有 API Root、局域网 IP 或个人反代域名；失败音源记忆仍按当前模式过滤

## AT-012

- 主题：Desktop 播放期间高频循环降频
- 原因：排查发现 desktop Electron 播放期间存在多处高频 IPC 和 RAF 循环，导致 CPU 唤醒频繁、笔记本续航下降
- 影响：
  - TaskbarLyric RAF 循环从 ~60fps 降频到 ~15fps（66ms 间隔），歌词文本不需要 60fps 更新
  - `sendTaskbarProgressData` 和 `sendMacStatusBarProgress` 补齐 250ms throttle，与其他 IPC 节奏一致
  - AMLL LyricPlayer RAF 保持 60fps（视觉动画需要），但通过 `disabled` prop 和组件卸载时自动停止
  - Native Rust 模块（UIA watcher、Tray watcher、SMTC）均为事件驱动，无忙等
  - 待后续真机验证：macOS 状态栏歌词 150ms 定时器、MPV time-pos 转发链路

## AT-013

- 主题：心动模式在列表内切歌时保持
- 原因：`updatePlayList` 默认会重置心动模式（`keepHeartbeatMode` 未设置时 `shuffleMode` 从 `heartbeat` 回退到 `off`），导致用户在心动模式下双击列表内歌曲自动退回顺序播放
- 影响：`SongList.vue` 双击播放时传入 `{ keepHeartbeatMode: true }`，仅切换整个歌单来源时才重置心动模式；播放模式循环按钮使用 `cyclePlayMode()` 统一处理所有模式（repeat-off → repeat-list → repeat-one → shuffle → heartbeat）
