# 📦 开发文档 — 扫完记得还

> 面向开发者。记录技术架构、关键决策和踩过的坑。
> 使用说明见 [README.md](./README.md)。

---

## 技术架构

### 核心流程

```
App 启动 → 已初始化？
              ├─ 否 → 引导页，手动触发
              └─ 是 → 自动执行
                        ① 启动倒计时（SKIP_UI）
                        ② 延时 1000ms
                        ③ 打开美团扫一扫
```

### 倒计时 — 三层兜底

```
ACTION_SET_TIMER (EXTRA_SKIP_UI)     ← 首选，系统原生，无弹窗
        ↓ 失败
AlarmManager + BroadcastReceiver     ← 次选，应用层计时
        ↓ 仍失败
提示用户手动设置                      ← 最后兜底
```

### 美团扫一扫 — 多级尝试

```
imeituan://www.meituan.com/scanQRCode   ← 通用扫码页（最稳定）
imeituan://www.meituan.com/bike/scan    ← 单车专用扫码页
imeituan://platformapi/startapp         ← 平台入口兜底
        ↓ 全部失败
包名启动美团主页
        ↓ 仍失败
引导用户安装美团
```

### 关键技术细节

| 要点 | 说明 |
|---|---|
| 延时 | 倒计时和美团之间隔 1000ms，避免 Activity 切换时相机初始化竞争 |
| 签名 | v2 签名方案，有效期 100000 天 |
| 混淆 | R8 开启，保留 MainActivity 和 TimerReceiver |
| 包可见性 | Manifest `<queries>` 声明 `ACTION_SET_TIMER` 和 `imeituan` Scheme |
| 图标 | 纯 mipmap PNG（mdpi~xxxhdpi），无自适应图标 XML 层 |

---

## 关键版本日志

### v2.6.0 — 更名「扫完记得还」

App 名从「一键骑车」改为「扫完记得还」。文案同步优化。

### v2.5.0 — 图标方案简化

> **教训：不要过度设计图标。**

删除自适应图标 XML，仅保留 PNG。之前折腾了三次都没成功——`@color/transparent` 不是 drawable、双图叠加、空 layer-list 在某些 ROM 上不工作。最朴素的 PNG 反而是最可靠的。

### v2.4.0 — 图标替换 + 配色 + 签名

替换用户提供的液态玻璃风格图标，生成全分辨率 mipmap。配色从橙红 `#FF6D00` 改为玫瑰粉 `#E55D6B`。新增 v2 签名，有效期 274 年。

### v2.3.0 — 首次启动权限优化

> **教训：权限申请是异步的，不要假设用户会立刻响应。**

`SharedPreferences` 标记初始化状态。首次显示引导页，等用户手动触发后再执行。后续启动直接自动执行。解决了权限弹窗打断流程的问题。

### v1.8.0 — 两个 startActivity() 不能在同一帧

> **关键突破：同一帧多个 startActivity 会互相覆盖。**

倒计时成功后立即 `return`，美团用 `postDelayed` 延迟启动。两个任务在时间上分离，互不干扰。

```kotlin
startActivity(skipIntent)
scheduleMeituan(500)  // 后来增至 1000ms
return
```

### v1.7.0 — 美团扫一扫黑屏

> **教训：URL Scheme 参数越少越好。**

`openAR=1` 在 HyperOS 3 上导致相机初始化失败。去掉所有非必要参数，用最简 Scheme。

### v1.5.0 — 包可见性限制

> **整个项目最关键的技术突破。**

Android 11 引入包可见性限制，`resolveActivity()` 返回 null，导致 `startActivity()` 抛异常。Manifest 加 `<queries>` 声明解决。

```xml
<queries>
    <intent><action android:name="android.intent.action.SET_TIMER" /></intent>
    <intent><action android:name="android.intent.action.VIEW" />
            <data android:scheme="imeituan" /></intent>
    <package android:name="com.sankuai.meituan" />
</queries>
```

### v1.1.0~v1.2.0 — Foreground Service 闪退（废弃）

> **教训：Android 14 的 Foreground Service 对第三方 App 几乎不可用。**

放弃 Foreground Service，改用 AlarmManager + BroadcastReceiver。系统时钟不可用时优先选 AlarmManager。

---

## 构建

```bash
cd MeiTuanOneTap
./gradlew assembleRelease
# 输出：app/build/outputs/apk/release/meituan-bike-reminder-v2.6.0.apk
```

版本号在 `MeiTuanOneTap/app/build.gradle.kts`：
```kotlin
versionCode = 260
versionName = "2.6.0"
```

## 发布

```bash
gh release create vX.Y.Z --title "标题" --notes "说明" path/to/apk
```
