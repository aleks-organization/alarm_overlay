import Flutter
import UIKit
import UserNotifications
import AVFoundation

public class AlarmOverlayPlugin: NSObject, FlutterPlugin {

    private static let snoozeActionIdentifier = "SNOOZE_ACTION"
    private static let dismissActionIdentifier = "DISMISS_ACTION"
    private static let alarmCategoryIdentifier = "ALARM_CATEGORY"
    private static let defaultsKey = "alarm_overlay_alarms"
    private static let snoozeMillis: Int64 = 10 * 60 * 1000

    private var methodChannel: FlutterMethodChannel?
    private var eventChannel: FlutterEventChannel?
    private var eventSink: FlutterEventSink?
    private var alarmViewController: AlarmViewController?

    /// The active plugin instance, used by the host AppDelegate to forward
    /// `UNUserNotificationCenterDelegate` calls.
    public static weak var shared: AlarmOverlayPlugin?

    public static func register(with registrar: FlutterPluginRegistrar) {
        let instance = AlarmOverlayPlugin()
        shared = instance
        instance.setupChannels(with: registrar)
        instance.registerNotificationCategories()
        registrar.addMethodCallDelegate(instance, channel: instance.methodChannel!)
    }

    private func setupChannels(with registrar: FlutterPluginRegistrar) {
        let messenger = registrar.messenger()

        methodChannel = FlutterMethodChannel(
            name: "com.example.alarm_overlay/overlay",
            binaryMessenger: messenger
        )

        eventChannel = FlutterEventChannel(
            name: "com.example.alarm_overlay/overlay_events",
            binaryMessenger: messenger
        )
        eventChannel?.setStreamHandler(self)
    }

    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "startOverlay":
            result(true)

        case "stopOverlay":
            dismissAlarm()
            result(true)

        case "hasOverlayPermission":
            checkNotificationPermissions { hasPermission in
                result(hasPermission)
            }

        case "requestOverlayPermission":
            requestNotificationPermissions()
            result(true)

        case "scheduleOverlay":
            if let args = call.arguments as? [String: Any],
               let id = args["id"] as? Int,
               let timeMillis = args["time"] as? Int64,
               let label = args["label"] as? String {
                let sound = args["sound"] as? String
                scheduleAlarm(id: id, timeMillis: timeMillis, label: label, sound: sound)
                result(true)
            } else {
                result(FlutterError(code: "INVALID_ARGUMENTS", message: "Invalid arguments", details: nil))
            }

        case "cancelOverlay":
            if let args = call.arguments as? [String: Any],
               let id = args["id"] as? Int {
                cancelAlarm(id: id)
                result(true)
            } else {
                result(FlutterError(code: "INVALID_ARGUMENTS", message: "Invalid arguments", details: nil))
            }

        default:
            result(FlutterMethodNotImplemented)
        }
    }

    private func requestNotificationPermissions() {
        var options: UNAuthorizationOptions = [.alert, .sound, .badge]
        if #available(iOS 12.0, *) {
            options.insert(.criticalAlert)
        }
        UNUserNotificationCenter.current().requestAuthorization(options: options) { _, _ in }
    }

    private func checkNotificationPermissions(completion: @escaping (Bool) -> Void) {
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            DispatchQueue.main.async {
                completion(settings.authorizationStatus == .authorized)
            }
        }
    }

    private func registerNotificationCategories() {
        let snoozeAction = UNNotificationAction(
            identifier: AlarmOverlayPlugin.snoozeActionIdentifier,
            title: "Snooze",
            options: []
        )
        let dismissAction = UNNotificationAction(
            identifier: AlarmOverlayPlugin.dismissActionIdentifier,
            title: "Dismiss",
            options: [.destructive]
        )
        let category = UNNotificationCategory(
            identifier: AlarmOverlayPlugin.alarmCategoryIdentifier,
            actions: [snoozeAction, dismissAction],
            intentIdentifiers: [],
            options: [.customDismissAction]
        )
        UNUserNotificationCenter.current().setNotificationCategories([category])
    }

    private func scheduleAlarm(id: Int, timeMillis: Int64, label: String, sound: String?) {
        registerNotificationCategories()

        let date = Date(timeIntervalSince1970: TimeInterval(timeMillis / 1000))

        let content = UNMutableNotificationContent()
        content.title = "Alarm"
        content.body = label
        if let soundName = notificationSoundName(from: sound), !soundName.isEmpty {
            content.sound = UNNotificationSound(named: UNNotificationSoundName(rawValue: soundName))
        } else {
            content.sound = UNNotificationSound.default
        }
        if #available(iOS 15.0, *) {
            content.interruptionLevel = .critical
        }
        content.categoryIdentifier = AlarmOverlayPlugin.alarmCategoryIdentifier
        content.userInfo = [
            "alarm_id": id,
            "alarm_time": timeMillis,
            "alarm_label": label,
            "alarm_sound": sound as Any,
            "is_alarm": true
        ]

        let components = Calendar.current.dateComponents(
            [.year, .month, .day, .hour, .minute], from: date
        )
        let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)

        let request = UNNotificationRequest(
            identifier: "alarm_\(id)",
            content: content,
            trigger: trigger
        )

        UNUserNotificationCenter.current().add(request) { error in
            if let error = error {
                print("Error scheduling alarm: \(error)")
            }
        }

        saveAlarm(id: id, timeMillis: timeMillis, label: label, sound: sound)
    }

    /// Resolves the file name (last path component) for custom sounds stored in
    /// `Library/Sounds`, so `UNNotificationSound(named:)` can find them in the
    /// app container. Built-in sounds are used as-is.
    private func notificationSoundName(from sound: String?) -> String? {
        guard let sound = sound, !sound.isEmpty else { return nil }
        let prefix = "custom:"
        guard sound.hasPrefix(prefix) else { return sound }
        let path = String(sound.dropFirst(prefix.count))
        return (path as NSString).lastPathComponent
    }

    private func cancelAlarm(id: Int) {
        UNUserNotificationCenter.current().removePendingNotificationRequests(
            withIdentifiers: ["alarm_\(id)"]
        )
        UNUserNotificationCenter.current().removeDeliveredNotifications(
            withIdentifiers: ["alarm_\(id)"]
        )
        removeAlarm(id: id)
    }

    private func snoozeAlarm(id: Int) {
        guard let data = getAlarm(id: id) else { return }
        let label = data["label"] as? String ?? "Alarm"
        let sound = data["sound"] as? String
        let newTime = Int64(Date().timeIntervalSince1970 * 1000) + AlarmOverlayPlugin.snoozeMillis
        scheduleAlarm(id: id, timeMillis: newTime, label: label, sound: sound)

        eventSink?(["action": "snooze", "id": id, "time": newTime])
    }

    private func dismissAlarm(id: Int) {
        cancelAlarm(id: id)
        eventSink?(["action": "close", "id": id, "time": Int64(Date().timeIntervalSince1970 * 1000)])
    }

    private func dismissAlarm() {
        if let alarmVC = alarmViewController {
            alarmVC.dismiss(animated: true) { [weak self] in
                self?.alarmViewController = nil
            }
        }
    }

    // MARK: - Persistence (UserDefaults)

    private func saveAlarm(id: Int, timeMillis: Int64, label: String, sound: String?) {
        var dict = UserDefaults.standard.dictionary(forKey: AlarmOverlayPlugin.defaultsKey) ?? [:]
        dict[String(id)] = [
            "time": timeMillis,
            "label": label,
            "sound": sound ?? ""
        ]
        UserDefaults.standard.set(dict, forKey: AlarmOverlayPlugin.defaultsKey)
    }

    private func removeAlarm(id: Int) {
        var dict = UserDefaults.standard.dictionary(forKey: AlarmOverlayPlugin.defaultsKey) ?? [:]
        dict.removeValue(forKey: String(id))
        UserDefaults.standard.set(dict, forKey: AlarmOverlayPlugin.defaultsKey)
    }

    private func getAlarm(id: Int) -> [String: Any]? {
        let dict = UserDefaults.standard.dictionary(forKey: AlarmOverlayPlugin.defaultsKey) ?? [:]
        return dict[String(id)] as? [String: Any]
    }

    // MARK: - Public API for host AppDelegate

    /// Call this from your AppDelegate's
    /// `userNotificationCenter(_:willPresent:withCompletionHandler:)`
    /// to handle alarm notifications when the app is in the foreground.
    public func handleNotificationWillPresent(
        _ notification: UNNotification,
        completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) -> Bool {
        let userInfo = notification.request.content.userInfo
        if let isAlarm = userInfo["is_alarm"] as? Bool, isAlarm {
            presentAlarmViewController(with: userInfo)
            completionHandler([])
            return true
        }
        return false
    }

    /// Call this from your AppDelegate's
    /// `userNotificationCenter(_:didReceive:withCompletionHandler:)`
    /// to handle alarm notification taps and action buttons when the app is in
    /// the background/terminated.
    public func handleNotificationDidReceive(
        _ response: UNNotificationResponse,
        completionHandler: @escaping () -> Void
    ) -> Bool {
        let userInfo = response.notification.request.content.userInfo
        guard let isAlarm = userInfo["is_alarm"] as? Bool, isAlarm else { return false }
        let id = userInfo["alarm_id"] as? Int ?? 0

        switch response.actionIdentifier {
        case AlarmOverlayPlugin.snoozeActionIdentifier:
            snoozeAlarm(id: id)
        case AlarmOverlayPlugin.dismissActionIdentifier:
            dismissAlarm(id: id)
        default:
            presentAlarmViewController(with: userInfo)
        }

        completionHandler()
        return true
    }

    private func presentAlarmViewController(with userInfo: [AnyHashable: Any]) {
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let window = windowScene.windows.first else { return }

        do {
            let audioSession = AVAudioSession.sharedInstance()
            try audioSession.setCategory(.playback, mode: .default)
            try audioSession.setActive(true)
        } catch {
            print("Error configuring audio session: \(error)")
        }

        if let existingAlarm = alarmViewController {
            existingAlarm.dismiss(animated: false, completion: nil)
            alarmViewController = nil
        }

        let alarmId = userInfo["alarm_id"] as? Int ?? 0
        let alarmTime = userInfo["alarm_time"] as? Int64 ?? 0
        let alarmLabel = userInfo["alarm_label"] as? String ?? "Alarm"
        let alarmSound = userInfo["alarm_sound"] as? String

        let alarmVC = AlarmViewController()
        alarmVC.configure(
            alarmId: alarmId,
            alarmTime: alarmTime,
            alarmLabel: alarmLabel,
            alarmSound: alarmSound,
            eventSink: eventSink
        )

        alarmVC.modalPresentationStyle = .fullScreen
        alarmViewController = alarmVC

        if let rootVC = window.rootViewController {
            rootVC.present(alarmVC, animated: true, completion: nil)
        }
    }
}

// MARK: - FlutterStreamHandler

extension AlarmOverlayPlugin: FlutterStreamHandler {
    public func onListen(
        withArguments arguments: Any?,
        eventSink events: @escaping FlutterEventSink
    ) -> FlutterError? {
        self.eventSink = events
        return nil
    }

    public func onCancel(withArguments arguments: Any?) -> FlutterError? {
        self.eventSink = nil
        return nil
    }
}
