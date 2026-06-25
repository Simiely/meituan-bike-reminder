# 📁 project-assets — WorkBuddy 项目资产

> **本目录由 WorkBuddy AI 代理自动整理，包含「美团骑车锁车提醒」项目的全部源文件、文档和构建产物。**
>
> **整理时间：** 2025-07
> **对应版本：** v2.3.0
> **整理者：** WorkBuddy AI Agent

---

## 📂 目录结构

```
project-assets/
├── README.md                  # 本文件（目录说明）
├── android-app/              # Android App 完整源码
├── android-tasker/           # Android Tasker 方案文档 + 配置文件
└── archive/                  # 历史构建产物（APK 等）
```

---

## 📱 android-app/

**内容：** 倒计时 扫车 — Android App 完整源码（Android Studio 项目）

**包名：** `com.meituan.onetap`

**当前版本：** v2.3.0（`versionCode=230`, `versionName="2.3.0"`）

**开发环境：** 针对小米澎湃OS3（HyperOS 3）开发，美团App为当前最新版

**测试环境：** 小米 HyperOS / MIUI

**目录结构：**
```
android-app/MeiTuanOneTap/
├── app/
│   ├── build.gradle.kts          # 构建配置（版本号、依赖）
│   ├── proguard-rules.pro        # 混淆规则
│   └── src/main/
│       ├── AndroidManifest.xml   # 权限声明 + <queries> 包可见性
│       ├── java/.../
│       │   ├── MainActivity.kt   # 主逻辑（倒计时 + 美团扫一扫）
│       │   └── TimerReceiver.kt  # AlarmManager 兜底提醒广播
│       └── res/
│           ├── layout/activity_main.xml   # 首页布局
│           ├── values/colors.xml          # 配色（橙红色系）
│           ├── values/strings.xml         # 文案（含 App 名称）
│           ├── values/themes.xml          # App 主题
│           ├── drawable/                 # 图标矢量图
│           └── mipmap-anydpi-v26/      # Adaptive Icon
├── build.gradle.kts        # 项目级构建配置
├── settings.gradle.kts     # 模块声明
├── gradle.properties       # Gradle 配置
└── gradlew                 # Gradle Wrapper（Linux/macOS）
```

**如何构建：**
```bash
cd android-app/MeiTuanOneTap
./gradlew assembleRelease
# 输出：app/build/outputs/apk/release/meituan-bike-reminder-v2.3.0.apk
```

**关键技术点：**
- 使用 `AlarmClock.ACTION_SET_TIMER` + `EXTRA_SKIP_UI` 调用系统倒计时（无确认弹窗）
- Android 11+ 包可见性需在 Manifest 中声明 `<queries>`
- 两个 `startActivity()` 需间隔 500ms，否则互相覆盖
- 美团后台激活需添加 `FLAG_ACTIVITY_CLEAR_TOP`
- 首次启动用 `SharedPreferences` 区分，避免权限弹窗打断执行

---

## 🤖 android-tasker/

**内容：** Android Tasker 自动化方案文档 + 配置文件

**文件：**
- `android_tasker_guide.md` — Tasker 安装 + 配置导入步骤
- `meituan_lock_reminder.xml` — Tasker 配置文件（可直接导入）

**原理：** Tasker 监听美团 App 启动事件，自动创建前台服务通知倒计时，每 3 分钟重复提醒，支持"已锁车"按钮取消。

**适用：** Android 8+，需要 Tasker App（付费 ~$3.5）。

---

## 📦 archive/

**内容：** 历史构建产物

**当前文件：**
- `meituan-bike-reminder-v2.3.0.apk` — v2.3.0 Release 构建包

**后续版本：** 新 APK 构建后请放入此目录，并注明版本号和日期。

---

## 🔗 相关链接

| 资源 | 链接 |
|---|---|
| **GitHub 仓库** | https://github.com/Simiely/meituan-bike-reminder |
| **GitHub Releases** | https://github.com/Simiely/meituan-bike-reminder/releases |

---

## 📝 跟进说明

如果你是需要接手本项目的新开发者/新 Agent，请按以下顺序阅读：

1. **先读 `/workspace/README.md`** — 了解项目背景、问题、方案总览
2. **再读 README 中的「开发日志」** — 了解 v1.0 ~ v2.3 每个版本迭代了什么
3. **重点读「关键技术问题记录」** — 6 个已踩过的坑，避免重复踩坑
4. **然后看 `android-app/` 源码** — `MainActivity.kt` 是核心，代码有详细注释
5. **如需修改配色/文案** — 修改 `res/values/colors.xml` 和 `res/values/strings.xml`
6. **如需发布新版本** — 修改 `app/build.gradle.kts` 中的 `versionCode` 和 `versionName`，然后 `./gradlew assembleRelease`

**Python 环境和构建环境说明：**
- 沙箱环境：Ubuntu 22.04，已安装 Android SDK（通过 Gradle Wrapper 自动下载）
- 构建命令：`./gradlew assembleRelease`（无需额外配置）
- Git 推送：已配置 GitHub Token，使用 `git remote set-url origin https://Simiely:<token>@github.com/Simiely/meituan-bike-reminder.git`

---

## ⚠️ 已知未完成工作

以下工作由用户提出，但因对话长度限制未能完成，需要后续跟进：

1. **图标素材处理** — 用户提供了图标素材图片，需要处理成 Android 各分辨率 mipmap 资源（mdpi ~ xxxhdpi），并替换当前默认的自行车矢量图标
2. **首页配色微调** — 需要根据图标实际色调进一步调整 `colors.xml`
3. **新版本发布** — 图标更新后，需要构建新 APK 并创建 GitHub Release

> 如需继续上述工作，请将图标素材图片提供给 Agent，并参考 `android-app/MeiTuanOneTap/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` 了解当前图标结构。
