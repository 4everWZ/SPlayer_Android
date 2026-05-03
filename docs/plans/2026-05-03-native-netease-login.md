# Android Native 登录计划废弃说明

> **For agentic workers:** 本文件是废弃记录，不再作为实现计划执行。后续实现以 `docs/specs/dev_android_api.md`、`docs/specs/dev_android_player.md`、`docs/specs/status_android_native_v2.md` 和 `docs/specs/matrix_android_native_v2.md` 为准。

**废弃原因:** 2026-05-03 用户已明确纠正：“原生 Android”只指 unlock 服务；登录不要原生化，继续使用既有远程二维码登录链路。

**当前结论:** Android Native 不实现网易云手机号登录、短信验证码登录、国家码列表或本地原生二维码登录客户端。设置页只保留远程二维码登录入口，调用已配置 `API_ROOT/netease/*`。

---

## 当前真实范围

- 登录：继续走远程 `API_ROOT/netease/login/qr/key`、`API_ROOT/netease/login/qr/create`、`API_ROOT/netease/login/qr/check`、`API_ROOT/netease/login/status`。
- API Root：APK 不内置私有根路径；用户在设置页自行填写完整根路径，例如 `https://example.com/splayer`。
- 原生能力：仅限低功耗本地 unlock，不启动桌面端 Node/Electron runtime。
- Remote unlock：只有用户切到外部 unlock 且已配置 API Root 时，才请求 `API_ROOT/unblock/*`。

## 已回滚内容

- 已删除误加的 native 登录生产实现和测试。
- 已移除 ZXing 依赖。
- `SettingsViewModel` 登录路径已改回 `SPlayerRemoteRepository`。
- 设置页已移除手机号、验证码、国家码等入口。

## 仍需跟进

- 真机验证远程二维码登录成功后，设置页、首页和推荐页不再提示缺少 API Root。
- 歌单播放开始后，分页加载的新歌曲必须动态补进当前播放列表，不能要求用户重新点一首歌。
- 文档和实现矩阵必须继续删除“Android 原生登录”残留说法。
