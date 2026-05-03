# Android Native 本地 API 登录与 Release Notes 实施记录

> **For agentic workers:** 本文件记录 2026-05-03 本地 API 登录与 release notes 修复的当前实施范围。后续实现以 `docs/specs/dev_android_api.md`、`docs/specs/dev_android_player.md`、`docs/specs/status_android_native_v2.md` 和 `docs/specs/matrix_android_native_v2.md` 为准。

## 当前目标

- Android Native 首次安装默认使用本地 API 模式。
- 本地 API 模式不要求填写 `apiRoot`，不启动 Node/Electron，不启动本地 HTTP server。
- 二维码登录继续使用网易云二维码链路，但 LOCAL 由 `NativeNeteaseApiClient` 用 Kotlin/OkHttp 请求网易云接口，REMOTE 才请求用户填写的外部 `API_ROOT/netease/*`。
- 推荐、发现、我的、歌单、搜索、歌词、评论、心动模式和官方音源等 Android 当前已用 `/netease/*` endpoint，在 LOCAL 模式优先走原生实现。
- 发布 workflow 使用唯一 tag 追加 release，并按 Conventional Commit 分类生成中文 release notes。

## 明确不做

- 不实现手机号登录、短信验证码登录和国家码列表。
- 不把 `.env.mobile`、用户局域网地址或私有 API Root 写进 APK。
- 不在 Android 内启动桌面端 Node/Electron runtime。
- 不把用户私有 API Root 当成本地 provider；LOCAL 只内置 desktop local 同款公开 provider 上游。

## 当前实现

- `SwitchingNeteaseApiClient` 按 `UnlockServerMode` 分发 LOCAL/REMOTE。
- `NativeNeteaseApiClient` 覆盖 Android 当前已用的网易云 endpoint，并生成二维码 data URI。
- `RemoteNeteaseApiClient` 只在 REMOTE 下使用用户配置的 API Root，空根路径时返回模式化提示。
- `SPlayerRemoteRepository` 不再直接用 API Root 判断网易云登录是否可用，改为依赖 `NeteaseApiClient`。
- Cookie 仍落到 `AppSettingsStore`，OkHttp 响应 Cookie 持久化改为应用 IO scope 异步写入，避免阻塞拦截器线程。
- `NativeUnblockApiClient` 用 Kotlin/OkHttp 原生实现 desktop local 同款 `bodian / gequbao / netease / kuwo` provider。
- LOCAL 与 REMOTE 的解锁候选顺序一致，REMOTE 走用户 API Root，LOCAL 不开 Node server。
- `.github/workflows/android-native-debug.yml` 使用 `android-native-debug-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}-${GITHUB_SHA}` 类唯一 tag，release body 包含构建信息和中文分类更新内容。

## 验证要求

- `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug`
- `corepack pnpm lint`
- `corepack pnpm build`
- `corepack pnpm format`
- `git diff --check --ignore-space-at-eol`
- APK 扫描不得出现用户私有 IP、`.env.mobile` 地址或个人 API Root；公开第三方 provider URL 属于预期内置依赖。
- ADB 卸载安装后启动，检查 logcat 中无 AndroidRuntime crash。
