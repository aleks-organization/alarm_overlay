package com.example.alarm_overlay

import android.app.Activity
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmActivity : Activity() {
    private var vibrator: Vibrator? = null
    private var alarmId: Int = 0
    private var alarmTime: Long = 0L
    private var alarmLabel: String = "Alarm"

    companion object {
        @Volatile
        private var activeInstance: AlarmActivity? = null

        /// Dismisses the currently visible alarm activity (if any).
        fun dismissActive() {
            val instance = activeInstance ?: return
            instance.runOnUiThread { instance.dismissActivity() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activeInstance = this

        alarmId = intent.getIntExtra("alarm_id", 0)
        alarmTime = intent.getLongExtra("alarm_time", 0L)
        alarmLabel = intent.getStringExtra("alarm_label") ?: "Alarm"

        // Volume rocker controls the alarm stream while the alarm is ringing
        volumeControlStream = AudioManager.STREAM_ALARM

        setupLockScreenDisplay()
        buildUI()
        startVibration()
    }

    private fun dismissActivity() {
        AlarmRingingService.stopRing(this)
        stopVibration()
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(AlarmRingingService.NOTIF_ID)
            nm.cancel(alarmId)
        } catch (_: Exception) {
        }
        finish()
    }

    private fun setupLockScreenDisplay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun buildUI() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setBackgroundColor(Color.parseColor("#10101E"))
        root.setPadding(dp(24), dp(24), dp(24), dp(24))

        val title = TextView(this)
        title.text = "ALARM"
        title.textSize = 18f
        title.setTextColor(0xCCFFFFFF.toInt())
        title.gravity = Gravity.CENTER
        root.addView(title)

        val timeTv = TextView(this)
        timeTv.text = formatTime(alarmTime)
        timeTv.textSize = 72f
        timeTv.setTextColor(Color.WHITE)
        timeTv.gravity = Gravity.CENTER
        val timeLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        timeLp.topMargin = dp(16)
        timeLp.bottomMargin = dp(16)
        timeTv.layoutParams = timeLp
        root.addView(timeTv)

        val labelTv = TextView(this)
        labelTv.text = alarmLabel
        labelTv.textSize = 20f
        labelTv.setTextColor(0xE6FFFFFF.toInt())
        labelTv.gravity = Gravity.CENTER
        val labelLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        labelLp.bottomMargin = dp(48)
        labelTv.layoutParams = labelLp
        root.addView(labelTv)

        val btnRow = LinearLayout(this)
        btnRow.orientation = LinearLayout.HORIZONTAL
        btnRow.gravity = Gravity.CENTER

        val snoozeBtn = makeButton("SNOOZE", 0xFFFF9800.toInt())
        snoozeBtn.setOnClickListener { snoozeAlarm() }
        val snoozeLp = LinearLayout.LayoutParams(0, dp(64), 1f)
        snoozeLp.marginEnd = dp(12)
        btnRow.addView(snoozeBtn, snoozeLp)

        val dismissBtn = makeButton("DISMISS", 0xFF4CAF50.toInt())
        dismissBtn.setOnClickListener { dismissAlarm() }
        val dismissLp = LinearLayout.LayoutParams(0, dp(64), 1f)
        dismissLp.marginStart = dp(12)
        btnRow.addView(dismissBtn, dismissLp)

        root.addView(
            btnRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(root)
    }

    private fun makeButton(text: String, color: Int): Button {
        return Button(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 16f
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(color)
                cornerRadius = dp(16).toFloat()
            }
            background = drawable
        }
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val pattern = longArrayOf(0, 1000, 1000)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun dismissAlarm() {
        AlarmRingingService.stopRing(this)
        stopVibration()
        AlarmScheduler.dismiss(this, alarmId)
        notifyFlutter("close")
        finish()
    }

    private fun snoozeAlarm() {
        AlarmRingingService.stopRing(this)
        stopVibration()
        AlarmScheduler.snooze(this, alarmId)
        notifyFlutter("snooze")
        finish()
    }

    private fun notifyFlutter(action: String) {
        try {
            AlarmOverlayPlugin.dartEventSink?.success(
                mapOf("action" to action, "id" to alarmId, "time" to alarmTime)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopVibration() {
        vibrator?.cancel()
    }

    private fun formatTime(timeMillis: Long): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timeMillis))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        if (activeInstance === this) {
            activeInstance = null
        }
        stopVibration()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Back button disabled; user must use Snooze or Dismiss
    }
}
