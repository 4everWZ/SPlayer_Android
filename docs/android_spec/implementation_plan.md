# SPlayer Capacitor Android V1 实施计划

## 摘要

本项目目标是将现有的 SPlayer 前端页面封装为一个独立的 **Android App**。使用 **Capacitor** 作为前端容器并覆盖现有部署的 Oracle API。目标通过 V1 交付，实现**App化**和**移动端UI可用性**，使应用具备类似原生 App 的体验。

## User Review Required

> [!IMPORTANT]
>
> 1. **环境准备**：Capacitor 的 Android 构建重度依赖 Java 以及完整的 Android SDK 环境。如果没有 `ANDROID_HOME` 和相应的 Build Tools，需要您确认协助（如果我这边脚本无法自动下载的话）。
> 2. **UI 限制**：由于项目规定必须使用 Naive UI，针对移动端弹窗/抽屉（如 Bottom Sheet）的改造将使用 Naive UI 提供的高度可定制 `n-drawer` (placement="bottom")。

## Proposed Changes

### 1. Capacitor 框架集成

配置与安装必要的 Capacitor 环境及其 Android 桥接。

#### [NEW] [capacitor.config.ts](file:///d:/Code/TypeScript/SPlayer_Android/capacitor.config.ts)

新增 Capacitor 配置文件，设定基础的 App ID 与 webDir (`dist` 等配置)，并注册插件。

#### [MODIFY] [package.json](file:///d:/Code/TypeScript/SPlayer_Android/package.json)

引入 `@capacitor/core`, `@capacitor/cli`, `@capacitor/android`, `@capacitor/share` 等依赖；增加相关的 Capacitor 脚手架命令：

- `cap:sync`: `cap sync android`
- `cap:open`: `cap open android`

#### [NEW] [.env.mobile](file:///d:/Code/TypeScript/SPlayer_Android/.env.mobile) (或者修改原有配置以支持独立 Android 构建)

设定移动端专属环境变量（例如指向主后端的 API URI）：`VITE_API_URL=http://159.13.36.12/api/netease`。并从打包配置 (`vite.config.ts` 等) 区分出 `build:mobile` 的特殊处理逻辑（或者保持一样只需打包到特定目录）。

### 2. P0页面级 UI 移动端特化改造

根据设计规范“移动优先，去除全量横向滚动与桌面布局硬塞”，使用 `max-width` 改写固定宽度，并在以下核心页面做调整：

#### 全局布局层

- 内容区滚动容器改造，使用 `min-height: 100dvh` 与安全区变量计算：`padding-top: env(safe-area-inset-top)`。
- 底部播放器不再固定盖住下方元素，为正文增加 `padding-bottom`。

#### P0页面 (首页 / 搜索 / 登录 / 播放 / 详情 / 设置)

- **播放与底层控制栏**：底部常驻、移动端控件排列垂直化。
- **歌单与列表**：长文本截断优化，单行双行溢出省略。调整触控区域至 44dp 以上。
- **搜索**：解决输入法弹起导致结果被遮挡。
- **登录二维码**：页面居中保证在各类手机屏不被裁剪。

### 3. 系统能力桥接层封装

抽离通用 API：
例如新建 `src/utils/platform.ts` ：
如果 Capacitor 环境成立，使用 `@capacitor/share` 调用原生分享面板。如果是纯 Web，则回退到 `navigator.share`。

## Open Questions

> [!WARNING]
>
> 1. 检测到您的系统暂未定义 `ANDROID_HOME` ，您可以先手动下载 Android Studio 补齐 SDK，然后把 SDK 路径告诉我，或者我稍后给您写脚本去拉 SDK 命令行？
> 2. 最终的 Android App 包名是否有具体要求？如果没有的话，我会先使用 `com.splayer.app` 占位。

## Verification Plan

### 自动化验证

完成安装后，利用 `pnpm lint` 和 `pnpm typecheck` 跑通检查；然后使用 `npx cap sync android` 生成本地工程确保不报错。

### 构建与界面人工验证

后续由您启动 Android Studio 安装 debug 包。在手机上确认横向不出图、登录会话维持正常并且 UI 无相互遮挡。
