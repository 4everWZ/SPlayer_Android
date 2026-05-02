# Android Native V2 API 文档

## 根路径

- APK 不内置私有 API 根路径
- 默认 `apiRoot` 为空，用户在 Android 设置页手动填写
- 外部服务器地址必须填写完整根路径，例如 `https://example.com/splayer` 或局域网地址
- 端口按服务实际部署或反代配置填写，客户端不会自动补端口

## 总原则

- API 语义与 `SPlayer desktop` 对齐
- Android 当前主线通过 `remote API` 落地这些语义
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

- Android 不启动桌面端 Node/Electron 本地 unlock 服务
- 默认 `unlockServerMode=LOCAL`，由 Android 原生网络请求按需解析网易云直连解锁候选
- 用户选择外部服务器时切到 `unlockServerMode=EXTERNAL`，请求当前设置页 `apiRoot` 下的 `/unblock/*`
- `apiRoot` 未配置时，登录、推荐、搜索、歌词等远程 API 不发起请求；本地 unlock 仍可按需请求原生直连解锁候选
- 客户端不得再叠加会员可用性判断、广告解锁判断或 VIP 导流逻辑
- 即便远程服务具备解锁能力，客户端仍要保留官方源与多解锁源故障转移链

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

1. `native-netease`，Android 原生按需请求 `https://music-api.gdstudio.xyz/api.php`

`unlockServerMode=EXTERNAL`：

1. `unblock/netease`
2. `unblock/kuwo`
3. `unblock/gequbao`
4. `unblock/bodian`

`unblock/netease` 失败或返回空 URL 时，按 desktop 对齐回退到 Android 原生网易云直连解锁候选。

所有音源 URL 在进入播放器前统一正规化。

## 长加载合同

- 歌单预览、歌单分页、歌词、热门评论、最新评论都允许走缓存优先
- 我的页喜欢歌单预热与正式进入歌单详情必须共享同一远程请求，不允许并发重复打同一接口
- 播放详情评论预热与正式打开评论抽屉必须共享同一远程请求，不允许并发重复打同一接口
- 歌词预热与歌词页正式显示必须共享同一远程请求，不允许并发重复打同一接口
- 歌单详情优先显示预览和已缓存首屏，再后台补齐分页
- 评论抽屉优先显示缓存和预热结果，再后台补齐热门与最新评论首屏

## 当前边界

- 首阶段不内置 Node/Electron 本地 API runtime；本地 unlock 只保留 Android 原生按需网络解析
- 播客相关仓储仍保留在仓库中，但不属于当前听歌主线
- `qqmusic` 端点尚未在 UI 首批功能中消费，但接口根路径已保留
