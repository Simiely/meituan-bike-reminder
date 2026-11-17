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

8 条按「一坑一篇」拆到 [docs/问题记录.md](docs/问题记录.md)。

## 关键版本日志

完整版本日志见 [docs/版本日志.md](docs/版本日志.md)；版本变更摘要仍以 [CHANGELOG.md](CHANGELOG.md) 为准。

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
