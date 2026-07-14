package com.meituan.onetap

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.meituan.onetap.databinding.ActivityMainBinding

/**
 * 扫完记得还 App
 *
 * 首次安装 → 显示引导，手动点击初始化权限
 * 初始化后 → 每次打开自动执行（启动倒计时 + 打开美团扫一扫）
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var hasLaunched = false

    companion object {
        private const val TAG = "MeiTuanOneTap"
        private const val PREF_NAME = "app_state"
        private const val KEY_INITIALIZED = "initialized"
        private const val TIMER_SECONDS = 3000
        private const val TIMER_MILLIS = TIMER_SECONDS * 1000L
        private const val REQ_POST_NOTIFICATIONS = 1001
        private const val MEITUAN_PACKAGE = "com.sankuai.meituan"
        // 计时与预热首页错开，避免同帧 startActivity 互相覆盖（v1.8.0 坑）
        private const val TIMER_TO_MEITUAN_GAP_MS = 400L
        // 预热后，由系统闹钟触发扫码 PendingIntent 前的热进程稳定时间
        private const val MEITUAN_WARMUP_MS = 1200L
        private const val SCAN_PENDING_REQ = 1

        private val MEITUAN_SCHEMES = listOf(
            "imeituan://www.meituan.com/scanQRCode",
            "imeituan://www.meituan.com/bike/scan",
            "imeituan://platformapi/startapp?saId=1001",
            "imeituan://"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        binding.btnStartRiding.setOnClickListener {
            // 用户主动点击 → 总是重新发车
            hasLaunched = false
            onStartRiding()
        }

        when {
            !prefs.getBoolean(KEY_INITIALIZED, false) -> showWelcome()
            else -> onStartRiding()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        hasLaunched = false
        if (!prefs.getBoolean(KEY_INITIALIZED, false)) return
        // 已初始化 → 每次回到前台都自动执行（启动倒计时 + 打开美团扫一扫）
        onStartRiding()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // 权限结果无需阻塞主流程；仅记录日志。
        if (requestCode == REQ_POST_NOTIFICATIONS) {
            val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "POST_NOTIFICATIONS granted=$granted")
        }
    }

    private fun showWelcome() {
        binding.title.text = "第一次使用"
        binding.subtitle.text = "点击下方按钮，按提示授予权限"
        binding.action1.text = "授权后，App 会自动执行"
        binding.action2.text = "下次点击图标即可自动运行"
        binding.statusText.text = "👆 点击按钮开始"
    }

    // ============ 执行逻辑 ============

    private fun onStartRiding() {
        if (hasLaunched) return
        hasLaunched = true

        // 在依赖通知前，确保已申请通知权限（Android 13+ 必需，否则兜底提醒静默失败）。
        ensureNotificationPermission()

        binding.btnStartRiding.isEnabled = false
        binding.statusText.text = "⏳ 执行中..."

        // 首次执行成功后标记已初始化
        if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
            prefs.edit().putBoolean(KEY_INITIALIZED, true).apply()
            // 恢复 UI 文字
            binding.title.text = "扫完记得还"
            binding.subtitle.text = "点击后自动执行："
            binding.action1.text = "⏱  创建 50 分钟倒计时"
            binding.action2.text = "📸  打开美团扫一扫"
        }

        // ① 系统时钟（SKIP_UI，无确认弹窗）
        val skipIntent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, TIMER_SECONDS)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            putExtra(AlarmClock.EXTRA_MESSAGE, "🚲 美团骑车")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (skipIntent.resolveActivity(packageManager) != null) {
            try {
                startActivity(skipIntent)
                launchMeituan(true) // 预热美团 + 由系统闹钟延时拉起扫码（规避冷启动黑屏）
                binding.btnStartRiding.isEnabled = true
                return
            } catch (e: Exception) {
                Log.e(TAG, "SKIP_UI failed", e)
            }
        }

        // ② AlarmManager 兜底
        var timerOk = false
        try {
            val alarmIntent = Intent(this, TimerReceiver::class.java).apply {
                action = TimerReceiver.ACTION_TIMES_UP
            }
            val pendingIntent = PendingIntent.getBroadcast(
                this, 0, alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarm = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val triggerAt = System.currentTimeMillis() + TIMER_MILLIS
            scheduleAlarm(alarm, triggerAt, pendingIntent)
            timerOk = true
        } catch (e: Exception) {
            Log.e(TAG, "AlarmManager failed", e)
        }

        launchMeituan(timerOk)
    }

    /**
     * 尽量精确地设置到点提醒：
     * - Android 12+ 且已获精确闹钟权限 → setExactAndAllowWhileIdle；
     * - 否则退化为 setAndAllowWhileIdle（近似，但可穿透 Doze），并引导用户开启精确闹钟权限。
     */
    private fun scheduleAlarm(alarm: AlarmManager, triggerAt: Long, pi: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarm.canScheduleExactAlarms()) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                requestExactAlarmPermission()
            }
        } else {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun requestExactAlarmPermission() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Log.w(TAG, "cannot open exact-alarm settings", e)
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_POST_NOTIFICATIONS
                )
            }
        }
    }

    /**
     * 预热美团并延时拉起扫一扫，规避冷启动相机黑屏。
     *
     * 原理：
     * 1) 先打开美团首页（不调相机），把美团进程预热为"热进程"；
     * 2) 再由系统闹钟（AlarmManager）触发扫码的 PendingIntent。
     *    PendingIntent 由系统调度触发，豁免 Android 10+ 后台启动限制；
     *    此时美团已是热进程，扫码页预览 Surface 已就绪，规避冷启动黑屏竞态。
     */
    private fun launchMeituan(timerOk: Boolean) {
        // ① 预热：错开计时器（TIMER_TO_MEITUAN_GAP_MS），打开美团首页，不调相机
        handler.postDelayed({
            openMeituanHome() // 预热失败则忽略，交由下方 PendingIntent 拉起扫码
        }, TIMER_TO_MEITUAN_GAP_MS)

        // ② 预热后由系统闹钟触发扫码 PendingIntent（豁免后台启动限制）
        val scanIntent = Intent(Intent.ACTION_VIEW, Uri.parse(MEITUAN_SCHEMES.first())).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val scanPi = PendingIntent.getActivity(
            this, SCAN_PENDING_REQ, scanIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarm = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + TIMER_TO_MEITUAN_GAP_MS + MEITUAN_WARMUP_MS
        val meituanOk = MEITUAN_SCHEMES.any { scheme ->
            Intent(Intent.ACTION_VIEW, Uri.parse(scheme)).resolveActivity(packageManager) != null
        } || packageManager.getLaunchIntentForPackage(MEITUAN_PACKAGE) != null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarm.canScheduleExactAlarms()) {
                // 无精确闹钟权限：退化为非精确闹钟（仍由系统调度，豁免后台启动限制）
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, scanPi)
            } else {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, scanPi)
            }
        } catch (e: Exception) {
            Log.e(TAG, "scan pending alarm failed", e)
            // 闹钟不可用：回退到直接深链（可能冷启动黑屏）
            openMeituan()
        }
        updateStatus(timerOk, meituanOk)
        binding.btnStartRiding.isEnabled = true
    }

    /**
     * 仅打开美团首页做进程预热（不调相机，避免冷启动黑屏）。
     * 返回是否成功拉起美团首页。
     */
    private fun openMeituanHome(): Boolean {
        return try {
            val homeIntent = packageManager.getLaunchIntentForPackage(MEITUAN_PACKAGE)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (homeIntent != null) {
                startActivity(homeIntent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "open meituan home failed", e)
            false
        }
    }

    private fun openMeituan(): Boolean {
        for (scheme in MEITUAN_SCHEMES) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(scheme)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                    return true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Scheme failed: $scheme", e)
            }
        }
        try {
            val intent = packageManager.getLaunchIntentForPackage(MEITUAN_PACKAGE)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(intent)
                return true
            }
        } catch (_: Exception) { }
        downloadMeituan()
        return false
    }

    private fun downloadMeituan() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=$MEITUAN_PACKAGE")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {
            Toast.makeText(this, "请手动安装美团", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateStatus(timerOk: Boolean, meituanOk: Boolean) {
        val parts = mutableListOf<String>()
        if (timerOk) parts.add("⏱ 50分钟倒计时已启动")
        else parts.add("❌ 倒计时不可用")
        if (meituanOk) parts.add("📸 美团已打开")
        else parts.add("❌ 美团打开失败")
        binding.statusText.text = parts.joinToString("\n")
    }
}
