package com.example.alarm_overlay

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SNOOZE = "com.example.alarm_overlay.ACTION_SNOOZE"
        const val ACTION_CLOSE = "com.example.alarm_overlay.ACTION_CLOSE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("alarm_id", 0)
        val time = intent.getLongExtra("alarm_time", 0L)

        // Stop the ringing foreground service (sound + notification).
        AlarmRingingService.stopRing(context)
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(AlarmRingingService.NOTIF_ID)
            nm.cancel(id)
        } catch (_: Exception) {
        }

        val action = when (intent.action) {
            ACTION_SNOOZE -> {
                AlarmScheduler.snooze(context, id)
                "snooze"
            }
            ACTION_CLOSE -> {
                AlarmScheduler.dismiss(context, id)
                "close"
            }
            else -> return
        }

        try {
            AlarmOverlayPlugin.dartEventSink?.success(
                mapOf("action" to action, "id" to id, "time" to time)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
