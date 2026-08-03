# CHANGELOG.md · 版本记录

> 按版本从新到旧。完整问题记录见 [DEVELOPMENT.md](DEVELOPMENT.md)。

## v2.7.2 — 安全与鲁棒性审计整改

- 签名密钥从仓库与 git 历史彻底清除（`git filter-repo`），改为 keystore.properties / 环境变量注入；明文密码删除
- Android 13+ 运行时申请 `POST_NOTIFICATIONS`；兜底闹钟用 `canScheduleExactAlarms` + `setExactAndAllowWhileIdle`
- 新增骑行会话状态防重复发车；修 SKIP_UI 状态闪烁；提醒通知不再重拉起发车界面；`allowBackup=false`、`shrinkResources=true`
- 删除死代码设置面板与 `settings_bg.xml`；清理内部草稿文档；CI 权限下放到 job 级
- ⚠️ 旧签名密钥已作废，重新部署必须 keytool 自建

## v2.7.1 — 按钮位置调整 + 修复按钮路径扫码被 BAL 拦截

- 按钮下移避免贴顶，底部开源信息位置不变
- 扫码拉起改为带后台启动授权的 PendingIntent（绕过 Android 10+ BAL），保留美团首页预热；修复「按钮路径只拉起首页不弹扫码」
- 曾试「连续两次扫码」规避黑屏，真机验证无效，已回退单次（勿再重复实验）

## v2.7.0 — 首页底部增加开源信息

- 首页内容上移，底部增加两行文字 + 可点击 GitHub 链接

## v2.6.0 — 更名「扫完记得还」

- App 名从「一键骑车」改为「扫完记得还」，文案同步优化

## v2.5.0 — 图标方案简化

- 删除自适应图标 XML，仅保留 PNG（折腾三次不成功，最朴素 PNG 最可靠）

## v2.4.0 — 图标替换 + 配色 + 签名

- 液态玻璃风格图标全分辨率 mipmap；配色橙红 `#FF6D00` → 玫瑰粉 `#E55D6B`；新增 Signature Scheme v2

## v2.3.0 — 首次启动权限优化

- SharedPreferences 标记初始化状态：首次显示引导页等手动触发，后续自动执行（权限弹窗不打断流程）

## v1.8.0 — 两个 startActivity 不能在同一帧

- 倒计时后 `return`，美团 `postDelayed` 延迟 1000ms 启动，任务时间分离互不干扰

## v1.7.0 — 美团扫一扫黑屏

- URL Scheme 去掉 `openAR=1` 等非必要参数（HyperOS 3 上致相机初始化失败），用最简 Scheme

## v1.5.0 — 包可见性限制

- Android 11 包可见性：`resolveActivity()` 返回 null，Manifest 加 `<queries>` 声明（ACTION_SET_TIMER / imeituan scheme / 美团包名）

## v1.1.0 ~ v1.2.0 — Foreground Service 闪退（废弃）

- Android 14 Foreground Service 对第三方 App 几乎不可用，放弃改用 AlarmManager + BroadcastReceiver

## 备注

- 项目无独立 CHANGELOG 文件，版本日志原在 DEVELOPMENT.md「关键版本日志」章节，本文件为提炼版
