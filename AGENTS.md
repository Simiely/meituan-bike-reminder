# AGENTS.md · 项目规则

> 给 AI / 未来的你：只记代码里看不出的关键信息。详细问题记录见 [DEVELOPMENT.md](DEVELOPMENT.md)（8 条踩坑 + 完整版本日志）。

## 技术栈

- 原生 Android（Kotlin），单 Activity + BroadcastReceiver 极简结构（MainActivity.kt / TimerReceiver.kt）
- JDK 17+ / Android SDK platform 34；R8 混淆开启；APK Signature Scheme v2
- 核心逻辑：启动系统倒计时（ACTION_SET_TIMER，SKIP_UI）+ 延时 1000ms + 打开美团扫一扫（imeituan:// scheme）
- 目标设备：小米澎湃 OS3（HyperOS 3），Android 7.0+

## 关键坑

1. **签名密钥绝不入库**——keystore.properties（.gitignore）+ 环境变量 KEYSTORE_* 注入；历史泄露的密钥已作废，重新部署必须 keytool 自建
2. **Android 10+ 后台启动限制（BAL）**——从后台拉起其他 App 的 Activity 会被静默拦截，必须用带后台启动授权的 `PendingIntent`（FLAG_ALLOW_BACKGROUND_ACTIVITY_STARTS / API34 发送方 ActivityOptions / API35 创建方 mode）
3. **Android 13+ 通知权限是运行时危险权限**——光 Manifest 声明 `POST_NOTIFICATIONS` 不够，必须在用前 `requestPermissions`，否则提醒静默失效
4. **精确闹钟声明权限 ≠ 用对 API**——`SCHEDULE_EXACT_ALARM` 声明后还要 `canScheduleExactAlarms()` 判断 + `setExactAndAllowWhileIdle()`，否则厂商省电策略下延迟
5. **singleTask 重进前台会重复执行**——`onNewIntent` 会重跑核心动作；用 `SharedPreferences` 记发车时间戳 + `isRideActive()` 幂等判断，骑行中只显示进行中不再发车
6. **通知点击别复用会发车的 Activity**——提醒通知不设 `contentIntent`，只 `setAutoCancel(true)` 点击消音
7. **Android 11 包可见性限制**——`resolveActivity()` 返回 null，Manifest 必须加 `<queries>` 声明（ACTION_SET_TIMER / imeituan scheme / 美团包名）
8. **同一帧多个 startActivity 会互相覆盖**——第二个任务用 `postDelayed` 延迟 1000ms

## 约定

- URL Scheme 参数越少越好（`openAR=1` 曾致相机初始化失败）
- 图标用纯 PNG mipmap，不做自适应图标 XML（跨 ROM 最可靠）
- 冷启动美团扫码黑屏 = 预览渲染问题（非相机采集），解法：预热进程 + PendingIntent 授权
- `allowBackup="false"`（轻量工具 App 不依赖系统备份）

## 常用命令

- 构建：`cd MeiTuanOneTap && ./gradlew assembleRelease`
- 发布：`gh release create vX.Y.Z --title ... --notes ... path/to/apk`
- 版本号：`MeiTuanOneTap/app/build.gradle.kts`（versionCode/versionName）
- 签名：`keytool -genkeypair -v -keystore release.keystore -keyalg RSA -keysize 2048 -validity 9125 -alias meituan_bike`
