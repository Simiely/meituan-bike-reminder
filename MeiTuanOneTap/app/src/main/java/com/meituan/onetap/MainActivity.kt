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
        // 冷启动预热：先打开美团让进程起来，再跳转扫码，规避扫码页相机预览初始化竞态（黑屏）
        private const val MEITUAN_WARMUP_MS = 1200L

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
                // 计时已启动，美团稍后异步打开 —— 先显示"即将打开"，避免误报失败
                binding.statusText.text = "⏱ 50分钟倒计时已启动\n📸 正在打开美团…"
                launchMeituan() // 预热 + 跳转扫码（带延时，规避冷启动相机黑屏）
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
        binding.btnStartRiding.isEnabled = true
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
     * 打开美团扫码。
     * 冷启动时先启动美团（预热进程），再延时跳转扫码页：让扫码活动在“热”进程中启动，
     * 规避美团扫码页相机预览初始化竞态导致的黑屏（热进程下不出现该问题，见 DEVELOPMENT.md）。
     */
    private fun launchMeituan(timerOk: Boolean = true) {
        val launchIntent = packageManager.getLaunchIntentForPackage(MEITUAN_PACKAGE)
        if (launchIntent != null) {
            try {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent) // 预热：先让美团进程/首页起来
            } catch (e: Exception) {
                Log.w(TAG, "warm-up launch failed", e)
            }
        }
        handler.postDelayed({
            val meituanOk = openMeituanScan()
            updateStatus(timerOk, meituanOk)
        }, MEITUAN_WARMUP_MS)
    }

    private fun openMeituanScan(): Boolean {
        for (scheme in MEITUAN_SCHEMES) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(scheme)).apply {
                    // 仅 NEW_TASK：避免 CLEAR_TOP 在热启动时重建美团栈，导致闪屏/预览竞态
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
