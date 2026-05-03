# Android 歌词低功耗同步计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在移动端功耗优先的前提下，让 Android Native 歌词同步接近 desktop 的实时 seek 体验。

**Architecture:** 短期采用歌词页可见时的低频高响应轮询：歌词页 `160ms`，非歌词页 `900ms`，后台 `2000ms`。显示层允许小幅提前补偿，但不改变播放器真实进度、通知栏进度或 seek 语义。长期再把普通逐句歌词切到按下一句边界调度，只在逐字歌词时维持响应 cadence。

**Tech Stack:** Kotlin、Jetpack Compose、Media3、JUnit、Gradle。

---

## 已执行范围

- [x] 对照 desktop：desktop 全屏歌词用 `useRafFn` 读取 `player.getSeek() + songOffset`，不是低频 store 进度。
- [x] 确认 Android 慢约 `0.5s` 的直接原因：歌词页普通 LRC 使用 `900ms` UI 进度 cadence。
- [x] Android 短期方案：歌词页可见时使用 `160ms` cadence，非歌词页仍保持 `900ms`。
- [x] Android 显示层增加 `500ms` 歌词提前量，只影响歌词索引、高亮和自动居中。

## 方案 1 TODO：边界调度

- [ ] **Step 1: 写下一句边界计算测试**

文件：`android-native/app/src/test/java/top/imsyy/splayer/nativeapp/ui/screen/PlayerScreenSupportTest.kt`

目标行为：给定当前显示时间和歌词列表，返回下一句开始时间距离现在的 delay；没有下一句时返回 `null`。

- [ ] **Step 2: 实现纯函数 helper**

文件：`android-native/app/src/main/java/top/imsyy/splayer/nativeapp/ui/screen/PlayerScreen.kt`

建议函数：

```kotlin
internal fun resolveNextLyricBoundaryDelayMs(
    lyrics: List<LyricLineUi>,
    displayPositionMs: Long,
): Long?
```

约束：最小 delay 要有下限，避免 seek 后立即密集循环。

- [ ] **Step 3: 把普通逐句歌词切到边界调度**

文件：`android-native/app/src/main/java/top/imsyy/splayer/nativeapp/ui/screen/PlayerScreen.kt`

普通 LRC 自动跟随态只在下一句边界附近触发重算和居中；逐字歌词仍走 `160ms` cadence。

- [ ] **Step 4: 处理生命周期边界**

必须覆盖：

- 切歌
- seek
- 暂停 / 恢复
- buffering
- 退出歌词页
- 手动预览态

- [ ] **Step 5: 验证功耗假设**

用真机比较：

- 普通 LRC 歌词页 1 分钟 wakeup 次数
- 逐字歌词页 1 分钟 wakeup 次数
- 封面页 1 分钟 wakeup 次数

验收目标：普通逐句歌词页比 `160ms` 固定轮询显著降低 wakeup，且行切换不再慢半秒。
