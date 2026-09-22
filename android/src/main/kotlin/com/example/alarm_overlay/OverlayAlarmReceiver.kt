package com.example.alarm_overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class OverlayAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ALARM_CHANNEL_ID = "alarm_channel"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("overlay_id", 0)
        val time = intent.getLongExtra("overlay_time", System.currentTimeMillis())
        val label = intent.getStringExtra("alarm_label") ?: "Alarm"
        val sound = intent.getStringExtra("alarm_sound")
        val volume = intent.getFloatExtra("alarm_volume", 1f)

        // Start a foreground service that plays the alarm sound and shows a
        // notification with SNOOZE/DISMISS actions. The service works even when
        // the full-screen activity cannot be launched (Android 14+ without the
        // "full screen" permission granted).
        val serviceIntent = Intent(context, AlarmRingingService::class.java).apply {
            putExtra("alarm_id", id)
            putExtra("alarm_time", time)
            putExtra("alarm_label", label)
            putExtra("alarm_sound", sound)
            putExtra("alarm_volume", volume)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}