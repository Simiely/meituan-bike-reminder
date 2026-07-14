# 🚲 扫完记得还 — 美团骑车锁车提醒

> 一键启动倒计时 + 打开美团扫一扫。结束骑行时准时提醒，再也不怕忘记锁车。

## 项目简介

「扫完记得还」是一个**极简的单一用途 Android 工具 App**：

- 美团单车 / 电单车随借随用，但容易骑忘了、锁车提醒全靠自己记；
- 这个 App 只做一件事——**点一下图标，自动帮你（1）启动一个 50 分钟系统倒计时、（2）直接打开美团扫一扫**；
- 倒计时到期，系统会响铃提醒你「该锁车了」，从源头避免超时扣费。

它不是一个完整的骑行 App，而是一个「启动器 + 提醒器」：把两个高频动作合并成一次点击，并在 50 分钟这个常见骑行时长后兜底提醒。代码量很小，核心逻辑集中在 `MainActivity.kt`，适合作为「Android 隐式 Intent / 系统闹钟 / 通知权限」的参考示例。

## 功能介绍

点击图标自动完成两件事：

1. **⏱ 启动 50 分钟系统倒计时**（无弹窗，支持灵动岛）
2. **📸 打开美团扫一扫**（直接进入扫码界面）

倒计时到期 → 系统响铃提醒锁车 🔔

## 下载

> 📦 最新版本：**v2.7.1**
>
> 👉 [下载 APK](https://github.com/Simiely/meituan-bike-reminder/releases)

## 使用

**首次安装：** 打开 App → 点击按钮 → 按提示授权 → 完成。

**之后每次：** 点击图标，自动执行，无需等待。

## 要求

- 小米澎湃OS3（HyperOS 3）
- 美团 App 当前最新版
- Android 7.0+

## FAQ

**Q：为什么是 50 分钟？**
A：覆盖大多数骑行场景。太长没用，太短容易超时。

**Q：没提醒？**
A：检查系统时钟的通知权限是否开启。设置 → 应用 → 权限 → 通知。

**Q：扫一扫黑屏？**
A：极小概率事件，已通过延时优化处理。确保使用最新版。

**Q：其他手机能用吗？**
A：针对 HyperOS 3 优化，其他 Android 7.0+ 设备理论上兼容。

---

## 📦 开发文档

[DEVELOPMENT.md](./DEVELOPMENT.md) — 技术架构、关键问题与踩坑参考、版本日志。

---

## 🔧 从源码构建 / 自己部署

> ⚠️ **自己部署前必读**：本项目**不包含任何可用签名密钥**（历史中的密钥已作废，不可复用）。
> 你必须用 `keytool` **生成自己的全新密钥**，并在 `keystore.properties` 中更新 `storeFile / storePassword / keyAlias / keyPassword`。
> 用旧密钥或他人密钥签名的包无法覆盖安装到对方设备上，且密钥泄露有安全风险。

### 1. 前置条件
- JDK 17+
- Android SDK（platform 34 + build-tools）

### 2. 克隆并配置 SDK
```bash
git clone https://github.com/Simiely/meituan-bike-reminder.git
cd meituan-bike-reminder

# 指向本地 Android SDK
echo "sdk.dir=/path/to/Android/Sdk" > MeiTuanOneTap/local.properties
```

### 3. 生成你自己的签名密钥（keytool）
```bash
# 生成新的 release.keystore（有效期建议 25~30 年，单位：天）
keytool -genkeypair \
  -v \
  -keystore release.keystore \
  -keyalg RSA -keysize 2048 -validity 9125 \
  -alias meituan_bike

# 按提示输入密钥库口令、姓名/组织等信息，最后确认（y）
```

### 4. 填写签名配置
```bash
cp keystore.properties.example keystore.properties
# 编辑 keystore.properties，填入上一步的：
#   storeFile=release.keystore
#   storePassword=<你的密钥库口令>
#   keyAlias=meituan_bike
#   keyPassword=<你的密钥口令>
#
# 也可用环境变量注入（CI 推荐）：
#   KEYSTORE_FILE / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD
```
> 🔐 **签名密钥不入库**：`keystore.properties` 与 `*.keystore` 均已被 `.gitignore` 忽略，永远不会提交进仓库。

### 5. 构建 Release APK
```bash
cd MeiTuanOneTap
./gradlew assembleRelease
# 输出：app/build/outputs/apk/release/meituan-bike-reminder-v2.7.1.apk
```
未配置签名时 release 构建会走未签名流程，可用于本地调试。详细构建与发布见 [DEVELOPMENT.md](./DEVELOPMENT.md)。

---

## License

MIT
