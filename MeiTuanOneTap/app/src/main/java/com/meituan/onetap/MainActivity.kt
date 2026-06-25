package com.meituan.onetap

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.meituan.onetap.databinding.ActivityMainBinding

/**
 * 扫完记得还 App v2.6
 *
 * 首次安装 → 显示引导，手动点击初始化权限
 * 初始化后 → 每次打开自动执行
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
        private const val MEITUAN_PACKAGE = "com.sankuai.meituan"

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
            hasLaunched = false
            onStartRiding()
        }

        if (prefs.getBoolean(KEY_INITIALIZED, false)) {
            // 已初始化 → 直接自动执行
            onStartRiding()
        } else {
            // 首次 → 显示引导
            showWelcome()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        hasLaunched = false
        if (prefs.getBoolean(KEY_INITIALIZED, false)) {
            onStartRiding()
        }
    }

    private fun showWelcome() {
        binding.title.text = "欢迎使用扫完记得还"
        binding.subtitle.text = "首次使用需要授权打开时钟和美团"
        binding.action1.text = "点击下方按钮完成初始化"
        binding.action2.text = "后续打开 App 将自动执行"
        binding.statusText.text = "👆 点击按钮开始"
    }

    // ============ 执行逻辑 ============

    private fun onStartRiding() {
        if (hasLaunched) return
        hasLaunched = true

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

        var timerOk = false

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
                timerOk = true
                scheduleMeituan(500)
                updateStatus(timerOk, false)
                binding.btnStartRiding.isEnabled = true
                return
            } catch (e: Exception) {
                Log.e(TAG, "SKIP_UI failed", e)
            }
        }

        // ② AlarmManager 兜底
        try {
            val alarmIntent = Intent(this, TimerReceiver::class.java).apply {
                action = TimerReceiver.ACTION_TIMES_UP
            }
            val pendingIntent = PendingIntent.getBroadcast(
                this, 0, alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarm = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarm.set(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + TIMER_SECONDS * 1000L, pendingIntent)
            timerOk = true
        } catch (e: Exception) {
            Log.e(TAG, "AlarmManager failed", e)
        }

        val meituanOk = openMeituan()
        updateStatus(timerOk, meituanOk)
        binding.btnStartRiding.isEnabled = true
    }

    private fun scheduleMeituan(delayMs: Long) {
        handler.postDelayed({
            val meituanOk = openMeituan()
            updateStatus(true, meituanOk)
        }, delayMs)
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
