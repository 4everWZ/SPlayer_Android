# Android Native V2 API 文档

## 根路径

- APK 不内置私有 API 根路径
- 默认 `apiRoot` 为空，用户在 Android 设置页手动填写
- 外部服务器地址必须填写完整根路径，例如 `https://example.com/splayer` 或局域网地址
- 端口按服务实际部署或反代配置填写，客户端不会自动补端口

## 总原则

- API 语义与 `SPlayer desktop` 对齐
- Android 默认走“本地 API”模式：不启动 Node/Electron，不启动本地 HTTP server，而是用 Kotlin + OkHttp 按需直连网易云接口
- 用户显式切到“远程 API”模式时，才通过设置页 `apiRoot` 请求外部 `${apiRoot}/netease/*`、`${apiRoot}/unblock/*`
- 原生端不额外引入一套与桌面不同的接口解释、错误语义或播放判定规则

## 子接口

- `netease/*`
- `unblock/*`
- `qqmusic/*`

## 当前已接入能力

- 登录状态
- 二维码登录
- 推荐歌单
- 发现页频道数据
- 我的页账号资料、歌单与喜欢的音乐摘要
- 歌单详情
- 搜索默认词
- 热搜
- 搜索歌曲
- 歌词
- 热门评论
- 最新评论
- 官方音源
- 解锁音源

## 服务前提

- Android 不启动桌面端 Node/Electron 本地 unlock/API 服务
- 默认 `unlockServerMode=LOCAL`，产品语义为“本地 API 模式”
- 本地 API 模式下，二维码登录、登录状态、推荐、发现、我的、歌单、搜索、歌词、评论、心动模式和官方音源请求都走 `NativeNeteaseApiClient`
- 远程 API 模式下，请求当前设置页 `apiRoot` 下的 `/netease/*` 和 `/unblock/*`
- 远程 API 模式且 `apiRoot` 为空时，提示“远程 API 模式需要先填写 API 根路径，或切回本地 API 模式登录/使用”
- 本地 API 模式下，解锁候选也与远程 API 模式保持同序，并按 desktop 默认启用优先级尝试：`bodian -> gequbao -> netease -> kuwo`
- 本地解锁候选由 `NativeUnblockApiClient` 原生实现 desktop local provider，不启动 Node 服务；公开第三方 provider URL 会进入 APK，但不得写入用户私有 API Root、局域网 IP 或个人反代域名
- 设置项“允许与其他应用同时播放”默认关闭；关闭时播放器请求音频焦点，开启时不处理音频焦点以允许混播
- 客户端不得再叠加会员可用性判断、广告解锁判断或 VIP 导流逻辑
- 即便远程服务具备解锁能力，客户端仍要保留官方源与多解锁源故障转移链

## 登录链路

- 设置页登录沿用二维码链路
- 本地 API 模式下，首次安装无需配置 API Root 即可生成二维码、轮询扫码状态并获取登录状态
- 远程 API 模式下，必须先配置 API Root 才能调用外部二维码接口
- Android Native 不实现手机号、验证码或国家码登录
- 设置页初始化不自动请求登录端点；只有点击二维码登录时才请求
- 设置页二维码以更大固定尺寸居中展示，不能随卡片宽度被压成过小预览
- 二维码轮询由设置页可见状态持有，取消登录、二维码过期、登录成功或 ViewModel 清理时必须停止
- LOCAL 模式登录走 `NativeNeteaseApiClient`
- REMOTE 模式登录走外部 `API_ROOT/netease/*`
- 未登录但 API 模式可用时，页面提示“请先登录网易云账号”，不误导用户填写 API Root

## Cookie 合同

请求统一通过 `OkHttp` 拦截器写入：

- `MUSIC_U`
- `__csrf`
- `NMTID`

响应里的 `Set-Cookie` 会回写到 `DataStore`，回写策略必须满足：

- 只用非空响应值覆盖已有值
- 缺失字段不能把已有 cookie 清空
- 登出只能走显式 `clearAccount()`，不能依赖空响应副作用

## 二维码登录状态机

### 请求链

1. `netease/login/qr/key`
2. `netease/login/qr/create`
3. 轮询 `netease/login/qr/check`

### 状态定义

- `800`：二维码已过期
- `801`：等待扫码
- `802`：已扫码，等待手机确认
- `803`：登录成功

### 登录完成合同

`qr/check == 803` 后，只有完成以下三件事才算登录完成：

1. cookie 已写入持久层
2. 重新拉取 `login/status`
3. UI 账户卡与首页登录态同步刷新

### 刷新策略

- `login/status` 必须允许短时重试
- 若首轮拉取为空，不得立即把 UI 回退成未登录
- 账户信息以持久层和远程状态共同收敛

## 推荐页刷新合同

推荐页和发现页共用 `DiscoveryHomeUi` 数据源，避免两个页面各自持有旧缓存。

### 数据来源

- `netease/personalized?limit=12`
- `netease/recommend/songs`
- `netease/top/song?type=0`
- `netease/top/artists?limit=12`
- `netease/album/new`
- `netease/toplist/detail`
- `netease/homepage/block/page?refresh=true`，仅 `forceRefresh=true` 时请求，成功时优先承接推荐歌单、推荐歌曲和新碟块

### 刷新策略

- 普通页面进入优先使用 30 分钟内的内存缓存
- 冷启动或 App 被系统重建后允许一次自动强刷；锁屏解锁、底部 tab 切换和短时间回前台不得触发自动强刷
- 手动刷新必须走 `forceRefresh=true`
- `forceRefresh=true` 必须绕过本地缓存，并给推荐相关请求附加 `timestamp`
- `homepage/block/page` 失败或返回空块时必须回退到 desktop 当前稳定接口组合，不能让推荐页空白
- Home 和 Discovery 之间切换不得重复触发强刷
- 已有内容时不显示整屏下拉刷新动画，只允许局部刷新状态

## 我的页资料合同

### 请求链

1. `netease/login/status`
2. `netease/user/account`
3. `netease/user/detail?uid={userId}&timestamp={now}`
4. `netease/likelist?uid={userId}`
5. `netease/user/playlist?uid={userId}&limit=20&offset=0`
6. `netease/album/sublist?limit=20&offset=0&timestamp={now}`
7. `netease/user/record?uid={userId}&type=1&timestamp={now}`，失败时允许静默降级为空

### 字段合同

- `user/detail.profile` 仍补充昵称、签名、等级、关注数、粉丝数、听歌数
- 背景字段按 `backgroundUrl -> backgroundImageUrl -> profileBackgroundUrl` 解析，作为资料字段保留
- “我的”页头图区背景按当前用户要求直接使用 `avatarUrl`
- 资料背景字段当前不作为头图区真值，避免继续显示网易云接口返回的旧背景
- 头图区图片缓存 key 只由实际图片 URL 生成，不得用刷新时间戳或轮询版本号强制失效
- 只有头像 URL 发生变化时，头图区图片层才应重新加载

## 音源解析

### 官方源

按顺序尝试：

1. `song/url/v1?level=exhigh`
2. `song/url/v1?level=higher`
3. `song/url/v1?level=standard`

如果官方接口返回 `freeTrialInfo`，默认视为试听 URL，不作为最终播放源，继续走解锁链路。

### 解锁源

`unlockServerMode=LOCAL`：

1. `/netease/song/url/v1` 由 `NativeNeteaseApiClient` 原生请求网易云官方接口
2. `NativeUnblockApiClient` 按 `bodian -> gequbao -> netease -> kuwo` 原生请求 desktop local 同款 provider

`unlockServerMode=EXTERNAL`：

1. `unblock/bodian`
2. `unblock/gequbao`
3. `unblock/netease`
4. `unblock/kuwo`

无论 LOCAL 还是 EXTERNAL，任一候选失败或返回空 URL 时都继续尝试下一个候选。LOCAL/EXTERNAL 的候选顺序和返回语义必须一致，差异只在 provider 执行位置。

所有音源 URL 在进入播放器前统一正规化。

## 长加载合同

- 歌单预览、歌单分页、歌词、热门评论、最新评论都允许走缓存优先
- 我的页喜欢歌单预热与正式进入歌单详情必须共享同一远程请求，不允许并发重复打同一接口
- 播放详情评论预热与正式打开评论抽屉必须共享同一远程请求，不允许并发重复打同一接口
- 歌词预热与歌词页正式显示必须共享同一远程请求，不允许并发重复打同一接口
- 歌单详情优先显示预览和已缓存首屏，再后台补齐分页
- 当前播放队列来自同一歌单时，后台分页补齐必须同步扩展播放列表，不能要求用户重新点歌
- 评论抽屉优先显示缓存和预热结果，再后台补齐热门与最新评论首屏

## 当前边界

- 首阶段不内置 Node/Electron 本地 API runtime；本地 API 与本地 unlock 都保留 Android 原生按需网络解析
- 播客相关仓储仍保留在仓库中，但不属于当前听歌主线
- `qqmusic` 当前仅作为歌词增强 fallback，不属于播放 unlock 候选；LOCAL 模式下该 fallback 仍可静默降级为空，不阻断网易云歌词、TTML 和播放解锁主链路
