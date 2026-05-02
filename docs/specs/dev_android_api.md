# Android Native V2 API 文档

## 根路径

- `BuildConfig.API_ROOT`
- 默认值：`http://192.9.181.26/splayer`

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

- 远程 `/splayer/*` 服务已承担解锁版本能力
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

### 刷新策略

- 普通页面进入优先使用 30 分钟内的内存缓存
- 冷启动或 App 从后台回前台后，同一前台周期只允许一次自动强刷
- 手动刷新必须走 `forceRefresh=true`
- `forceRefresh=true` 必须绕过本地缓存，并给推荐相关请求附加 `timestamp`
- Home 和 Discovery 之间切换不得重复触发强刷
- 已有内容时不显示整屏下拉刷新动画，只允许局部刷新状态

## 我的页资料合同

### 请求链

1. `netease/login/status`
2. `netease/user/account`，仅在登录状态没有可用 profile 时兜底
3. `netease/user/detail?uid={userId}&timestamp={now}`
4. `netease/likelist?uid={userId}`
5. `netease/user/playlist?uid={userId}&limit=20&offset=0`
6. `netease/album/sublist?limit=20&offset=0&timestamp={now}`
7. `netease/user/record?uid={userId}&type=1&timestamp={now}`，失败时允许静默降级为空

### 字段合同

- `user/detail.profile` 仍是昵称、签名、等级、关注数、粉丝数、听歌数的主来源
- 背景字段按 `backgroundUrl -> backgroundImageUrl -> profileBackgroundUrl` 解析，作为资料字段保留
- 当前已验证 `backgroundUrl` 可能长期返回旧个人主页背景，不能作为“我的”页头图区唯一真值
- “我的”页头图区背景图优先使用已更新的 `avatarUrl`，仅在头像为空时回退到背景字段
- 头图区图片缓存 key 只由实际图片 URL 生成，不得用刷新时间戳或轮询版本号强制失效
- 只有远程返回的头像 URL 或回退背景 URL 发生变化时，图片层才应重新加载

## 音源解析

### 官方源

按顺序尝试：

1. `song/url/v1?level=exhigh`
2. `song/url/v1?level=higher`
3. `song/url/v1?level=standard`

### 解锁源

按顺序尝试：

1. `unblock/netease`
2. `unblock/kuwo`
3. `unblock/gequbao`
4. `unblock/bodian`

所有音源 URL 在进入播放器前统一正规化。

## 长加载合同

- 歌单预览、歌单分页、歌词、热门评论、最新评论都允许走缓存优先
- 我的页喜欢歌单预热与正式进入歌单详情必须共享同一远程请求，不允许并发重复打同一接口
- 播放详情评论预热与正式打开评论抽屉必须共享同一远程请求，不允许并发重复打同一接口
- 歌词预热与歌词页正式显示必须共享同一远程请求，不允许并发重复打同一接口
- 歌单详情优先显示预览和已缓存首屏，再后台补齐分页
- 评论抽屉优先显示缓存和预热结果，再后台补齐热门与最新评论首屏

## 当前边界

- 首阶段不内置本地 API runtime
- 播客相关仓储仍保留在仓库中，但不属于当前听歌主线
- `qqmusic` 端点尚未在 UI 首批功能中消费，但接口根路径已保留
