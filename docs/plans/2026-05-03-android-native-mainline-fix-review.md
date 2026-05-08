# Android Native V2 主线审阅与修复方案

## 范围

- 修复 `android-native-debug.yml` 发布步骤的 bash 条件语法错误。
- 修复歌单分页补齐时正在播放被短暂清空的抖动。
- 为 Android 侧歌单添加/删除准备本地化的非轮询同步机制。
- 在唱片页提供红心入口，允许把当前歌曲加入或移出“我喜欢的音乐”。
- 记录播放器长按后的收藏到歌单等后续功能 TODO。
- 收敛播放器和前台服务的空闲资源策略，降低后台耗电风险。

## 审阅结论

当前主线是 `dev`，HEAD 为 `a01c8bef feat(android): add native local api and unlock parity`，远端 `origin/dev` 已对齐。

CI 失败来自 `.github/workflows/android-native-debug.yml` 中的 bash 正则：

```bash
if [[ "$line" =~ ^([a-zA-Z]+)(\([^)]*\))?!?:[[:space:]]*(.*)$ ]]; then
```

bash 会在 `[[ ... =~ ... ]]` 里把右侧未引用正则中的括号解析为条件表达式语法，和 Actions 日志 `unexpected token ')'` 一致。

歌单播放抖动来自分页补队列时调用 `QueueRepository.replaceQueue()`。该方法先 `clearQueue()` 再插入新队列，Room Flow 可能先发出空队列，`PlaybackCoordinator` 收到空队列后会清掉当前播放态。

Android 当前没有在线歌单添加/删除的 Mutation API 或 UI 入口，只有读取链路。桌面端只能作为“成功 mutation 后立即刷新用户歌单和详情”的语义参考，Android 侧需要做成事件化缓存失效，不能轮询。

Desktop 参考语义：

- 添加歌曲到在线歌单成功后刷新用户歌单；如果目标是喜欢歌单，还刷新喜欢歌曲。
- 收藏或取消收藏歌单成功后刷新用户歌单。
- 删除歌单成功后刷新用户歌单。
- 删除歌单内歌曲时当前详情先乐观移除，失败再提示或刷新。
- 桌面端详情页存在缓存后台检查，但 Android 不采用常驻检查或轮询，只在页面进入、用户手动刷新、mutation 事件到达时刷新。

子代理静态审阅发现两个资源风险：

- `PlaybackCoordinator` 创建 `ExoPlayer` 后没有明确释放路径。
- `SPlayerPlaybackService` 创建即前台化，缺少暂停、失败、队列清空后的空闲降级或停止策略。

## 方案选择

### 方案 A：最小修复

- CI 只把正则提到变量并补验证脚本。
- 队列只把 DAO 清空和插入包进 Room 事务。

优点是改动最小。缺点是不能解决歌单 mutation 事件化，也不能覆盖前台服务耗电风险。

### 方案 B：推荐方案

- CI 正则变量化，并扩展 `verify-android-native-debug-workflow.mjs` 检查。
- 队列替换改为单事务，避免观察者看到中间空队列。
- 新增 Android 本地 `PlaylistMutationEvent` / `SharedFlow` 语义，用于未来添加、删除、收藏、取消收藏、歌单内歌曲变更后失效缓存并触发前台页面刷新。
- `PlaybackCoordinator` 增加空闲策略判断函数和明确的用户清空队列释放路径；`SPlayerPlaybackService` 改为只在有有效播放会话时前台化，并在空闲窗口后停止服务。

优点是覆盖用户反馈和 Android 功耗边界。缺点是需要多文件测试和更严格验证。

### 方案 C：一次性补齐完整歌单编辑

- 在方案 B 基础上直接实现创建、删除、添加到歌单、移除歌曲、收藏歌单 UI。

优点是功能最完整。缺点是 UI 和 API 面扩大，验证成本高，不适合作为本轮顺手修复。

## 推荐设计

采用方案 B。

### CI

把 conventional commit 正则写成变量：

```bash
commit_subject_pattern='^([a-zA-Z]+)(\([^)]*\))?!?:[[:space:]]*(.*)$'
if [[ "$line" =~ $commit_subject_pattern ]]; then
```

验证脚本增加对变量化正则的检查，避免同类语法回归。

### 播放队列

在 `PlaybackQueueDao` 增加事务方法，保证清空和插入对 Flow 观察者表现为一次队列替换。测试先用 fake DAO 复现 `clear -> empty -> insert` 会导致播放态被清空，再验证事务替换不暴露空队列。

### 歌单同步

在 `SPlayerRemoteRepository` 内建立事件化失效机制：

- 成功 mutation 后发出事件，不轮询。
- 事件携带 `playlistId`、操作类型和影响范围：
  - `PlaylistTracksChanged`：歌单内歌曲添加、删除、重排。
  - `UserPlaylistLibraryChanged`：创建、删除、收藏、取消收藏歌单。
  - `LikedSongsChanged`：喜欢歌单歌曲变化。
- 清理内存页缓存和详情缓存。
- `MyViewModel` 只在可见生命周期内收集事件并触发轻量刷新。
- `PlaylistDetailViewModel` 只对当前歌单事件刷新当前详情。

当前 Android 尚无 mutation UI，本轮先补事件与仓库接口骨架；后续添加 UI 时直接调用这些接口。

### 唱片页红心

唱片页歌曲信息右侧加入红心按钮：

- 未喜欢时显示空心红心，点击后调用 `/like?id={songId}&like=true`。
- 已喜欢时显示实心红心，点击后调用 `/like?id={songId}&like=false`。
- 成功后立即更新本地喜欢状态，并发出 `LikedSongsChanged` 与喜欢歌单的 `PlaylistTracksChanged`。
- 失败时保留原状态，并通过播放器错误提示给出可见反馈。
- 初始喜欢状态来自 `likelist`，不额外轮询。

### 长按功能 TODO

本轮只记录 TODO，不直接实现长按面板：

- 长按歌曲信息或唱片页更多按钮后打开“收藏到歌单”底部面板。
- 面板顶部提供“新建歌单”和“多选”入口。
- 歌单列表来自当前用户创建歌单，不展示无法写入的收藏歌单。
- 选择歌单后调用 `playlist/tracks` 添加歌曲，并发出 `PlaylistTracksChanged`。
- 多选模式允许一次添加到多个歌单，但需要批量请求失败汇总和部分成功提示。
- 长按菜单后续还可接入下载、分享、查看专辑、查看歌手，不在本轮实现。

### 功耗与释放

不在普通暂停时释放播放器，避免破坏通知恢复和小播放条恢复。只在用户明确清空队列、播放失败且无可恢复队列、或服务空闲超时后降级。

建议新增纯函数测试：

- 有播放中歌曲时服务保持前台。
- 暂停但有当前歌曲时保留可恢复状态，不立即释放。
- 队列清空且不播放时允许停止服务并清理播放器 media items。

## 验证

- `corepack pnpm exec node scripts/verify-android-native-debug-workflow.mjs`
- `bash -n` 或等价脚本片段解析检查。
- `android-native\gradlew.bat testDebugUnitTest`
- `android-native\gradlew.bat assembleDebug`
- `corepack pnpm lint`
- 如需提交前再跑 `corepack pnpm build` 和 `corepack pnpm format`

## 待确认

本轮是否按推荐方案 B 执行。
