# 📌 项目进度摘要（供新对话使用）

**仓库：** https://github.com/Simiely/meituan-bike-reminder
**GitHub Token：** 请参考历史记录获取（不应明文存储在代码中）

---

## 当前状态（v2.5.0）

### ✅ 已完成
1. README 已精简，仅保留用户导向内容
2. App 名称为「**倒计时 扫车**」
3. 首页配色已更新：橙红色系 → **玫瑰粉色系**（`primary=#E55D6B`, `background=#FFF5F6`）
4. **App 图标已替换**为用户提供的液态玻璃风格图标，生成全分辨率 mipmap 资源
5. 代码已推送到 GitHub main 分支
6. APK 已构建并签名，发布到 GitHub Releases：**v2.5.0**
7. 移除了 PWA / iOS / Tasker 等无关方案
8. 拆分开发文档至 DEVELOPMENT.md

### ❌ 未完成（待处理）
1. 暂无

---

## 项目关键信息

### 项目路径
- 工作目录：`/workspace/`
- App 源码：`/tmp/meituan-bike-reminder/MeiTuanOneTap/`
- 输出 APK：`/workspace/meituan-bike-reminder-v2.4.0.apk`

### 当前版本号
- `versionCode = 240`
- `versionName = "2.4.0"`
- 文件：`MeiTuanOneTap/app/build.gradle.kts`

### 包名
- `com.meituan.onetap`

### 关键文件
| 文件 | 说明 |
|---|---|
| `MeiTuanOneTap/app/src/main/java/com/meituan/onetap/MainActivity.kt` | 主逻辑 |
| `MeiTuanOneTap/app/src/main/AndroidManifest.xml` | Manifest（含 `<queries>` 声明） |
| `MeiTuanOneTap/app/src/main/res/values/strings.xml` | App 名称：「**一键骑车**」 |
| `MeiTuanOneTap/app/src/main/res/values/colors.xml` | 当前配色：玫瑰粉色系 |
| `MeiTuanOneTap/app/src/main/res/layout/activity_main.xml` | 首页布局 |

### 签名
- 签名密钥：`release.keystore`（有效至2053年）
- 签名方案：APK Signature Scheme v2
- 密钥密码：请查看 build.gradle.kts

### Git 远程仓库
```
origin  https://github.com/Simiely/meituan-bike-reminder.git
```

---

## 下一步（新对话中继续）

1. 暂无计划任务

---

## 技术备注

- 构建命令：`cd /tmp/meituan-bike-reminder/MeiTuanOneTap && ./gradlew assembleRelease`
- Android SDK 已在沙箱环境中配置好（/root/Android/Sdk）
- 发布命令：`gh release create vX.Y.Z --title "标题" --notes "说明" /path/to/apk`
- Git 推送需用 token URL 格式：`git remote set-url origin https://Simiely:<token>@github.com/Simiely/meituan-bike-reminder.git`
