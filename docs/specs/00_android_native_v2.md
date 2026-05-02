# Android Native V2 总规格

## 定位

Android 端主线固定为 `android-native/` 原生工程，当前阶段只交付 **手机竖屏听歌主线**。

- 技术栈：Kotlin + Jetpack Compose + Hilt + Retrofit + Room + DataStore + Media3
- 接入模式：`remote`
- 服务根路径：用户在 Android 设置页填写，例如 `https://your-domain.com/splayer`
- 业务语义：与 `SPlayer desktop` 对齐
- 旧 `Capacitor` 线：冻结为 legacy，只保留应急修复

## 当前主线范围

### 保留

- 推荐
- 发现
- 我的
- 搜索
- 歌单详情
- 专辑详情
- 播放详情
- 歌词
- 评论抽屉
- 播放队列
- 登录
- 设置

### 移出主线

- 播客
- 听书
- 免费听
- 社交关注
- 笔记
- 消息
- 云贝
- 装扮
- 商城
- 票务
- VIP 导流
- 广告位
- 直播
- 短视频
- AI 调音
- Automix
- 小灯泡
- MV 入口

## 外部对齐基线

这轮对齐的是 **网易云音乐手机竖屏听歌主路径**，不是全产品全商业化形态。

- 官方产品页：网易云音乐 App Store 中国页  
  <https://apps.apple.com/cn/app/%E7%BD%91%E6%98%93%E4%BA%91%E9%9F%B3%E4%B9%90-%E6%95%B0%E4%BA%BF%E9%9F%B3%E4%B9%90%E7%95%85%E5%90%AC/id590338362>
- 2025-01-28 导航与首页收口报道：新浪科技  
  <https://finance.sina.com.cn/tech/roll/2025-01-28/doc-inehpimx7593076.shtml>
- 2024-07-04 播放器样式公开稿：搜狐  
  <https://www.sohu.com/a/790752858_121124367>

## 信息架构

- 底部导航固定为：`推荐 / 发现 / 我的`
- 首页左上角抽屉是 **唯一** 设置收口入口
- 抽屉只保留：
  - 账号头部
  - 喜欢的音乐
  - 最近播放
  - 收藏歌单
  - 设置
- 小播放条常驻于底部导航上方
- 播放详情、歌词、评论、队列继续沿 Native V2 播放主链收口

## 体验红线

- 不允许底部导航继续出现 `播客`
- 不允许抽屉出现 VIP、消息、云贝、装扮、商城、票务、云推歌等非听歌入口
- 不允许首页混入听书、免费听、会员专区、票务和广告位
- 不允许“我的”页再出现播客、笔记、社交和非听歌服务卡
- 不允许播放器出现左右滑页
- 不允许歌词页顶部重复出现第二个歌曲头部
- 不允许评论缺席
- 不允许缓冲阶段系统时间轴继续自己走表
- 不允许设置继续散落在底部一级导航或“我的”页主内容中

## 当前阶段目标

- 用三栏听歌壳替换当前旧四栏壳
- 用音乐频道化推荐页替换通用卡片堆叠
- 把发现页固定为音乐浏览页
- 把“我的”页固定为音乐资产页
- 小播放条继续网易云化，但不引入商业化次级入口
- 文档、矩阵、smoke test 与当前实现保持一致

## 非目标

- 这轮不做 `embedded` 本地 API runtime
- 这轮不承接播客和听书主路径
- 这轮不复刻网易云品牌素材
- 这轮不接入会员、广告和社交体系
- 这轮不把真机功耗结论伪装成模拟器结论

## 当前代码入口

- 原生工程：[`android-native/`](../../android-native)
- 架构文档：[`docs/design/android_native_architecture.md`](../design/android_native_architecture.md)
- API 文档：[`docs/specs/dev_android_api.md`](./dev_android_api.md)
- 播放器文档：[`docs/specs/dev_android_player.md`](./dev_android_player.md)
- UI 文档：[`docs/specs/dev_android_ui.md`](./dev_android_ui.md)
- 实现矩阵：[`docs/specs/matrix_android_native_v2.md`](./matrix_android_native_v2.md)
- 集成验证：[`docs/specs/integration_android_native_v2.md`](./integration_android_native_v2.md)

## 当前结论

Native V2 正在按网易云手机竖屏听歌主路径收口中。

- 当前不能写成“已对齐网易云”
- 当前可以写成“三栏听歌版壳层已落地，推荐 / 发现 / 我的 主结构已切换”
