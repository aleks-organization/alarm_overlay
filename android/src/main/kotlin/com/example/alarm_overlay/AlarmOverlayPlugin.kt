package com.example.alarm_overlay

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.NonNull
import androidx.core.app.NotificationManagerCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class AlarmOverlayPlugin : FlutterPlugin, MethodChannel.MethodCallHandler, EventChannel.StreamHandler {

    private lateinit var channel: MethodChannel
    private lateinit var eventChannel: EventChannel
    private var applicationContext: Context? = null

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        applicationContext = flutterPluginBinding.applicationContext

        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "com.example.alarm_overlay/overlay")
        channel.setMethodCallHandler(this)

        eventChannel = EventChannel(flutterPluginBinding.binaryMessenger, "com.example.alarm_overlay/overlay_events")
        eventChannel.setStreamHandler(this)
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        eventChannel.setStreamHandler(null)
        applicationContext = null
        Companion.dartEventSink = null
    }

    // ---- EventChannel.StreamHandler ----

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        Companion.dartEventSink = events
    }

    override fun onCancel(arguments: Any?) {
        Companion.dartEventSink = null
    }

    // ---- MethodChannel.MethodCallHandler ----

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: MethodChannel.Result) {
        val ctx = applicationContext ?: run {
            result.error("NO_CONTEXT", "Plugin not attached to engine", null)
            return
        }

        when (call.method) {
            "scheduleOverlay" -> {
                val args = call.arguments as? Map<*, *>
                val id = (args?.get("id") as? Int) ?: 0
                val time = (args?.get("time") as? Long) ?: 0L
                val label = (args?.get("label") as? String) ?: "Alarm"
                val sound = args?.get("sound") as? String
                val volume = (args?.get("volume") as? Double) ?: 1.0
                result.success(
                    AlarmScheduler.schedule(ctx, id, time, label, sound, volume.toFloat())
                )
            }
            "cancelOverlay" -> {
                val args = call.arguments as? Map<*, *>
                val id = (args?.get("id") as? Int) ?: 0
                result.success(AlarmScheduler.cancel(ctx, id))
            }
            "stopOverlay" -> {
                AlarmActivity.dismissActive()
                ctx.stopService(Intent(ctx, AlarmRingingService::class.java))
                result.success(true)
            }
            "hasOverlayPermission" -> result.success(hasPermission(ctx))
            "requestOverlayPermission" -> {
                requestPermission(ctx)
                result.success(true)
            }
            else -> result.notImplemented()
        }
    }

    // ---- Permissions ----

    private fun hasPermission(ctx: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        } else {
            true
        }
    }

    private fun requestPermission(ctx: Context) {
        // The app requests POST_NOTIFICATIONS via permission_handler (runtime
        // dialog). Opening the notification settings screen here is not needed.
    }

    companion object {
        var dartEventSink: EventChannel.EventSink? = null
            internal set
    }
}
