# 🚲 扫完记得还 — 美团骑车锁车提醒

> 一键启动倒计时 + 打开美团扫一扫。结束骑行时准时提醒，再也不怕忘记锁车。

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

[DEVELOPMENT.md](./DEVELOPMENT.md) — 技术架构、版本日志、踩坑记录。

---

## 🔧 从源码构建

```bash
# 前置条件：JDK 17+、Android SDK（platform 34 + build-tools）

# 1. 克隆
git clone https://github.com/Simiely/meituan-bike-reminder.git
cd meituan-bike-reminder

# 2. 设置 local.properties（指向本地 Android SDK）
echo "sdk.dir=/path/to/Android/Sdk" > MeiTuanOneTap/local.properties

# 3. 配置签名（密钥不入库，见下方说明）
cp keystore.properties.example keystore.properties
#   然后编辑 keystore.properties 填入你自己的密钥库路径与口令

# 4. 构建 Release APK
cd MeiTuanOneTap
./gradlew assembleRelease

# 输出：app/build/outputs/apk/release/meituan-bike-reminder-v2.7.1.apk
```

> 🔐 **签名密钥不入库**：签名信息通过项目根目录的 `keystore.properties`（已被 `.gitignore` 忽略）或环境变量
> `KEYSTORE_FILE / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD` 注入。未配置签名时可构建未签名包用于本地调试。

源码、资源、Gradle Wrapper 均已提交，配置好本地 SDK 与签名后即可复现构建。详见 [DEVELOPMENT.md](./DEVELOPMENT.md)。

---

## License

MIT
