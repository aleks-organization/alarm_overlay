package com.example.alarm_overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel(nm)

        val fullScreenPending = buildActivityPending(context, id, time, label, sound, volume)
        val snoozePending = buildActionPending(context, id, time, AlarmActionReceiver.ACTION_SNOOZE)
        val dismissPending = buildActionPending(context, id, time, AlarmActionReceiver.ACTION_CLOSE)

        // Custom content views with always-visible SNOOZE/DISMISS buttons.
        val collapsedView = buildContentView(context, R.layout.alarm_notification, time, label)
        val bigView = buildContentView(context, R.layout.alarm_notification_big, time, label)
        bindButtons(collapsedView, snoozePending, dismissPending)
        bindButtons(bigView, snoozePending, dismissPending)

        val builder = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Alarm")
            .setContentText(label)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(fullScreenPending)
            .setCustomContentView(collapsedView)
            .setCustomBigContentView(bigView)
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_lock_idle_alarm,
                    "SNOOZE",
                    snoozePending
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "DISMISS",
                    dismissPending
                ).build()
            )

        // Always launch the full-screen alarm popup (not a heads-up toast),
        // regardless of the screen/lock state. AlarmActivity plays the sound.
        builder.setFullScreenIntent(fullScreenPending, true)

        try {
            nm.notify(id, builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun buildContentView(
        context: Context,
        layoutRes: Int,
        time: Long,
        label: String
    ): RemoteViews {
        val views = RemoteViews(context.packageName, layoutRes)
        views.setTextViewText(R.id.alarm_title, "ALARM")
        views.setTextViewText(
            R.id.alarm_time,
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))
        )
        views.setTextViewText(R.id.alarm_label, label)
        return views
    }

    private fun bindButtons(
        views: RemoteViews,
        snoozePending: PendingIntent,
        dismissPending: PendingIntent
    ) {
        views.setOnClickPendingIntent(R.id.btn_snooze, snoozePending)
        views.setOnClickPendingIntent(R.id.btn_dismiss, dismissPending)
    }

    private fun buildActivityPending(
        context: Context,
        id: Int,
        time: Long,
        label: String,
        sound: String?,
        volume: Float
    ): PendingIntent {
        val activityIntent = Intent(context, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("alarm_id", id)
            putExtra("alarm_time", time)
            putExtra("alarm_label", label)
            putExtra("alarm_sound", sound)
            putExtra("alarm_volume", volume)
        }
        return PendingIntent.getActivity(
            context, id, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
    }

    private fun buildActionPending(
        context: Context,
        id: Int,
        time: Long,
        action: String
    ): PendingIntent {
        val actionIntent = Intent(context, AlarmActionReceiver::class.java).apply {
            this.action = action
            putExtra("alarm_id", id)
            putExtra("alarm_time", time)
        }
        val requestCode = if (action == AlarmActionReceiver.ACTION_SNOOZE) id * 2 + 1 else id * 2
        return PendingIntent.getBroadcast(
            context, requestCode, actionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
    }

    private fun createChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ALARM_CHANNEL_ID,
                "Alarm",
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.enableVibration(true)
            nm.createNotificationChannel(channel)
        }
    }

    private fun immutableFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
    }
}
