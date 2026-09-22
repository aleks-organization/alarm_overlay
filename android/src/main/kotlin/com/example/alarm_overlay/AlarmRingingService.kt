package com.example.alarm_overlay

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Foreground service that keeps the alarm ringing and shows a notification with
 * SNOOZE/DISMISS actions even when the full-screen activity cannot launch
 * (e.g. Android 14+ without the "full screen" permission granted).
 */
class AlarmRingingService : Service() {

    companion object {
        const val NOTIF_ID = 2001

        @Volatile
        private var mediaPlayer: MediaPlayer? = null
        private var originalAlarmVolume: Int = -1
        private var audioManager: AudioManager? = null

        fun stopRing() {
            try {
                mediaPlayer?.let {
                    if (it.isPlaying) it.stop()
                    it.release()
                }
            } catch (_: Exception) {
            }
            mediaPlayer = null
            restoreAlarmVolume()
        }

        fun stopRing(context: Context) {
            stopRing()
            try {
                context.stopService(Intent(context, AlarmRingingService::class.java))
            } catch (_: Exception) {
            }
        }

        private fun restoreAlarmVolume() {
            if (originalAlarmVolume >= 0) {
                try {
                    audioManager?.setStreamVolume(
                        AudioManager.STREAM_ALARM,
                        originalAlarmVolume,
                        0
                    )
                } catch (_: Exception) {
                }
                originalAlarmVolume = -1
            }
        }
    }

    private var alarmId: Int = 0
    private var alarmTime: Long = 0L
    private var alarmLabel: String = "Alarm"
    private var alarmSound: String? = null
    private var alarmVolume: Float = 1f

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            alarmId = it.getIntExtra("alarm_id", 0)
            alarmTime = it.getLongExtra("alarm_time", System.currentTimeMillis())
            alarmLabel = it.getStringExtra("alarm_label") ?: "Alarm"
            alarmSound = it.getStringExtra("alarm_sound")
            alarmVolume = it.getFloatExtra("alarm_volume", 1f)
        }

        createChannel()
        startForeground(NOTIF_ID, buildNotification())

        startAlarmSound()

        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = android.app.NotificationChannel(
                OverlayAlarmReceiver.ALARM_CHANNEL_ID,
                "Alarm",
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.enableVibration(true)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val snoozePending = buildActionPending(AlarmActionReceiver.ACTION_SNOOZE)
        val dismissPending = buildActionPending(AlarmActionReceiver.ACTION_CLOSE)
        val contentPending = buildContentPending()

        // Custom content views with always-visible SNOOZE/DISMISS buttons.
        val collapsedView = buildContentView(R.layout.alarm_notification, alarmTime, alarmLabel)
        val bigView = buildContentView(R.layout.alarm_notification_big, alarmTime, alarmLabel)
        bindButtons(collapsedView, snoozePending, dismissPending)
        bindButtons(bigView, snoozePending, dismissPending)

        return NotificationCompat.Builder(this, OverlayAlarmReceiver.ALARM_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Alarm")
            .setContentText(alarmLabel)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(contentPending)
            .setCustomContentView(collapsedView)
            .setCustomBigContentView(bigView)
            .setCustomHeadsUpContentView(collapsedView)
            .setFullScreenIntent(contentPending, true)
            .build()
    }

    private fun buildContentView(layoutRes: Int, time: Long, label: String): android.widget.RemoteViews {
        val views = android.widget.RemoteViews(packageName, layoutRes)
        views.setTextViewText(R.id.alarm_title, "ALARM")
        views.setTextViewText(
            R.id.alarm_time,
            java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(time))
        )
        views.setTextViewText(R.id.alarm_label, label)
        return views
    }

    private fun bindButtons(
        views: android.widget.RemoteViews,
        snoozePending: PendingIntent,
        dismissPending: PendingIntent
    ) {
        views.setOnClickPendingIntent(R.id.btn_snooze, snoozePending)
        views.setOnClickPendingIntent(R.id.btn_dismiss, dismissPending)
    }

    private fun buildActionPending(action: String): PendingIntent {
        val actionIntent = Intent(this, AlarmActionReceiver::class.java).apply {
            this.action = action
            putExtra("alarm_id", alarmId)
            putExtra("alarm_time", alarmTime)
        }
        val requestCode = if (action == AlarmActionReceiver.ACTION_SNOOZE) alarmId * 2 + 1 else alarmId * 2
        return PendingIntent.getBroadcast(
            this, requestCode, actionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
    }

    private fun buildContentPending(): PendingIntent {
        val activityIntent = Intent(this, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("alarm_id", alarmId)
            putExtra("alarm_time", alarmTime)
            putExtra("alarm_label", alarmLabel)
            putExtra("alarm_sound", alarmSound)
            putExtra("alarm_volume", alarmVolume)
        }
        return PendingIntent.getActivity(
            this, alarmId, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
    }

    private fun startAlarmSound() {
        try {
            audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_ALARM) ?: 0
            originalAlarmVolume = audioManager?.getStreamVolume(AudioManager.STREAM_ALARM) ?: -1
            val desiredVolume = (maxVolume * alarmVolume).toInt().coerceIn(0, maxVolume)
            if (desiredVolume != originalAlarmVolume) {
                audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, desiredVolume, 0)
            }

            val player = MediaPlayer()
            mediaPlayer = player
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            } else {
                @Suppress("DEPRECATION")
                player.setAudioStreamType(AudioManager.STREAM_ALARM)
            }

            val soundFile = alarmSound ?: "over_the_horizon.mp3"
            val afd = assets.openFd(soundFile)
            player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            player.prepare()
            player.isLooping = true
            player.start()
        } catch (e: Exception) {
            playSystemAlarm()
        }
    }

    private fun playSystemAlarm() {
        try {
            mediaPlayer?.release()
            val player = MediaPlayer()
            mediaPlayer = player
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            } else {
                @Suppress("DEPRECATION")
                player.setAudioStreamType(AudioManager.STREAM_ALARM)
            }
            player.setDataSource(
                this,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            )
            player.isLooping = true
            player.prepare()
            player.start()
        } catch (e2: Exception) {
            e2.printStackTrace()
        }
    }

    private fun immutableFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRing()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}