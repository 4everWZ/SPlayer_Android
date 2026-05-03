# SPlayer Android V1.5 实施说明

## 1. 当前架构

Android 版本仍然是 **Capacitor 轻壳 + 现有 Vue 前端 + Web/JS 播放链路**。

这一版没有把 Node/Fastify 直接塞进 APK，也没有迁移到原生音频引擎。V1.5 的重点是：

- `/splayer/*` 新域名收口
- 播放稳定性、卡流恢复和系统媒体时间轴修复
- 移动端全屏播放器、评论和搜索容错收口
- 两种 Android 调试包构建口径

## 2. 两种 APK 口径

### `remote`

- 命令：`pnpm build:mobile:remote`
- 特征：完全依赖你自己部署的 `/splayer/*` 服务
- 适用场景：本地开发、自用调试、连接你自己的 Oracle/Nginx 服务

### `embedded`

- 命令：`pnpm build:mobile:embedded`
- 目标：APK 内置当前 App 实际使用到的 API runtime，不依赖远程 `/splayer/*`
- 当前实际状态：已完成运行时挂点、环境切换、CI 工作流与构建链；未实现的 provider 仍会回退到远程 `/splayer/*`

## 3. 已完成的代码面

### 3.1 API 路由收口

- 新增 `ApiEndpointRegistry`
- 新增 `ApiRuntime`
- 新增 `build:mobile:remote` / `build:mobile:embedded`
- 默认移动构建改为读取 `VITE_API_ROOT`

### 3.2 播放稳定性

- 新增 `playBuffering`
- 新增 `playRecovering`
- 新增 `lastProgressAt`
- 新增 `lastStablePosition`
- 新增 `waiting / emptied` 恢复链和 watchdog
- Android 媒体桥在缓冲和恢复阶段冻结时间轴

### 3.3 移动端播放器交互

- 去掉全屏播放器左右滑页
- 点击唱片进入歌词页
- 评论改成底部抽屉
- 队列保持底部抽屉并补齐安全区

### 3.4 搜索与容错

- 搜索结果页补空值保护
- 搜索结果页补错误态和重试按钮
- 关键词变化时显式重拉

### 3.5 自动化

- GitHub Actions 已新增 Android Debug APK 工作流
- `push` 后自动执行 `lint / typecheck / build:mobile:embedded / cap:sync / assembleDebug`

## 4. 当前未完成边界

> 历史说明：本节描述 Android V1.5 Capacitor 轻壳阶段。Android Native V2 已转为默认 LOCAL 原生 API / 原生 unlock，当前实现状态以 `docs/specs/dev_android_api.md`、`docs/specs/dev_android_player.md` 和 `docs/specs/status_android_native_v2.md` 为准。

### 4.1 完全本地 APK

当前 `embedded` 模式还不是“完全本地 APK”。

原因不是构建链没通，而是 `netease / unblock / qqmusic` 的内置 provider 还没补齐。现阶段：

- `ApiRuntime` 已接入
- 三个 embedded provider 文件已建好
- 但未处理的请求仍然会回退到远程 `/splayer/*`

### 4.2 原生后台播放

当前补的是标准通知栏、锁屏媒体卡片和媒体按钮控制，不是前台播放服务，也不是原生音频引擎。

## 5. 验证基线

当前这版代码应至少满足：

- `pnpm format`
- `pnpm lint`
- `pnpm typecheck:web`
- `pnpm build:mobile:remote`
- `pnpm build:mobile:embedded`
- `pnpm cap:sync`
- `.\gradlew.bat assembleDebug`

## 6. 合并建议

如果本次合并目标是：

- **Android V1.5 remote 可用**
- **embedded 构建链与 CI 打通**

那么当前分支已经具备合并条件。

如果本次合并目标是：

- **embedded 已经是完全本地 APK**

那么当前分支还不能把这个能力写成“已交付”。
