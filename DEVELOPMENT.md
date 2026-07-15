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
| 签名 | APK Signature Scheme v2；密钥通过 `keystore.properties`/环境变量注入，**不入库** |
| 混淆 | R8 开启，保留 MainActivity 和 TimerReceiver |
| 包可见性 | Manifest `<queries>` 声明 `ACTION_SET_TIMER` 和 `imeituan` Scheme |
| 图标 | 纯 mipmap PNG（mdpi~xxxhdpi），无自适应图标 XML 层 |

---

## 关键问题与踩坑参考

> 本节点记录开发中**真实踩过、且以后极可能再遇到**的问题，按「现象 → 根因 → 解法 → 适用场景」整理，
> 作为同类问题的速查参考。**不要只记结论，要记「为什么」**——下次换个项目、换个人，照样能照着做。

### ① 签名密钥入库 = 高危漏洞（最该长记性的一条）

- **现象**：仓库里直接提交了 `release.keystore`，且 `build.gradle.kts` 明文写死 `storePassword="meituan123"`、`keyAlias`。任何人 clone 即可用同一把密钥签发「更新包」覆盖你的 App。
- **根因**：把「能直接构建」的便利，凌驾于「密钥保密」之上；以为私有仓库就安全。
- **解法**：
  1. 密钥与密码**绝不入库**：放进 `keystore.properties`（加 `.gitignore`）+ 支持环境变量 `KEYSTORE_*` 注入；
  2. 已泄露的密钥**永久不可信**，必须 `keytool` 生成新密钥轮换；
  3. 已进历史的密钥要彻底清除：`git filter-repo --path <file> --invert-paths --force`，再 force-push；**只 `git rm` 不够**，历史里还能翻出来；
  4. CI 用 Secret 注入环境变量，本地 `.gitignore` 兜底。
- **适用场景**：任何带签名/凭证的仓库（Android / iOS / npm / 云服务），「能一键构建」和「凭证安全」必须分开。
- **一句话**：**密钥、token、密码，永远从环境/本地忽略文件来，绝不写进 git。**

### ② Android 13+ 通知权限不运行时申请 → 兜底提醒静默失效

- **现象**：App 在 Android 13 上声明了 `POST_NOTIFICATIONS`，但只在 Manifest 声明、没 `requestPermissions`；结果「该锁车了」通知根本不弹，用户毫无感知。
- **根因**：Android 13（API 33, `TIRAMISU`）起通知是**运行时危险权限**，光声明没用，必须在用到前弹窗申请。
- **解法**：在发车流程最前面调 `ActivityCompat.requestPermissions(..., POST_NOTIFICATIONS)`，结果在 `onRequestPermissionsResult` 里只记日志、不阻塞主流程（权限异步，用户可能稍后才同意）。
- **适用场景**：所有「依赖系统通知做提醒/保活」的功能，13+ 都必须补运行时申请，否则在低版本测得好好的，到新系统全哑火。

### ③ 声明了精确闹钟权限，却用了不精确的 set()

- **现象**：Manifest 声明了 `SCHEDULE_EXACT_ALARM`，但倒计时兜底用了 `AlarmManager.set()`（非精确），在厂商省电策略下可能被延迟数分钟，到点提醒严重不准。
- **根因**：声明权限 ≠ 用对 API；精确闹钟要显式调用 `setExactAndAllowWhileIdle`，且需先 `canScheduleExactAlarms()` 判断。
- **解法**：
  ```kotlin
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarm.canScheduleExactAlarms()) {
      alarm.setExactAndAllowWhileIdle(RTC_WAKEUP, triggerAt, pi)   // 精确，可穿透 Doze
  } else {
      alarm.setAndAllowWhileIdle(RTC_WAKEUP, triggerAt, pi)       // 降级：近似但能穿透 Doze
      // 引导用户去开「精确闹钟」权限
      startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
  }
  ```
- **适用场景**：所有需要「到点准时触发」的闹钟/提醒；Android 12+ 精确闹钟受管控，必须先判断能力再选 API，并提供降级与引导。

### ④ singleTask 重进前台会重复执行核心动作

- **现象**：`launchMode="singleTask"` 下，每次从桌面/其他 App 回到前台都会走 `onNewIntent` 重跑「启动倒计时 + 打开美团」，骑行途中误触就重复计时、重复弹美团。
- **根因**：把「每次回到前台」当成了「每次需要执行」，没有区分「同一趟骑行」还是「新一趟」。
- **解法**：用 `SharedPreferences` 记 `KEY_RIDE_START`（本次发车时间戳）；`isRideActive() = 距发车 < 50 分钟`；`onCreate`/`onNewIntent` 里若骑行仍在进行就只显示「进行中」、**不再发车**。用户主动点按钮才重置会话重新计时。
- **适用场景**：任何「回到前台就自动做某件事」的 App（快捷启动器、自动化触发），都应加「会话/幂等」状态防止重复触发。

### ⑤ 通知点击复用会「发车」的 Activity → 误触发

- **现象**：到点提醒通知设了 `contentIntent` 指向主 Activity；用户点通知本想消音，结果主界面被拉起、又自动发车一遍。
- **根因**：把「提醒通知」和「启动入口」混用了同一个 Activity + 同样的自动执行逻辑。
- **解法**：提醒通知**不设 `contentIntent`**，只 `setAutoCancel(true)` 点击消音；主入口与通知职责分离（通知 Receiver 只弹提醒，不触达任何会自动执行的界面）。
- **适用场景**：凡是「通知」和「可自动执行的入口」共存的场景，务必厘清点击通知的意图，别让它顺手又把核心动作跑一遍。

### ⑥ 死代码：布局定义了 UI，Activity 却没接线

- **现象**：`activity_main.xml` 里完整写了「倒计时时长设置」面板（`NumberPicker` + 保存按钮），但 `MainActivity.kt` 从头到尾没引用过这些控件——功能永远用不上，还误导后来者以为有时长设置。
- **根因**：UI 先画了，逻辑没接 or 接了一半被放弃，没清理。
- **解法**：删掉未被引用的布局节点与对应 `drawable`（如 `settings_bg.xml`）；并用「绑定视图是否都有引用」做静态核对，避免布局与代码漂移。
- **适用场景**：迭代中临时加的 UI、被砍掉的功能，**删功能时连布局/资源一起删**，留着就是技术债和误导。

### ⑦ git 历史里删文件 ≠ 仓库安全

- **现象**：以为 `git rm` 删掉 `release.keystore` 就安全了，其实它还在所有历史提交里，任何人都能 `git log` + 旧 commit 翻出。
- **根因**：`git rm` 只改当前树，不动历史对象。
- **解法**：要彻底清除用 `git filter-repo --path <file> --invert-paths --force`（需先 `pip install git-filter-repo`），再 `git push --force`。操作不可逆，重写前先通知协作者重新 clone。
- **适用场景**：误提交密钥/token/大文件后的紧急止血；普通清理用 `git rm` 即可。

### ⑧ allowBackup 默认值偏宽松

- **现象/风险**：未显式设置时 `allowBackup=true`，用户可通过 adb 备份恢复 App 数据，敏感状态（如初始化标记）可能被迁移/篡改。
- **解法**：非必要恢复场景的轻量工具 App，直接 `android:allowBackup="false"` 收紧。
- **适用场景**：不依赖系统云备份恢复的小工具类 App，默认关掉更稳。

---

## 关键版本日志

### v2.7.2 — 安全与鲁棒性审计整改

代码审计 + 整改（详见上文「关键问题与踩坑参考」）：
- 签名密钥从仓库与 git 历史彻底清除（`git filter-repo`），改为 `keystore.properties`/环境变量注入；明文密码删除。
- Android 13+ 运行时申请 `POST_NOTIFICATIONS`；兜底闹钟用 `canScheduleExactAlarms` + `setExactAndAllowWhileIdle`。
- 新增骑行会话状态防重复发车；修 SKIP_UI 状态闪烁；提醒通知不再重拉起发车界面；`allowBackup=false`、`shrinkResources=true`。
- 删除死代码设置面板与 `settings_bg.xml`；清理内部草稿文档；CI 权限下放到 job 级。
- **部署注意**：旧签名密钥已作废，重新部署必须用 `keytool` 生成自己的密钥（见 README「自己部署」与本文「签名配置」）。

### v2.7.1 — 按钮位置调整 + 修复按钮路径扫码被 BAL 拦截

- 按钮下移避免贴顶，底部开源信息位置不变。
- **修复「点 App 内『启动』按钮只拉起美团首页、不弹扫码」**：扫码拉起从普通 `startActivity` 改为带「后台启动授权」的 `PendingIntent`（FLAG + API34 发送方 ActivityOptions + API35 创建方 mode），绕过 Android 10+ 后台启动限制（BAL）。图标冷启动本就有「刚回前台」宽限期、能拉起；按钮路径 App 已转后台、无宽限期才暴露该问题。保留美团首页预热（热进程避黑屏），扫码失败回退普通直启。
- **扫码拉起改为连续两次（间隔 `SCAN_RETRY_GAP_MS=700ms`）**：第二次把已在前台的扫码页「重踢」、相机重新初始化，进一步规避偶发预览黑屏；两次均走带后台授权的 `PendingIntent`、且用独立 `requestCode` 互不合并、都真正投递。
- 回退点：本提交（含此前 `7d5dca7` 预热版）；如需回退到「只拉起首页/偶发黑屏」的稳定态，可 checkout `d616911`。

### v2.7.0 — 首页底部增加开源信息

首页内容上移，底部增加两行文字：
- `世界的风吹向你 / 开源项目 / 一切为了还车`
- 可点击的 GitHub 项目链接

### v2.6.0 — 更名「扫完记得还」

App 名从「一键骑车」改为「扫完记得还」。文案同步优化。

### v2.5.0 — 图标方案简化

> **教训：不要过度设计图标。**

删除自适应图标 XML，仅保留 PNG。之前折腾了三次都没成功——`@color/transparent` 不是 drawable、双图叠加、空 layer-list 在某些 ROM 上不工作。最朴素的 PNG 反而是最可靠的。

### 冷启动美团扫一扫黑屏（已用「预热 + PendingIntent 授权」解决）

> **现象：美团不在后台（冷启动）时，自动打开扫码页相机预览概率性黑屏，但能正常扫码。**
> **关键判断：黑屏却仍能解码 = 相机在采帧、预览 Surface 没渲染 → 预览渲染问题，非相机采集问题。**
> **根因：美团扫码活动在“冷进程”里 `camera.open` 早于预览 `Surface` 就绪，竞态导致黑屏；热进程（已在后台）下不出现。美团代码改不了。**
> **解法（v2.7.1 已落地）：先拉起美团首页进程「预热」（不调相机，错开 400ms），再用带「后台启动授权」的 `PendingIntent` 拉起扫码。**
> - 预热让美团变热进程 → 规避冷启动黑屏竞态（复刻「长按图标→扫一扫」稳定的本质：美团热进程）。
> - 第二步**必须**用 `PendingIntent` 显式授权后台启动，否则本 App 被预热顶到后台后，普通 `startActivity` 会被 BAL 静默拦截、扫码页永远不弹。
> - 扫码投递**两次**（间隔 `SCAN_RETRY_GAP_MS=700ms`）：首次拉起，第二次把已在前台的扫码页「重踢」一次、相机重新初始化，规避偶发预览黑屏；两次用独立 `requestCode` 的 `PendingIntent` 确保都真正投递、互不合并。
>   - API 31+：`FLAG_ALLOW_BACKGROUND_ACTIVITY_STARTS`（compileSdk=34 的 stub 已移除该常量 → 反射读取 + 硬编码 `0x01000000` 回退）；
>   - API 34（发送方）：`ActivityOptions.setPendingIntentBackgroundActivityStartMode(MODE_BACKGROUND_ACTIVITY_START_ALLOWED)`；
>   - API 35（创建方）：`PendingIntent.setPendingIntentCreatorBackgroundActivityStartMode(MODE_BACKGROUND_ACTIVITY_START_ALLOWED)`。
> **坑（曾踩，已修）：第二步若用普通 `startActivity` —— 图标冷启动能拉起（有系统“刚回到前台”宽限期），但 App 内「启动」按钮只拉起美团首页、不弹扫码（App 已在后台、无宽限期、被 BAL 吞掉）。这是 v2.7.1 按钮路径修复的关键。**

### v2.4.0 — 图标替换 + 配色 + 签名

替换用户提供的液态玻璃风格图标，生成全分辨率 mipmap。配色从橙红 `#FF6D00` 改为玫瑰粉 `#E55D6B`。新增 APK Signature Scheme v2 签名。

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

## 前置条件

- JDK 17+
- Android SDK（platform 34 + build-tools）
- 克隆项目后创建 `local.properties`：

```bash
echo "sdk.dir=/path/to/Android/Sdk" > MeiTuanOneTap/local.properties
```

### 签名配置（密钥不入库，必须自建）

> ⚠️ 仓库**不包含任何可用签名密钥**（历史密钥已作废）。自己部署前必须用 `keytool` 生成你自己的密钥：
>
> ```bash
> keytool -genkeypair -v -keystore release.keystore \
>   -keyalg RSA -keysize 2048 -validity 9125 -alias meituan_bike
> ```
> 然后把 `storeFile / storePassword / keyAlias / keyPassword` 填进 `keystore.properties`（复制自 `keystore.properties.example`）。

签名信息通过项目根目录 `keystore.properties`（已被 `.gitignore` 忽略）或环境变量注入：

```bash
cp keystore.properties.example keystore.properties
# 编辑 keystore.properties：storeFile / storePassword / keyAlias / keyPassword
```

对应环境变量：`KEYSTORE_FILE`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`（CI 中用 Secret 注入）。
未配置时 release 构建走未签名流程，便于本地调试。

## 构建

```bash
cd MeiTuanOneTap
./gradlew assembleRelease
# 输出：app/build/outputs/apk/release/meituan-bike-reminder-v2.7.1.apk
```

版本号在 `MeiTuanOneTap/app/build.gradle.kts`：
```kotlin
versionCode = 271
versionName = "2.7.1"
```

## 发布

```bash
gh release create vX.Y.Z --title "标题" --notes "说明" path/to/apk
```

---

## 项目文件清单

```
meituan-bike-reminder/
├── keystore.properties.example   # 签名配置模板（复制为 keystore.properties；密钥须用 keytool 自建，不入库）
├── .gitignore
├── README.md                     # 用户文档
├── DEVELOPMENT.md                # 开发者文档（本文）
│
├── MeiTuanOneTap/                # Android 项目根目录
│   ├── build.gradle.kts          # 项目级构建配置
│   ├── settings.gradle.kts       # 模块声明
│   ├── gradle.properties         # Gradle 属性
│   ├── gradlew / gradlew.bat     # Gradle Wrapper 脚本
│   ├── gradle/wrapper/           # Gradle Wrapper（含 gradle-wrapper.jar）
│   │
│   └── app/
│       ├── build.gradle.kts      # 模块级构建配置（版本号、签名、混淆等）
│       ├── proguard-rules.pro    # R8/ProGuard 混淆规则
│       └── src/main/
│           ├── AndroidManifest.xml
│           ├── java/com/meituan/onetap/
│           │   ├── MainActivity.kt      # 主界面 & 核心逻辑
│           │   └── TimerReceiver.kt     # 倒计时广播接收器
│           └── res/
│               ├── drawable/            # 背景等可绘制资源
│               ├── layout/              # 界面布局 XML
│               ├── mipmap-*/            # 应用图标（各分辨率）
│               └── values/              # 颜色、字符串、主题
```

> clone 后配置 `local.properties`（指向本地 Android SDK）与 `keystore.properties`（签名信息），即可 `./gradlew assembleRelease` 复现构建。源码、资源、Gradle Wrapper 均已包含；**签名密钥不入库**。
