# SPlayer Capacitor Android 实施任务列表

- [x] 1. 环境准备和变量配置
  - [x] 确认并设置 `ANDROID_HOME` 环境变量
  - [x] 安装 Capacitor 相关依赖: `@capacitor/core`, `@capacitor/cli`, `@capacitor/android`, `@capacitor/share`
  - [x] 配置 `package.json` 的 mobile build script
- [x] 2. Capacitor 工程初始化
  - [x] 初始化 `capacitor.config.ts` (应用包名 `top.imsyy.splayer`)
  - [x] 执行 `npx cap add android` 同步至 Android 项目
- [ ] 3. P0 页面 移动端适配
  - [ ] 修改基础样式 `env(safe-area-inset-*)`
  - [ ] 为所有 P0 核心页面（首页、搜索、登录、播放、设等）添加移动端 UI 支持，取消横滑
- [ ] 4. 平台 API 桥接
  - [ ] 增加 `src/utils/platform.ts` 用于分享等操作的平滑降级
- [ ] 5. 测试与验证
  - [ ] 本地利用 `pnpm build:mobile` 编译检查
  - [ ] 构建 apk (或在模拟器运行) 并做界面验证
