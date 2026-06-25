# 📦 开发文档 — 倒计时 扫车

> 本文档面向开发者，包含项目结构、技术架构、开发日志和关键技术问题记录。
> 普通用户请查看 [README.md](./README.md) 了解使用说明。

---

## 项目结构

```
├── README.md                       # 用户使用说明
├── DEVELOPMENT.md                  # 本文件（开发文档）
├── PROJECT_STATUS.md               # 项目进度摘要
├── project-assets/                 # 项目资产目录（源码备份）
└── MeiTuanOneTap/                 # Android App 源码
    ├── app/
    │   ├── build.gradle.kts       # versionCode=260, versionName="2.6.0"
    │   ├── proguard-rules.pro     # 混淆规则
    │   └── src/main/
    │       ├── AndroidManifest.xml # <queries> 声明，权限
    │       ├── java/com/meituan/onetap/
    │       │   ├── MainActivity.kt  # 主逻辑（倒计时 + 美团扫一扫）
    │       │   └── TimerReceiver.kt # AlarmManager 兜底提醒广播
    │       └── res/
    │           ├── layout/activity_main.xml  # 首页布局
    │           ├── values/colors.xml         # 配色（橙红色系）
    │           ├── values/strings.xml        # 文案（含 App 名称）
    │           ├── values/themes.xml         # App 主题
    │           ├── drawable/                 # 图标矢量图
    │           └── mipmap-anydpi-v26/        # Adaptive Icon
    ├── build.gradle.kts        # 项目级构建配置
    ├── settings.gradle.kts     # 模块声明
    ├── gradle.properties       # Gradle 配置
    └── gradlew                 # Gradle Wrapper
```

---

## 技术架构

### 倒计时实现优先级

```
尝试 ACTION_SET_TIMER (EXTRA_SKIP_UI)
        ↓ 失败
AlarmManager + BroadcastReceiver 兜底
        ↓ 仍失败
提示用户手动设置
```

### 美团打开实现优先级

```
尝试 imeituan:// Scheme（多个变体）
        ↓ 全部失败
尝试包名启动美团主页
        ↓ 仍失败
引导用户安装美团
```

### 执行模式

```
App 启动
    ↓
已初始化？
    ├── 否 → 显示引导页，等待用户手动点击
    └── 是 → 自动执行
                ↓
          ① 启动倒计时（SKIP_UI，无确认）
                ↓ 成功
          ② 延迟 500ms 启动美团
                ↓
          执行完毕
```

---

## 开发日志

### v2.6.0 — 更名「扫完记得还」+ 文案优化

**日期：** 2025-07

**变更内容：**
- App 名称从「一键骑车」正式更名为「扫完记得还」
- Manifest 改用 `@string/app_name` 引用，避免硬编码
- 更新所有 UI 文案与 App 名称风格统一
- 版本号 v2.6.0 → v2.6.0

---

### v2.6.0 — 图标方案简化

**日期：** 2025-07

**变更内容：**
- 彻底移除了 `mipmap-anydpi-v26/` 自适应图标定义
- 删除 `drawable/transparent.xml` 调试遗留文件
- 仅保留各分辨率 mipmap PNG 图标（mdpi ~ xxxhdpi）
- Android 8+ 无自适应图标时直接使用 PNG，解决图标不显示问题

---

### v2.4.0 — 图标与配色更新 + 签名优化

**日期：** 2025-07

**变更内容：**
- 替换App图标为用户提供的液态玻璃风格图标（1920×1920px）
- 生成Android标准各分辨率mipmap资源（mdpi ~ xxxhdpi）
- 更新自适应图标XML（Android 8+）指向新图标
- 配色从橙红色系（`#FF6D00`）调整为玫瑰粉色系（`#E55D6B`），与图标风格一致
- 生成release签名密钥库（有效期100000天 ≈ 274年），APK使用v2签名方案签名
- 优化ProGuard保留规则，确保TimerReceiver不被混淆
- 修复自适应图标显示：foreground 改为透明色，避免双图叠加导致图标异常

---

### v2.3.0 — 首次启动权限优化 + 图标与配色更新

**日期：** 2025-07

**问题：** 首次安装启动时，系统弹出权限申请弹窗，App 未等待用户授权就继续执行，导致倒计时和美团打开均失败。

**解决方案：**
- 引入 `SharedPreferences` 标记初始化状态（`KEY_INITIALIZED`）
- 首次启动 → 显示引导页，用户手动点击按钮触发
- 用户可以在弹窗出现时从容授权，授权后流程正常执行
- 执行成功后标记 `initialized = true`
- 后续启动 → 自动执行，无需任何手动操作

**关键代码：**
```kotlin
if (prefs.getBoolean(KEY_INITIALIZED, false)) {
    onStartRiding() // 已初始化 → 自动执行
} else {
    showWelcome()    // 首次 → 显示引导页
}
```

**图标与配色更新：**
- 替换App图标为用户提供的液态玻璃风格图标（1920×1920px PNG）
- 生成Android标准各分辨率mipmap资源（mdpi ~ xxxhdpi）
- 更新自适应图标XML（Android 8+）指向新图标
- 配色从橙红色系（`#FF6D00`）调整为玫瑰粉色系（`#E55D6B`），与图标风格一致

**签名与构建修复：**
- 生成release签名密钥库，APK使用v2签名方案签名
- 优化R8/ProGuard保留规则，确保TimerReceiver不被混淆

---

### v2.2.0 — 后台恢复 + 按钮重复执行修复

**问题 1：** App 在后台时点击图标，会回到首页而非自动执行。

**问题 2：** 执行完后按钮无法再次触发。

**解决方案：**
- 添加 `onNewIntent()` 回调，处理 `singleTask` 模式下后台切回的场景
- 在 `onNewIntent()` 和按钮点击时重置 `hasLaunched = false`
- 美团 Intent 添加 `FLAG_ACTIVITY_CLEAR_TOP`，确保美团在后台时也能正确激活扫一扫界面

---

### v2.0.0 — 自动执行模式

**问题：** 每次都需要手动点击按钮才能触发，不够便捷。

**解决方案：**
- 移除"设置"面板（NumberPicker 时长选择），固定 50 分钟
- App 启动即自动执行两个任务
- 执行完毕后自动 finish()，不留在界面上

> 后因用户体验问题（无法取消/重设时长），在 v2.2.0 中重新引入了手动触发机制。

---

### v1.8.0 — 两个任务冲突问题（关键突破）

**问题：** 倒计时和打开美团两个 `startActivity()` 在同一帧执行，互相覆盖，只有一个能成功。

**尝试过的失败方案：**
1. `startActivities()` — 第二个 Intent 不生效
2. 顺序执行两个 `startActivity()` — 仍互相覆盖

**最终解决方案：**
- 倒计时成功后**立即 return**，不再执行后续代码
- 美团启动通过 `handler.postDelayed(500ms)` 延迟执行
- 两个任务在**时间上分离**，互不干扰

**关键代码：**
```kotlin
// ① 先启动倒计时
startActivity(skipIntent)
scheduleMeituan(500) // 500ms 后启动美团
return                // 立即 return，不继续执行
```

---

### v1.7.0 — 美团扫一扫黑屏问题

**问题：** 美团扫一扫界面能打开，但相机黑屏，无法扫码。

**根因：** URL Scheme 中携带了 `openAR=1` 参数，在 HyperOS 3 上导致相机初始化失败。

**解决方案：** 移除 `openAR=1` 参数，使用纯净的 `imeituan://www.meituan.com/scanQRCode`。

---

### v1.5.0 — ACTION_SET_TIMER "找不到可处理的 App"

**问题：** 调用 `AlarmClock.ACTION_SET_TIMER` 时系统提示"没有找到可处理此操作的应用"，直接闪退。

**根因：** Android 11（API 30）引入了**包可见性限制（Package Visibility）**，App 默认无法查询其他 App 的 Activity，导致 `resolveActivity()` 返回 `null`，`startActivity()` 直接抛异常。

**解决方案：** 在 `AndroidManifest.xml` 中添加 `<queries>` 声明：
```xml
<queries>
    <!-- 系统时钟倒计时 -->
    <intent>
        <action android:name="android.intent.action.SET_TIMER" />
    </intent>
    <!-- 美团 URL Scheme -->
    <intent>
        <action android:name="android.intent.action.VIEW" />
        <data android:scheme="imeituan" />
    </intent>
    <!-- 美团包名兜底 -->
    <package android:name="com.sankuai.meituan" />
</queries>
```

> **这是整个项目中最关键的技术突破**，解决了 Android 11+ 上的核心兼容性问题。

---

### v1.1.0 ~ v1.2.0 — Foreground Service 闪退（废弃方案）

**问题：** 在 HyperOS 3（Android 14）上，使用 Foreground Service 兜底方案时 App 直接闪退。

**尝试过的修复：**
1. 添加 `foregroundServiceType="specialUse"` + `<property>` 声明 → 仍闪退
2. 移除 `specialUse`，改用普通 Foreground Service → 仍闪退

**最终决策：** 彻底放弃 Foreground Service 方案，改用 `AlarmManager` + `BroadcastReceiver` 兜底。

**经验教训：** Android 14 对 Foreground Service 的限制极其严格，在第三方 App 上很难稳定使用。系统时钟不可用时应优先选择 `AlarmManager` 而非 Foreground Service。

---

### v1.0.0 — 初始版本

- 基础功能：点击按钮 → 启动倒计时 + 打开美团
- 使用 `AlarmManager` 兜底方案（当时还未发现 `ACTION_SET_TIMER` + `EXTRA_SKIP_UI` 的用法）

---

## 关键技术问题记录

### 问题 1：Android 11+ 包可见性导致 `ACTION_SET_TIMER` 失败

| | |
|---|---|
| **现象** | 调用系统时钟 `ACTION_SET_TIMER`，系统提示"找不到可处理的 App" |
| **根因** | Android 11 引入包可见性限制，`resolveActivity()` 返回 null |
| **修复** | Manifest 添加 `<queries>` 声明对应 Intent 和 Scheme |
| **影响版本** | v1.5.0 修复 |
| **关键代码** | 见上方 `<queries>` 代码段 |

**思考：** 这是 Android 11 之后的常见坑，但官方文档对此说明不够突出，社区中也有大量开发者踩坑。使用隐式 Intent 时一定要关注包可见性问题。

---

### 问题 2：两个 `startActivity()` 不能在同一帧执行

| | |
|---|---|
| **现象** | 倒计时和美团只有一个能成功打开 |
| **根因** | 两个 `startActivity()` 在同一帧被调用，后者覆盖前者 |
| **修复** | 第一个成功后立即 `return`，第二个用 `postDelayed` 延迟执行 |
| **影响版本** | v1.8.0 修复 |

**思考：** Android 的 `startActivity()` 虽然是异步的，但 Intent 分发是在同一帧处理的。多个 Intent 同时提交会导致 Activity Stack 混乱。需要人为引入时间差。

---

### 问题 3：美团在后台时扫一扫无法激活

| | |
|---|---|
| **现象** | 美团 App 在后台运行，点击"一键骑车"后，美团回到前台但未进入扫一扫 |
| **根因** | 默认的启动 Intent 只是把美团带到前台，不会重新触发扫一扫 Scheme |
| **修复** | 添加 `FLAG_ACTIVITY_CLEAR_TOP`，强制重启美团的根 Activity 并重新处理 Scheme |
| **影响版本** | v2.2.0 修复 |

**思考：** `FLAG_ACTIVITY_CLEAR_TOP` 是处理"后台 App 重新激活"场景的关键 Flag，但会清除 Activity Stack，需要权衡。

---

### 问题 4：HyperOS 3 上 `openAR=1` 导致相机黑屏

| | |
|---|---|
| **现象** | 美团扫一扫界面打开，但相机预览黑屏 |
| **根因** | URL Scheme 中 `openAR=1` 参数在 HyperOS 3 的相机政策下导致相机初始化失败 |
| **修复** | 移除所有额外参数，使用最简 Scheme：`imeituan://www.meituan.com/scanQRCode` |
| **影响版本** | v1.7.0 修复 |

**思考：** 不同 ROM 对相机权限的管理策略不同，URL Scheme 中的非必要参数应尽量省略，提高兼容性。

---

### 问题 5：Foreground Service 在 Android 14 上必然闪退

| | |
|---|---|
| **现象** | 调用 `startForegroundService()` 直接闪退，无有效错误日志 |
| **根因** | Android 14 对 Foreground Service 类型声明要求极其严格，且第三方 App 难以满足 `specialUse` 的审核条件 |
| **修复** | 放弃 Foreground Service，改用 `AlarmManager` + `BroadcastReceiver` |
| **影响版本** | v1.2.0 修复（方案替换） |

**思考：** 官方文档对 Foreground Service 的限制说明分散且不及时更新。在实际开发中，如果不需要持久通知，应避免使用 Foreground Service，选择 `AlarmManager` 或 WorkManager。

---

### 问题 6：首次启动权限弹窗打断执行流程

| | |
|---|---|
| **现象** | 首次点击按钮，系统弹出权限申请，App 未等待用户响应就继续执行，导致两个任务均失败 |
| **根因** | 权限申请是异步的，App 在权限尚未授予时就调用了 `startActivity()` |
| **修复** | 用 `SharedPreferences` 区分首次/后续启动，首次显示引导页让用户手动触发 |
| **影响版本** | v2.3.0 修复 |

**思考：** 权限申请和业务流程的混合处理是移动开发的经典难题。最稳健的方案是"首次引导 + 后续自动"，既不阻塞用户，也保证了权限已就绪。

---

## 构建说明

### 构建 APK

```bash
cd MeiTuanOneTap
./gradlew assembleRelease
# 输出：app/build/outputs/apk/release/meituan-bike-reminder-v2.6.0.apk
```

### 版本号管理

文件：`MeiTuanOneTap/app/build.gradle.kts`

```kotlin
versionCode = 250
versionName = "2.5.0"
```

- `versionCode`：整数，每次递增
- `versionName`：语义化版本号

### 推送至 GitHub

```bash
# 配置远程仓库（首次）
git remote set-url origin https://Simiely:<token>@github.com/Simiely/meituan-bike-reminder.git

# 提交并推送
git add -A
git commit -m "描述更改"
git push origin main
```
