# Android Native Netease Login Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 Android Native 在未配置 `apiRoot` 时提供与远程 `API_ROOT/netease/*` 登录接口等价的本地登录能力；完成标准不是“只能扫码”，而是覆盖 desktop 当前登录 API 面，同时不在 APK 内启动或常驻 Node/JS runtime。

**Architecture:** `SPlayerRemoteRepository` 保留远程 `/netease/*` 语义和非登录远程数据能力；新增 `NeteaseLoginRepository` 作为唯一登录路由层。`apiRoot` 为空时，登录路由层才通过 `Provider<NeteaseLoginClient>` 懒加载 Kotlin 原生登录实现；`apiRoot` 已配置时，登录路由层只委托远程仓储，不实例化原生登录实现，不分配二维码 bitmap 生成器，不访问网易云原生端点。原生登录接口面覆盖二维码登录、短信验证码、手机号登录、国家码列表、登录态、刷新登录和登出，并且按用户动作按需请求，不启动本地 HTTP server，不加载 Node。

**Tech Stack:** Kotlin、OkHttp、kotlinx.serialization、ZXing Core、JUnit4、Hilt DI、Gradle Android Native。

---

## Scope And Current State

- [x] 已确认 desktop 的登录能力来自 `@neteasecloudmusicapienhanced/api`，不是浏览器页面本身。
- [x] 已确认 Node 常驻不适合作为 Android 默认方案；本阶段只移植远程登录 API 等价所需协议，非登录业务 API 不原生化。
- [x] 已新增 WIP 测试文件 `android-native/app/src/test/java/top/imsyy/splayer/nativeapp/data/repository/NativeNeteaseLoginClientTest.kt`。
- [x] 已在 `android-native/app/src/test/java/top/imsyy/splayer/nativeapp/data/repository/SPlayerRemoteRepositoryTest.kt` 加入部分 WIP 测试。
- [x] WIP 测试当前预期无法编译，因为生产代码还没有 `NeteaseLoginClient`、`NativeNeteaseLoginClient`、`NativeNeteaseHttpTransport` 等类型。
- [x] WIP 测试需要跟随本修订调整：登录路由测试迁移到 `NeteaseLoginRepositoryTest.kt`；`SPlayerRemoteRepositoryTest.kt` 只保留远程 API 行为测试。
- [x] WIP 测试需要跟随本修订调整：`fetchQrImage()` 应断言 `data:image/png;base64,`，不能只断言二维码 URL。
- [x] WIP 测试需要补齐短信验证码、手机号登录、国家码、刷新登录、登出和 remote 模式 native provider 不实例化的契约。
- [x] 本轮本地 RED/GREEN 已补 `SettingsViewModelLoginTest`：设置页初始化不触发登录仓储，QR 停止和 ViewModel 清理会取消轮询。
- [ ] 本轮手机已撤，ADB 安装、启动、真人扫码和短信验证码真机回归暂未执行。

## File Structure

- Create: `android-native/app/src/main/java/top/imsyy/splayer/nativeapp/data/repository/NeteaseLoginClient.kt`
  - 负责完整登录客户端接口、原生 cookie DTO、HTTP transport DTO、登录结果 DTO。
- Create: `android-native/app/src/main/java/top/imsyy/splayer/nativeapp/data/repository/NeteaseLoginRepository.kt`
  - 负责按 `apiRoot` 是否配置路由登录请求；remote 模式只委托 `SPlayerRemoteRepository`，local 模式才懒加载 `NeteaseLoginClient`。
- Create: `android-native/app/src/main/java/top/imsyy/splayer/nativeapp/data/repository/NativeNeteaseCrypto.kt`
  - 负责网易云 `weapi` 与 `eapi` 加密。
- Create: `android-native/app/src/main/java/top/imsyy/splayer/nativeapp/data/repository/NativeNeteaseLoginClient.kt`
  - 负责二维码登录、登录态、刷新登录、登出、短信验证码、手机号登录、国家码列表原生请求。
- Create: `android-native/app/src/main/java/top/imsyy/splayer/nativeapp/data/repository/NativeQrCodeGenerator.kt`
  - 负责把 `https://music.163.com/login?codekey={key}` 生成为 `data:image/png;base64,...`，与远程 `qr/create?qrimg=true` 返回形态一致。
- Modify: `android-native/app/src/main/java/top/imsyy/splayer/nativeapp/data/repository/SPlayerRemoteRepository.kt`
  - 保持 remote API 登录语义，并补齐 refresh/logout/手机号/验证码/国家码远程方法；不负责本地/远程路由。
- Modify: `android-native/app/src/main/java/top/imsyy/splayer/nativeapp/di/AppModule.kt`
  - 注入 `NeteaseLoginRepository`，通过 provider/factory 懒加载原生登录客户端，remote 模式不实例化本地登录实现，不启动本地 Node。
- Modify: `android-native/app/src/main/java/top/imsyy/splayer/nativeapp/ui/ViewModels.kt`
  - `SettingsViewModel` 的登录相关调用改走 `NeteaseLoginRepository`，非登录远程数据仍走原仓储。
- Modify: `android-native/app/build.gradle.kts`
  - 添加轻量二维码生成依赖 `com.google.zxing:core`。
- Modify: `android-native/app/src/main/java/top/imsyy/splayer/nativeapp/ui/screen/SettingsScreen.kt`
  - 文案说明“本地登录无需 API Root；推荐/歌单/搜索等非登录远程功能仍需要 API Root”。
- Modify: `docs/specs/dev_android_api.md`
  - 同步 API Root、原生登录等价接口面、remote 模式资源边界。
- Test: `android-native/app/src/test/java/top/imsyy/splayer/nativeapp/data/repository/NativeNeteaseLoginClientTest.kt`
- Test: `android-native/app/src/test/java/top/imsyy/splayer/nativeapp/data/repository/SPlayerRemoteRepositoryTest.kt`
  - 只测试 `API_ROOT/netease/*` 远程路径、参数、cookie body 解析和非登录远程接口仍需 `apiRoot`。
- Test: `android-native/app/src/test/java/top/imsyy/splayer/nativeapp/data/repository/NeteaseLoginRepositoryTest.kt`
  - 测试本地/远程登录路由、运行中 `apiRoot` 变化、remote 模式 provider 不实例化。
- Test: `android-native/app/src/test/java/top/imsyy/splayer/nativeapp/data/repository/NativeQrCodeGeneratorTest.kt`
- Test: `android-native/app/src/test/java/top/imsyy/splayer/nativeapp/data/repository/NativeLoginResourceContractTest.kt`
- Test: `android-native/app/src/test/java/top/imsyy/splayer/nativeapp/ui/SettingsViewModelLoginTest.kt`

## Desktop Protocol Facts

- Before implementing protocol code, open and re-check every listed `node_modules/@neteasecloudmusicapienhanced/api/module/*.js` file. If endpoint, crypto type, payload field, cookie behavior, or QR generation behavior differs from this plan, update this plan first and then implement.
- `login/qr/key`:
  - Desktop module: `node_modules/@neteasecloudmusicapienhanced/api/module/login_qr_key.js`
  - Native request: `POST https://interface.music.163.com/eapi/login/qrcode/unikey`
  - Request data before encryption: `{"type":3}`
  - Crypto: `eapi` for URI `/api/login/qrcode/unikey`
- `login/qr/create`:
  - Desktop module only builds QR URL/image locally.
  - Native must generate a QR image data URI from `https://music.163.com/login?codekey={key}` when `qrimg=true` semantics are requested.
- `login/qr/check`:
  - Desktop module: `node_modules/@neteasecloudmusicapienhanced/api/module/login_qr_check.js`
  - Native request: `POST https://interface.music.163.com/eapi/login/qrcode/client/login`
  - Request data before encryption: `{"key":"{key}","type":3}`
  - Crypto: `eapi` for URI `/api/login/qrcode/client/login`
  - `Set-Cookie` values must be compacted to `MUSIC_U=...; __csrf=...; NMTID=...` before `AppSettingsStore.updateCookiesFromHeader()`.
- `login/status`:
  - Desktop module: `node_modules/@neteasecloudmusicapienhanced/api/module/login_status.js`
  - Native request: `POST https://music.163.com/weapi/w/nuser/account/get`
  - Request data before encryption: `{"csrf_token":"{__csrf from cookie}"}`
  - Crypto: `weapi`
- `user/account`:
  - Desktop module: `node_modules/@neteasecloudmusicapienhanced/api/module/user_account.js`
  - Native request: `POST https://music.163.com/weapi/nuser/account/get`
  - Request data before encryption: `{"csrf_token":"{__csrf from cookie}"}`
  - Crypto: `weapi`
- `login/refresh`:
  - Desktop module: `node_modules/@neteasecloudmusicapienhanced/api/module/login_refresh.js`
  - Native request: `POST https://interface.music.163.com/eapi/login/token/refresh`
  - Request data before encryption: `{}`
  - Crypto: `eapi` for URI `/api/login/token/refresh`
  - Successful response may return login cookies and must compact them into the same cookie header format as QR login.
- `logout`:
  - Desktop module: `node_modules/@neteasecloudmusicapienhanced/api/module/logout.js`
  - Native request: `POST https://interface.music.163.com/eapi/logout`
  - Request data before encryption: `{}`
  - Crypto: `eapi` for URI `/api/logout`
- `captcha/sent`:
  - Desktop module: `node_modules/@neteasecloudmusicapienhanced/api/module/captcha_sent.js`
  - Native request: `POST https://music.163.com/weapi/sms/captcha/sent`
  - Request data before encryption: `{"ctcode":"86","secrete":"music_middleuser_pclogin","cellphone":"{phone}","csrf_token":"{__csrf from cookie}"}`
  - Crypto: `weapi`
- `captcha/verify`:
  - Desktop module: `node_modules/@neteasecloudmusicapienhanced/api/module/captcha_verify.js`
  - Native request: `POST https://music.163.com/weapi/sms/captcha/verify`
  - Request data before encryption: `{"ctcode":"86","cellphone":"{phone}","captcha":"{captcha}","csrf_token":"{__csrf from cookie}"}`
  - Crypto: `weapi`
- `login/cellphone`:
  - Desktop module: `node_modules/@neteasecloudmusicapienhanced/api/module/login_cellphone.js`
  - Native request: `POST https://music.163.com/weapi/w/login/cellphone`
  - Request data before encryption with captcha: `{"type":"1","https":"true","phone":"{phone}","countrycode":"86","captcha":"{captcha}","remember":"true"}`
  - Request data before encryption with password: `{"type":"1","https":"true","phone":"{phone}","countrycode":"86","password":"{md5Password}","remember":"true"}`
  - Crypto: `weapi`
  - Successful response cookie handling must match remote API body `cookie` behavior.
- `countries/code/list`:
  - Desktop module: `node_modules/@neteasecloudmusicapienhanced/api/module/countries_code_list.js`
  - Native request: `POST https://interface.music.163.com/eapi/lbs/countries/v1`
  - Request data before encryption: `{}`
  - Crypto: `eapi` for URI `/api/lbs/countries/v1`

## Local And Remote Parity Contract

- This implementation must ship the complete current desktop login API surface in one release: `login/qr/key`, `login/qr/create`, `login/qr/check`, `login/status`, `user/account`, `captcha/sent`, `captcha/verify`, `login/cellphone`, `countries/code/list`, `login/refresh`, `logout`.
- No login method may be documented as implemented unless both local native mode and remote/API Root mode expose the same Kotlin method and compatible result semantics.
- Remote/API Root mode must continue calling `API_ROOT/netease/*` for every login method and must not instantiate `NativeNeteaseLoginClient`.
- Empty `apiRoot` mode must call absolute upstream网易云 URLs for login only; discovery, playlist, search, lyric, comments and external unlock still require API Root unless separately native-implemented later.
- Cookie handling must be equivalent across QR, cellphone, refresh and logout: compact `Set-Cookie` or remote body `cookie` into the existing header format for `AppSettingsStore.updateCookiesFromHeader()`, and never log `MUSIC_U`, `__csrf`, phone or captcha.

## Power And Resource Contract

- Do not embed Node, V8, JS runtime, desktop server code, or a local HTTP server into the APK.
- Do not start a foreground service, background service, WorkManager task, polling coroutine, or application-scope job for login.
- QR polling remains UI-owned and must run only while the QR card is visible; `SettingsViewModel.stopQrLogin()` must cancel it.
- Native login client must be created lazily via provider/factory and only when the selected login path is local.
- Remote/API Root mode must not call `nativeLoginClientProvider.get()`, must not allocate QR bitmap generation objects, and must not request `music.163.com` or `interface.music.163.com` native endpoints.
- HTTP requests use the shared OkHttp client and run on `Dispatchers.IO`; do not create an extra executor, thread pool, retry loop, or timer except the existing short `fetchLoginStateWithRetry()` after login success.
- QR image generation is synchronous CPU work for one bitmap per QR key; no repeated regeneration while the same key is active.
- Entering Settings without pressing login must not start a network request to native NetEase endpoints.
- Stopping QR login, QR expiry, login success, and `SettingsViewModel.onCleared()` must cancel polling; a test must prove no later `checkQrState()` call happens after cancellation.

## Task 1: Keep The Failing Tests As The Contract

**Files:**

- Modify: `android-native/app/src/test/java/top/imsyy/splayer/nativeapp/data/repository/SPlayerRemoteRepositoryTest.kt`
- Create: `android-native/app/src/test/java/top/imsyy/splayer/nativeapp/data/repository/NativeNeteaseLoginClientTest.kt`
- Create: `android-native/app/src/test/java/top/imsyy/splayer/nativeapp/data/repository/NeteaseLoginRepositoryTest.kt`
- Create: `android-native/app/src/test/java/top/imsyy/splayer/nativeapp/data/repository/NativeQrCodeGeneratorTest.kt`
- Create: `android-native/app/src/test/java/top/imsyy/splayer/nativeapp/data/repository/NativeLoginResourceContractTest.kt`
- Create: `android-native/app/src/test/java/top/imsyy/splayer/nativeapp/ui/SettingsViewModelLoginTest.kt`

- [ ] **Step 1: Move routing tests out of `SPlayerRemoteRepositoryTest.kt`**

Delete or move tests whose subject is local/native provider selection from `SPlayerRemoteRepositoryTest.kt`:

```kotlin
@Test
fun `fetchLoginState uses native login provider when api root is blank`() = runBlocking { ... }

@Test
fun `fetchQrKey and checkQrState use native login provider when api root is blank`() = runBlocking { ... }

@Test
fun `fetchLoginState keeps remote api root path when configured even with native provider`() = runBlocking { ... }

@Test
fun `configured api root login methods do not instantiate native provider`() = runBlocking { ... }
```

Recreate those behaviors in `NeteaseLoginRepositoryTest.kt`, because only `NeteaseLoginRepository` chooses local vs remote.

- [ ] **Step 2: Keep remote API and unlock tests in `SPlayerRemoteRepositoryTest.kt`**

Expected remote-only tests:

```kotlin
@Test
fun `remote login methods use configured api root paths`() = runBlocking { ... }

@Test
fun `remote login mutation parses cookie body`() = runBlocking { ... }

@Test
fun `resolveSongSource in blank api root local mode skips remote official and uses native direct unlock`() = runBlocking { ... }

@Test
fun `non login remote requests require api root when blank`() = runBlocking { ... }
```

- [ ] **Step 3: Add complete native client tests**

Expected test cases in `NativeNeteaseLoginClientTest.kt`:

```kotlin
@Test
fun `fetchQrKey posts native eapi qrcode unikey request`() = runBlocking { ... }

@Test
fun `checkQrState returns code and compact cookie header`() = runBlocking { ... }

@Test
fun `fetchLoginState reads login status then prefers user account profile`() = runBlocking { ... }

@Test
fun `fetchQrImage returns data uri image compatible with remote qrimg response`() = runBlocking { ... }

@Test
fun `refreshLogin returns compact cookie header`() = runBlocking { ... }

@Test
fun `logout posts native eapi logout request`() = runBlocking { ... }

@Test
fun `sendCaptcha verifyCaptcha loginCellphone and countryList use native endpoints`() = runBlocking { ... }
```

- [ ] **Step 4: Add routing, QR generator, resource, and polling tests**

Expected tests in `NeteaseLoginRepositoryTest.kt`:

```kotlin
@Test
fun `blank api root uses native login provider for every login method`() = runBlocking { ... }

@Test
fun `configured api root uses remote login methods and does not instantiate native provider`() = runBlocking { ... }

@Test
fun `api root changes are observed on next login call`() = runBlocking { ... }
```

Expected tests in `NativeQrCodeGeneratorTest.kt`:

```kotlin
@Test
fun `qr generator returns png data uri`() { ... }
```

Expected tests in `NativeLoginResourceContractTest.kt`:

```kotlin
@Test
fun `android native login source does not reference node or local server runtime`() { ... }
```

Expected tests in `SettingsViewModelLoginTest.kt`:

```kotlin
@Test
fun `start qr login does not poll after stop`() = runTest { ... }

@Test
fun `settings init does not call native login network before user action`() = runTest { ... }
```

- [ ] **Step 5: Run tests to verify RED**

Run:

```powershell
cd android-native
.\gradlew.bat :app:testDebugUnitTest --tests "top.imsyy.splayer.nativeapp.data.repository.NativeNeteaseLoginClientTest" --tests "top.imsyy.splayer.nativeapp.data.repository.NeteaseLoginRepositoryTest" --tests "top.imsyy.splayer.nativeapp.data.repository.SPlayerRemoteRepositoryTest"
```

Expected: FAIL/compile error mentioning missing `NeteaseLoginClient` or `NativeNeteaseLoginClient`.

- [ ] **Step 6: Do not weaken the test expectations**

Keep these behavioral assertions:

```kotlin
assertEquals(0, api.calls.size)
assertEquals("https://interface.music.163.com/eapi/login/qrcode/unikey", request.url)
assertEquals("MUSIC_U=native-music-u; __csrf=native-csrf", state.cookieHeader)
assertEquals(listOf("https://music-api.gdstudio.xyz/api.php"), api.calls.map { it.url })
assertTrue(repositoryNativeProviderCreatedCount == 0)
assertTrue(qrImage.startsWith("data:image/png;base64,"))
assertFalse(remoteModeCalls.any { it.contains("music.163.com") })
```

## Task 2: Add The Native Login Interfaces

**Files:**

- Create: `android-native/app/src/main/java/top/imsyy/splayer/nativeapp/data/repository/NeteaseLoginClient.kt`

- [ ] **Step 1: Create the interface and DTOs**

Add:

```kotlin
package top.imsyy.splayer.nativeapp.data.repository

import top.imsyy.splayer.nativeapp.model.UserAccountUi

interface NeteaseLoginClient {
    suspend fun fetchLoginState(): UserAccountUi?
    suspend fun fetchQrKey(): String
    suspend fun fetchQrImage(key: String): String
    suspend fun checkQrState(key: String): QrCheckState
    suspend fun refreshLogin(): LoginMutationResult
    suspend fun logout(): LoginMutationResult
    suspend fun sendCaptcha(phone: String, countryCode: String = "86"): LoginMutationResult
    suspend fun verifyCaptcha(phone: String, captcha: String, countryCode: String = "86"): LoginMutationResult
    suspend fun loginCellphone(phone: String, captcha: String? = null, password: String? = null, countryCode: String = "86"): LoginMutationResult
    suspend fun fetchCountryCodeList(): String
}

data class NativeNeteaseCookies(
    val musicU: String = "",
    val csrf: String = "",
    val nmtid: String = "",
)

data class NativeNeteaseHttpResponse(
    val body: String,
    val cookies: List<String> = emptyList(),
)

data class LoginMutationResult(
    val code: Int,
    val body: String,
    val cookieHeader: String = "",
)

sealed interface LoginBackend {
    data object LocalNative : LoginBackend
    data class RemoteApiRoot(val apiRoot: String) : LoginBackend
}

interface NativeNeteaseHttpTransport {
    suspend fun postForm(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String>,
    ): NativeNeteaseHttpResponse
}
```

- [ ] **Step 2: Run RED tests again**

Run:

```powershell
cd android-native
.\gradlew.bat :app:testDebugUnitTest --tests "top.imsyy.splayer.nativeapp.data.repository.NativeNeteaseLoginClientTest" --tests "top.imsyy.splayer.nativeapp.data.repository.SPlayerRemoteRepositoryTest"
```

Expected: FAIL because `NativeNeteaseLoginClient` and the extended login methods are still missing.

- [ ] **Step 3: Keep interface parity explicit**

Do not split out a QR-only interface. If the first UI only exposes QR login, the underlying repository must still expose every method in `NeteaseLoginClient`, because the acceptance target is remote API capability parity, not QR-only login.

## Task 3: Implement Native Netease Crypto

**Files:**

- Create: `android-native/app/src/main/java/top/imsyy/splayer/nativeapp/data/repository/NativeNeteaseCrypto.kt`

- [ ] **Step 1: Re-check desktop protocol modules**

Run:

```powershell
Get-Content node_modules/@neteasecloudmusicapienhanced/api/module/login_qr_key.js
Get-Content node_modules/@neteasecloudmusicapienhanced/api/module/login_qr_create.js
Get-Content node_modules/@neteasecloudmusicapienhanced/api/module/login_qr_check.js
Get-Content node_modules/@neteasecloudmusicapienhanced/api/module/login_status.js
Get-Content node_modules/@neteasecloudmusicapienhanced/api/module/user_account.js
Get-Content node_modules/@neteasecloudmusicapienhanced/api/module/captcha_sent.js
Get-Content node_modules/@neteasecloudmusicapienhanced/api/module/captcha_verify.js
Get-Content node_modules/@neteasecloudmusicapienhanced/api/module/login_cellphone.js
Get-Content node_modules/@neteasecloudmusicapienhanced/api/module/countries_code_list.js
Get-Content node_modules/@neteasecloudmusicapienhanced/api/module/login_refresh.js
Get-Content node_modules/@neteasecloudmusicapienhanced/api/module/logout.js
```

Expected: each file matches the endpoint and payload facts above. If not, edit this plan before implementation.

- [ ] **Step 2: Implement `eapi` and `weapi` helpers**

Add a small object with these functions:

```kotlin
internal object NativeNeteaseCrypto {
    fun eapi(uri: String, payloadJson: String): Map<String, String>
    fun weapi(payloadJson: String, secretKey: String): Map<String, String>
}
```

Implementation requirements:

- AES/CBC/PKCS5Padding for `weapi`
- AES/ECB/PKCS5Padding for `eapi`
- RSA/ECB/NoPadding for `weapi` `encSecKey`
- MD5 message format for `eapi`: `nobody{uri}use{payloadJson}md5forencrypt`
- `eapi` encrypted string must be uppercase hex

- [ ] **Step 3: Add focused crypto tests before implementation**

Create:

`android-native/app/src/test/java/top/imsyy/splayer/nativeapp/data/repository/NativeNeteaseCryptoTest.kt`

Use deterministic `secretKey = "abcdefghijklmnop"` and assert stable properties plus at least one captured desktop-compatible fixture:

```kotlin
assertTrue(NativeNeteaseCrypto.eapi("/api/login/qrcode/unikey", """{"type":3}""").containsKey("params"))
assertTrue(NativeNeteaseCrypto.weapi("{}", "abcdefghijklmnop").containsKey("encSecKey"))
assertEquals(
    "010001",
    NativeNeteaseCrypto.WEAPI_RSA_EXPONENT,
)
```

The test does not need to assert the random secret path. It must assert deterministic output when a fixed secret key is injected.

- [ ] **Step 4: Run targeted tests**

Run:

```powershell
cd android-native
.\gradlew.bat :app:testDebugUnitTest --tests "top.imsyy.splayer.nativeapp.data.repository.NativeNeteaseLoginClientTest"
```

Expected: still FAIL until the client is implemented.

## Task 4: Implement NativeNeteaseLoginClient

**Files:**

- Create: `android-native/app/src/main/java/top/imsyy/splayer/nativeapp/data/repository/NativeNeteaseLoginClient.kt`
- Create: `android-native/app/src/main/java/top/imsyy/splayer/nativeapp/data/repository/NativeQrCodeGenerator.kt`
- Modify: `android-native/app/build.gradle.kts`

- [ ] **Step 1: Implement transport-backed login client**

Constructor:

```kotlin
class NativeNeteaseLoginClient(
    private val cookieProvider: () -> NativeNeteaseCookies,
    private val transport: NativeNeteaseHttpTransport,
    private val qrCodeGenerator: NativeQrCodeGenerator = ZxingNativeQrCodeGenerator(),
    private val secretKeyProvider: () -> String = ::randomSecretKey,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) : NeteaseLoginClient
```

Endpoint constants:

```kotlin
private const val INTERFACE_DOMAIN = "https://interface.music.163.com"
private const val MUSIC_DOMAIN = "https://music.163.com"
```

Required method behavior:

```kotlin
override suspend fun fetchQrKey(): String {
    val body = postEapi("/api/login/qrcode/unikey", """{"type":3}""")
    return parse(body).obj("data").string("unikey").ifBlank { parse(body).string("unikey") }
}

override suspend fun fetchQrImage(key: String): String {
    return qrCodeGenerator.toPngDataUri("https://music.163.com/login?codekey=$key")
}

override suspend fun checkQrState(key: String): QrCheckState {
    val response = postEapiWithResponse("/api/login/qrcode/client/login", """{"key":"$key","type":3}""")
    return QrCheckState(code = parse(response.body).int("code"), cookieHeader = compactCookieHeader(response.cookies))
}

override suspend fun fetchLoginState(): UserAccountUi? {
    val loginStatus = postWeapi("/api/w/nuser/account/get", nativeJsonObject("csrf_token" to cookieProvider().csrf))
    val statusUser = parse(loginStatus).toNativeUser()
    val accountUser = runCatching {
        parse(postWeapi("/api/nuser/account/get", nativeJsonObject("csrf_token" to cookieProvider().csrf))).obj("profile").toNativeUser()
    }.getOrNull()
    return accountUser ?: statusUser
}

override suspend fun refreshLogin(): LoginMutationResult {
    val response = postEapiWithResponse("/api/login/token/refresh", "{}")
    return response.toMutationResult()
}

override suspend fun logout(): LoginMutationResult {
    val response = postEapiWithResponse("/api/logout", "{}")
    return response.toMutationResult()
}

override suspend fun sendCaptcha(phone: String, countryCode: String): LoginMutationResult {
    val body = nativeJsonObject(
        "ctcode" to countryCode,
        "secrete" to "music_middleuser_pclogin",
        "cellphone" to phone,
        "csrf_token" to cookieProvider().csrf,
    )
    return postWeapiWithResponse("/api/sms/captcha/sent", body).toMutationResult()
}

override suspend fun verifyCaptcha(phone: String, captcha: String, countryCode: String): LoginMutationResult {
    val body = nativeJsonObject(
        "ctcode" to countryCode,
        "cellphone" to phone,
        "captcha" to captcha,
        "csrf_token" to cookieProvider().csrf,
    )
    return postWeapiWithResponse("/api/sms/captcha/verify", body).toMutationResult()
}

override suspend fun loginCellphone(phone: String, captcha: String?, password: String?, countryCode: String): LoginMutationResult {
    require(!captcha.isNullOrBlank() || !password.isNullOrBlank()) {
        "手机号登录需要验证码或密码"
    }
    val body = if (!captcha.isNullOrBlank()) {
        nativeJsonObject(
            "type" to "1",
            "https" to "true",
            "phone" to phone,
            "countrycode" to countryCode,
            "captcha" to captcha,
            "remember" to "true",
            "csrf_token" to cookieProvider().csrf,
        )
    } else {
        nativeJsonObject(
            "type" to "1",
            "https" to "true",
            "phone" to phone,
            "countrycode" to countryCode,
            "password" to md5(password.orEmpty()),
            "remember" to "true",
            "csrf_token" to cookieProvider().csrf,
        )
    }
    return postWeapiWithResponse("/api/w/login/cellphone", body).toMutationResult()
}

override suspend fun fetchCountryCodeList(): String {
    return postEapi("/api/lbs/countries/v1", "{}")
}
```

Use repository-local private JSON helpers in this file; do not make `SPlayerRemoteRepository.kt` larger just to share private extensions.

All `transport.postForm()` calls must run inside `withContext(dispatcher)` so native login does not block the main thread and does not create its own executor.

- [ ] **Step 2: Implement request headers**

Headers must include:

```kotlin
mapOf(
    "User-Agent" to "NeteaseMusic 9.0.90/5038 (iPhone; iOS 16.2; zh_CN)",
    "Referer" to "https://music.163.com",
    "Cookie" to buildNativeCookieHeader(cookieProvider(), clock()),
)
```

`buildNativeCookieHeader` must include `MUSIC_U` and `__csrf` only when non-blank, and must include stable device/os values equivalent to desktop defaults without logging secrets.

- [ ] **Step 3: Implement QR generator**

Add dependency in `android-native/app/build.gradle.kts`:

```kotlin
implementation("com.google.zxing:core:3.5.3")
```

Create:

```kotlin
interface NativeQrCodeGenerator {
    fun toPngDataUri(content: String): String
}

class ZxingNativeQrCodeGenerator : NativeQrCodeGenerator {
    override fun toPngDataUri(content: String): String {
        // 生成 512x512 PNG 二维码并返回 data:image/png;base64,...
    }
}
```

The implementation must allocate only inside `toPngDataUri`; do not keep bitmaps or background threads.

- [ ] **Step 4: Run targeted tests to verify GREEN**

Run:

```powershell
cd android-native
.\gradlew.bat :app:testDebugUnitTest --tests "top.imsyy.splayer.nativeapp.data.repository.NativeNeteaseLoginClientTest"
```

Expected: PASS.

## Task 5: Add Login Repository Routing And Keep Remote Repository Focused

**Files:**

- Create: `android-native/app/src/main/java/top/imsyy/splayer/nativeapp/data/repository/NeteaseLoginRepository.kt`
- Modify: `android-native/app/src/main/java/top/imsyy/splayer/nativeapp/data/repository/SPlayerRemoteRepository.kt`
- Modify: `android-native/app/src/main/java/top/imsyy/splayer/nativeapp/di/AppModule.kt`
- Modify: `android-native/app/src/main/java/top/imsyy/splayer/nativeapp/ui/ViewModels.kt`
- Test: `android-native/app/src/test/java/top/imsyy/splayer/nativeapp/data/repository/NeteaseLoginRepositoryTest.kt`

- [ ] **Step 1: Keep `SPlayerRemoteRepository` as remote API implementation**

Do not add native login routing state to `SPlayerRemoteRepository`. Add only remote login methods:

```kotlin
suspend fun refreshLogin(): LoginMutationResult {
    return getNetease("login/refresh", mapOf("timestamp" to now())).toRemoteLoginMutationResult()
}

suspend fun logout(): LoginMutationResult {
    return getNetease("logout", mapOf("timestamp" to now())).toRemoteLoginMutationResult()
}

suspend fun sendCaptcha(phone: String, countryCode: String = "86"): LoginMutationResult {
    return getNetease(
        "captcha/sent",
        mapOf("phone" to phone, "ctcode" to countryCode, "timestamp" to now()),
    ).toRemoteLoginMutationResult()
}

suspend fun verifyCaptcha(phone: String, captcha: String, countryCode: String = "86"): LoginMutationResult {
    return getNetease(
        "captcha/verify",
        mapOf("phone" to phone, "captcha" to captcha, "ctcode" to countryCode, "timestamp" to now()),
    ).toRemoteLoginMutationResult()
}

suspend fun loginCellphone(
    phone: String,
    captcha: String? = null,
    password: String? = null,
    countryCode: String = "86",
): LoginMutationResult {
    val params = linkedMapOf(
        "phone" to phone,
        "ctcode" to countryCode,
        "timestamp" to now(),
    )
    if (!captcha.isNullOrBlank()) {
        params["captcha"] = captcha
    } else {
        require(!password.isNullOrBlank()) { "手机号登录需要验证码或密码" }
        params["password"] = password
    }
    return getNetease("login/cellphone", params).toRemoteLoginMutationResult()
}

suspend fun fetchCountryCodeList(): String {
    return getRaw("netease/countries/code/list", mapOf("timestamp" to now()))
}
```

`toRemoteLoginMutationResult()` must parse `code` and optional body `cookie`. Remote proxy puts login cookies in body `cookie`.
`SPlayerRemoteRepository` already has private `getRaw()` and `getNetease()` helpers; do not make them public just for tests. Test through public methods and fake `SPlayerApiService` calls.

- [ ] **Step 2: Create `NeteaseLoginRepository` router**

Add:

```kotlin
@Singleton
class NeteaseLoginRepository @Inject constructor(
    private val remoteRepository: SPlayerRemoteRepository,
    private val appSettingsStore: AppSettingsStore,
    private val nativeLoginClientProvider: Provider<NeteaseLoginClient>,
) {
    private fun resolveBackend(): LoginBackend {
        val apiRoot = appSettingsStore.currentApiRoot()
        return if (apiRoot.isBlank()) {
            LoginBackend.LocalNative
        } else {
            LoginBackend.RemoteApiRoot(apiRoot)
        }
    }

    suspend fun fetchLoginState(): UserAccountUi? {
        return when (resolveBackend()) {
            LoginBackend.LocalNative -> nativeLoginClientProvider.get().fetchLoginState()
            is LoginBackend.RemoteApiRoot -> remoteRepository.fetchLoginState()
        }
    }
}
```

Add equivalent routing methods for every `NeteaseLoginClient` method. This is the only place that chooses local vs remote.
Every method must call `resolveBackend()` before touching `nativeLoginClientProvider`; never compute `val native = nativeLoginClientProvider.get()` before the `when`.

- [ ] **Step 3: Route SettingsViewModel through NeteaseLoginRepository**

Change constructor:

```kotlin
class SettingsViewModel @Inject constructor(
    private val loginRepository: NeteaseLoginRepository,
    private val appSettingsStore: AppSettingsStore,
    private val lyricsPreferences: LyricsPreferences,
    private val queueRepository: QueueRepository,
) : ViewModel()
```

Replace login calls:

```kotlin
val user = loginRepository.fetchLoginState()
val key = loginRepository.fetchQrKey()
val qrImage = loginRepository.fetchQrImage(key)
val result = loginRepository.checkQrState(key)
```

Do not route discovery, playlist, search, lyric, comments, or external unlock through `NeteaseLoginRepository`.
`HomeViewModel` and `MyViewModel` can continue to call `SPlayerRemoteRepository.fetchLoginState()` only if the UX is allowed to require `apiRoot` there. If local login should update shared account state immediately, route account refresh through `NeteaseLoginRepository` and keep only non-login data on `SPlayerRemoteRepository`.

- [ ] **Step 4: Use true lazy provider DI**

In `AppModule.kt`, keep a normal native client provider:

```kotlin
@Provides
@Singleton
fun provideNativeNeteaseLoginClient(
    okHttpClient: OkHttpClient,
    appSettingsStore: AppSettingsStore,
): NeteaseLoginClient {
    return NativeNeteaseLoginClient(
        cookieProvider = {
            NativeNeteaseCookies(
                musicU = appSettingsStore.settings.value.musicU,
                csrf = appSettingsStore.settings.value.csrf,
                nmtid = appSettingsStore.settings.value.nmtid,
            )
        },
        transport = OkHttpNativeNeteaseHttpTransport(okHttpClient),
    )
}
```

Inject `Provider<NeteaseLoginClient>` into `NeteaseLoginRepository`, not an already-created `NeteaseLoginClient`. Hilt should only create the native client when `nativeLoginClientProvider.get()` is called in blank `apiRoot` mode.
Do not mark `NativeQrCodeGenerator` as `@Singleton`; construct it inside the native login provider or default constructor so remote mode does not allocate it.
Production code must not directly inject `NeteaseLoginClient` anywhere except as `Provider<NeteaseLoginClient>` in `NeteaseLoginRepository`. `NativeNeteaseLoginClient`, `OkHttpNativeNeteaseHttpTransport`, and `ZxingNativeQrCodeGenerator` must only be created after `nativeLoginClientProvider.get()` is invoked.

- [ ] **Step 5: Add routing tests**

Create `NeteaseLoginRepositoryTest.kt`:

```kotlin
@Test
fun `blank api root uses native login provider`() = runBlocking { ... }

@Test
fun `configured api root uses remote repository and does not instantiate native provider`() = runBlocking { ... }

@Test
fun `api root changes are observed on next login call`() = runBlocking { ... }

@Test
fun `remote mode never requests native netease endpoints`() = runBlocking { ... }

@Test
fun `no production class directly injects native login client`() { ... }
```

- [ ] **Step 6: Run repository targeted tests**

Run:

```powershell
cd android-native
.\gradlew.bat :app:testDebugUnitTest --tests "top.imsyy.splayer.nativeapp.data.repository.SPlayerRemoteRepositoryTest" --tests "top.imsyy.splayer.nativeapp.data.repository.NeteaseLoginRepositoryTest"
```

Expected: PASS.

## Task 6: Update Settings UI Text And Docs

**Files:**

- Modify: `android-native/app/src/main/java/top/imsyy/splayer/nativeapp/ui/screen/SettingsScreen.kt`
- Modify: `docs/specs/dev_android_api.md`
- Modify if matrix wording changes: `docs/specs/matrix_android_native_v2.md`

- [ ] **Step 1: Update account card copy**

Change account description from:

```kotlin
description = "Native V2 首阶段继续复用远程 /splayer/* 登录链路"
```

to:

```kotlin
description = "未配置 API 根路径时使用 Android 原生登录；配置后复用外部 /splayer/* 登录链路"
```

- [ ] **Step 2: Update network copy**

In local unlock mode, use:

```kotlin
"当前播放源解析使用原生本地解锁，不加载桌面本地 unlock 服务。本地登录能力与远程登录 API 对齐，包含二维码、验证码、手机号、刷新、登出和国家码；推荐、发现、歌单和搜索仍需要 API 根路径。"
```

- [ ] **Step 3: Add phone login entry if the current Settings UI lacks it**

Desktop exposes QR and phone-code login. Android can keep QR as the first visible path, but feature parity requires a phone-code path reachable from Settings:

```kotlin
OutlinedTextField(value = phoneInput, onValueChange = { phoneInput = it }, label = { Text("手机号") })
OutlinedTextField(value = captchaInput, onValueChange = { captchaInput = it }, label = { Text("验证码") })
Button(onClick = { viewModel.sendCaptcha(phoneInput, countryCodeInput) }) { Text("发送验证码") }
Button(onClick = { viewModel.loginWithCaptcha(phoneInput, captchaInput, countryCodeInput) }) { Text("手机号登录") }
```

Use existing Material Compose components and existing typography; do not add a WebView, browser login page, or Node-backed bridge.

- [ ] **Step 4: Route phone login UI to `NeteaseLoginRepository`**

Add methods to `SettingsViewModel`:

```kotlin
fun sendCaptcha(phone: String, countryCode: String = "86") { ... }
fun loginWithCaptcha(phone: String, captcha: String, countryCode: String = "86") { ... }
fun refreshLogin() { ... }
fun logout() { ... }
fun loadCountryCodes() { ... }
```

`loginWithCaptcha()` must call `verifyCaptcha()` then `loginCellphone()`, update cookies from `LoginMutationResult.cookieHeader`, then refresh account state through `fetchLoginStateWithRetry()`.
`logout()` must call the login repository, then clear stored account and cookies through existing settings-store APIs or add a focused settings-store API with tests.

- [ ] **Step 5: Update API docs**

In `docs/specs/dev_android_api.md`, add:

```markdown
## Android 原生登录

- `apiRoot` 为空时，登录走 Android 原生网易云登录 provider
- 原生登录接口面按 desktop/standalone 登录 API 对齐：二维码、登录态、刷新、登出、短信验证码、手机号登录、国家码列表
- 原生登录只在用户触发登录/刷新/登出时按需请求，不启动 Node、本地 HTTP server 或桌面 runtime
- `apiRoot` 已配置时，登录继续走外部 `API_ROOT/netease/*`，且不实例化原生登录 provider
- 推荐、歌单、搜索、歌词和评论仍属于远程 API 能力，未配置 `apiRoot` 时不主动请求
```

- [ ] **Step 6: Run markdown/text sanity**

Run:

```powershell
rg -n "登录、发现和歌单仍需要 API 根路径|请先在设置中填写 API 根路径" android-native/app/src/main/java docs/specs/dev_android_api.md
```

Expected: the old settings text is gone; repository exception text may remain for non-login remote APIs.

- [ ] **Step 7: Add low-power static guards**

Add tests in `android-native/app/src/test/java/top/imsyy/splayer/nativeapp/data/repository/NativeLoginResourceContractTest.kt`:

```kotlin
@Test
fun `android native login source does not reference node or local server runtime`() {
    val sourceRoot = File("src/main/java/top/imsyy/splayer/nativeapp").walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .joinToString("\n") { it.readText() }

    assertFalse(sourceRoot.contains("ProcessBuilder"))
    assertFalse(sourceRoot.contains("node"))
    assertFalse(sourceRoot.contains("25884"))
    assertFalse(sourceRoot.contains("server/standalone"))
}
```

Add login repository routing test:

```kotlin
@Test
fun `configured api root login methods do not instantiate native provider`() = runBlocking {
    var nativeCreated = 0
    val repository = NeteaseLoginRepository(
        remoteRepository = fakeRemoteLoginRepository,
        appSettingsStore = fakeSettingsStore(apiRoot = "https://api.example.com/splayer"),
        nativeLoginClientProvider = Provider {
            nativeCreated += 1
            FakeNeteaseLoginClient()
        },
    )

    repository.fetchQrKey()

    assertEquals(0, nativeCreated)
}
```

## Task 7: Verification And Push

**Files:**

- No new files unless verification exposes a defect.

- [ ] **Step 1: Run Android unit tests and build**

Run:

```powershell
cd android-native
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run repo-level checks**

Run from repository root:

```powershell
corepack pnpm lint
corepack pnpm format
corepack pnpm build
git diff --check --ignore-space-at-eol
```

Expected: each command exits 0.

- [ ] **Step 3: Verify APK does not contain private API Root**

Run:

```powershell
Select-String -Path android-native\app\build\outputs\apk\debug\app-debug.apk -Pattern "192.9.181.26" -SimpleMatch
Select-String -Path android-native\app\build\outputs\apk\debug\app-debug.apk -Pattern "http://192." -SimpleMatch
```

Expected: no matches.

- [ ] **Step 4: Required ADB install and launch smoke test**

Only run after build success:

```powershell
adb install -r android-native\app\build\outputs\apk\debug\app-debug.apk
adb shell monkey -p top.imsyy.splayer.native -c android.intent.category.LAUNCHER 1
adb logcat -c
Start-Sleep -Seconds 10
adb logcat -d AndroidRuntime:E ActivityTaskManager:I '*:S'
```

Expected: app launches without crash. Do not clear app data unless the user explicitly approves.

- [ ] **Step 5: Required ADB login mode smoke tests**

Without clearing user data:

```powershell
adb shell am start -n top.imsyy.splayer.native/top.imsyy.splayer.nativeapp.MainActivity
adb logcat -c
```

Manual device checks:

- Open Settings with blank API Root; account card must allow QR login and phone-code login without showing “请先在设置中填写 API 根路径”.
- With blank API Root, stay on Settings for 30 seconds without pressing login; no login network request or QR polling should start.
- Start QR login, then cancel; wait 5-10 seconds and inspect logcat or test fake counters for no continued QR polling.
- Save an external API Root; start QR login; remote mode must not log or call `music.163.com` / `interface.music.163.com` native endpoints.
- Save an external API Root; phone-code, refresh and logout paths must continue through `API_ROOT/netease/*` and native provider creation count must remain 0 in tests.
- Local unlock mode remains default when API Root is blank.
- Run `adb shell dumpsys activity services top.imsyy.splayer.native` or an equivalent service dump and confirm this feature did not add a Node/local-server/background login service.
- After a successful real login, restart the app and confirm account state persists; after logout, account state and cookies are cleared.

If a real account login cannot be completed because credentials or SMS are unavailable, state that the UI, routing, endpoint selection, cancellation, and crash checks passed, and mark real credential login as unverified. Do not claim full true-login success without observing it.

- [ ] **Step 6: Commit and push**

Run:

```powershell
git status --short
git add android-native/app/build.gradle.kts android-native/app/src/main/java/top/imsyy/splayer/nativeapp/data/repository/NeteaseLoginClient.kt android-native/app/src/main/java/top/imsyy/splayer/nativeapp/data/repository/NeteaseLoginRepository.kt android-native/app/src/main/java/top/imsyy/splayer/nativeapp/data/repository/NativeNeteaseCrypto.kt android-native/app/src/main/java/top/imsyy/splayer/nativeapp/data/repository/NativeNeteaseLoginClient.kt android-native/app/src/main/java/top/imsyy/splayer/nativeapp/data/repository/NativeQrCodeGenerator.kt android-native/app/src/main/java/top/imsyy/splayer/nativeapp/data/repository/SPlayerRemoteRepository.kt android-native/app/src/main/java/top/imsyy/splayer/nativeapp/di/AppModule.kt android-native/app/src/main/java/top/imsyy/splayer/nativeapp/ui/ViewModels.kt android-native/app/src/main/java/top/imsyy/splayer/nativeapp/ui/screen/SettingsScreen.kt android-native/app/src/test/java/top/imsyy/splayer/nativeapp/data/repository/NativeNeteaseLoginClientTest.kt android-native/app/src/test/java/top/imsyy/splayer/nativeapp/data/repository/NeteaseLoginRepositoryTest.kt android-native/app/src/test/java/top/imsyy/splayer/nativeapp/data/repository/NativeQrCodeGeneratorTest.kt android-native/app/src/test/java/top/imsyy/splayer/nativeapp/data/repository/NativeLoginResourceContractTest.kt android-native/app/src/test/java/top/imsyy/splayer/nativeapp/data/repository/SPlayerRemoteRepositoryTest.kt android-native/app/src/test/java/top/imsyy/splayer/nativeapp/ui/SettingsViewModelLoginTest.kt docs/specs/dev_android_api.md docs/specs/matrix_android_native_v2.md docs/plans/2026-05-03-native-netease-login.md
git commit -m "feat: add native netease login"
git push origin dev
```

Expected: push succeeds and GitHub Actions build the APK/release flow.

## Self-Review Notes

- Spec coverage: covers native login, remote API fallback, no Node runtime, settings copy, docs, tests, build, APK secret scan.
- Placeholder scan: no `TBD` or unresolved future steps are intentionally left.
- Risk boundary: 原生登录覆盖 desktop 当前登录 API 面，不扩大到推荐/歌单/搜索的网易云 API 原生化；这是用户当前要求的最小可测范围。
- Subagent note: 子代理审查已确认不能停留在 QR-only，必须补手机号验证码、国家码、刷新和登出；当前会话已达到新建子代理线程上限，后续实现可复用既有审查结果或在释放线程后继续派工。
