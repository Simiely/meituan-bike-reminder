# 🚲 美团骑车 — 锁车提醒

> **再也不怕忘记锁车了！** 扫码骑车后自动设置定时提醒，到期通知你锁车。

## 问题

使用美团单车时，停车后经常忘记锁车，导致持续计费或被他人骑走。

## 解决方案

本仓库提供**两套独立方案**，分别针对 iPhone 和 Android 用户，均使用系统原生提醒机制，**无需后台常驻、无需位置权限、100% 可靠**。

---

## 方案对比

| | iPhone 方案 | Android 方案 |
|---|---|---|
| **原理** | Shortcuts 快捷指令 + 系统提醒事项 | Tasker 自动化脚本 |
| **触发方式** | 打开美团 App 时自动触发 | 打开美团 App 时自动触发 |
| **提醒机制** | 系统提醒事项（锁屏/静音均可靠） | Tasker 前台服务通知（带交互按钮） |
| **用户选择时长** | ✅ 15/20/30 分钟 | ✅ 15/20/30 分钟 + 延长时间 |
| **重复提醒** | ❌ 单次提醒 | ✅ 每 3 分钟重复 |
| **已锁车按钮** | ❌ 需手动取消提醒 | ✅ 点击"已锁车"停止提醒 |
| **费用** | 免费（系统自带） | Tasker 应用 ~$3.5 |
| **配置难度** | ⭐⭐ 简单（5 分钟） | ⭐⭐⭐ 中等（需导入配置） |
| **适用版本** | iOS 17+ | Android 8+ |

---

## 快速开始

### 📱 iPhone 用户

👉 [iOS 快捷指令配置指南](./ios_shortcuts_guide.md)

核心步骤：
1. 打开"快捷指令"App → 自动化 → 创建个人自动化
2. 选择触发条件：**App → 美团 → 已打开**
3. 添加动作：弹出菜单选择时长 → 创建提醒事项
4. 关闭"运行前询问"

### 📱 Android 用户

推荐使用以下 Android App（开箱即用）：

#### 📲 App 1：锁车提醒（通用版）
👉 [**下载锁车提醒 App v1.0.0**](https://github.com/Simiely/meituan-bike-reminder/releases/tag/v1.0.0)
- 前台服务计时，通知栏倒计时，到点弹窗提醒
- 支持自定义时长，带「已锁车」按钮
- 通用 Android 8+ 设备

#### 📲 App 2：一键骑车（MIUI 专属）👈 新
👉 [**下载一键骑车 App v1.0.0**](https://github.com/Simiely/meituan-bike-reminder/releases/tag/v1.0.0-onetap)
- **专为小米 MIUI 设计！** 一个按钮做两件事
- ⏱ 自动创建 **MIUI 系统 50 分钟倒计时**（小米时钟原生计时，无确认弹窗）
- 📸 自动打开 **美团扫一扫**（扫码骑车）
- 极简界面，点击即用
- 时间到 → 锁屏全屏提醒 +「已锁车」按钮
- 适配 MIUI / EMUI / OneUI 等主流 ROM

> 进阶用户也可以参考 [Android Tasker 配置指南](./android_tasker_guide.md)，使用 Tasker 自动化方案。

---

## 辅助工具

### 🌐 PWA 倒计时网页

作为轻量补充，提供一个倒计时 PWA，适合手机亮屏场景。

**在线访问（推荐）：** 👉 [https://simiely.github.io/meituan-bike-reminder/](https://simiely.github.io/meituan-bike-reminder/)

**添加到主屏幕（像 App 一样用）：**
- **iPhone Safari**：打开链接 → 底部"分享"按钮 → **添加到主屏幕**
- **Android Chrome**：打开链接 → 右上角菜单 → **添加到主屏幕**

> ⚠️ **注意**：PWA 需要保持浏览器页面打开才能计时，关闭浏览器后计时停止。适合骑行时手机亮屏放在车篮的场景，或配合系统自动化方案一起使用。

---

## 工作原理

```
你扫码骑车 → 打开美团 App
        ↓
自动触发自动化规则
        ↓
弹出菜单选择预计骑行时长
        ↓
⏰ 倒计时开始
        ↓
时间到 → 弹出提醒："🚲 该锁车了！"
        ↓
  ┌─────┴─────┐
  ↓             ↓
已锁车✅     忘记锁车❌
  ↓             ↓
结束         持续计费 → 再次提醒
```

## 注意事项

- ⚠️ 打开美团 App（包括点外卖）都会触发提醒，这是设计上的取舍——宁可误触发，不可漏提醒
- ⚠️ Android 端请务必在系统设置中将 Tasker 加入电池优化白名单
- ✅ 所有方案均**不需要**位置权限、网络权限、通讯录权限
- ✅ 提醒仅在锁屏/通知中心弹出，不影响正常使用

---

## 项目结构

```
├── README.md                        # 本文件
├── ios_shortcuts_guide.md           # iOS 快捷指令配置指南
├── android_tasker_guide.md          # Android Tasker 配置指南
├── meituan_lock_reminder.xml        # Tasker 配置文件（可直接导入）
├── index.html                       # PWA 倒计时网页
├── manifest.json                    # PWA 清单
├── sw.js                            # Service Worker
├── MeiTuanOneTap/                   # 📲 一键骑车 App（MIUI 专属）源码
└── MeiTuanReminder/                 # 📲 锁车提醒 App（通用版）源码
```
