import 'dart:async';
import 'package:flutter/services.dart';

enum OverlayAction { close, snooze, unknown }

class OverlayActionEvent {
  final OverlayAction action;
  final int? id;
  final DateTime? time;

  const OverlayActionEvent({required this.action, this.id, this.time});

  factory OverlayActionEvent.fromMap(Map<dynamic, dynamic> map) {
    final actionStr = (map['action'] ?? '') as String;
    final action = (actionStr == 'close' || actionStr == 'dismiss')
        ? OverlayAction.close
        : actionStr == 'snooze'
            ? OverlayAction.snooze
            : OverlayAction.unknown;

    final id = map['id'] != null ? (map['id'] as num).toInt() : null;
    final timeMs = map['time'] != null ? (map['time'] as num).toInt() : null;
    return OverlayActionEvent(
      action: action,
      id: id,
      time: timeMs != null ? DateTime.fromMillisecondsSinceEpoch(timeMs) : null,
    );
  }
}

class AlarmOverlay {
  static const MethodChannel _channel =
      MethodChannel('com.example.alarm_overlay/overlay');
  static const EventChannel _events =
      EventChannel('com.example.alarm_overlay/overlay_events');

  static Stream<OverlayActionEvent> get onAction =>
      _events.receiveBroadcastStream().map((event) {
        try {
          return OverlayActionEvent.fromMap(event as Map);
        } catch (_) {
          return const OverlayActionEvent(action: OverlayAction.unknown);
        }
      });

  /// Schedule an alarm overlay at [when].
  /// On Android this uses AlarmManager + system overlay window.
  /// On iOS this uses UNUserNotificationCenter + full-screen UIViewController.
  ///
  /// [id] - unique alarm identifier
  /// [when] - DateTime of when the alarm should trigger
  /// [label] - optional label displayed on the overlay
  /// [sound] - optional sound filename (without extension) for built-in
  ///   sounds (Android assets / iOS bundle), or a `custom:`-prefixed absolute
  ///   path to a user-provided file in app-private storage. Falls back to
  ///   system default.
  /// [volume] - optional alarm volume as a fraction of the max (0.0 - 1.0).
  ///   Defaults to 1.0 (full alarm volume).
  static Future<void> schedule(
    int id,
    DateTime when, {
    String label = 'Alarm',
    String? sound,
    double? volume,
  }) async {
    await _channel.invokeMethod('scheduleOverlay', {
      'id': id,
      'time': when.millisecondsSinceEpoch,
      'label': label,
      'sound': sound,
      'volume': volume ?? 1.0,
    });
  }

  /// Cancel a previously scheduled alarm overlay by [id].
  static Future<void> cancel(int id) async {
    await _channel.invokeMethod('cancelOverlay', {'id': id});
  }

  /// Dismiss the currently displayed alarm (full-screen activity on Android,
  /// alarm view controller on iOS) without changing the scheduled alarm state.
  static Future<void> stop() async {
    await _channel.invokeMethod('stopOverlay');
  }

  /// Check if the overlay permission is granted.
  /// Android: SYSTEM_ALERT_WINDOW
  /// iOS: notification authorization
  static Future<bool> hasPermission() async {
    final res = await _channel.invokeMethod('hasOverlayPermission');
    return res == true;
  }

  /// Request overlay permission (opens system settings if needed).
  static Future<void> requestPermission() async {
    await _channel.invokeMethod('requestOverlayPermission');
  }
}
