import UIKit
import AVFoundation
import AudioToolbox
import Flutter

@objc public class AlarmViewController: UIViewController {

    private var alarmId: Int = 0
    private var alarmTime: Int64 = 0
    private var alarmLabel: String = ""
    private var alarmSound: String? = nil
    private var eventSink: FlutterEventSink?

    private var audioPlayer: AVAudioPlayer?
    private var timer: Timer?
    private var animationTimer: Timer?
    private var soundTimer: Timer?

    // MARK: - UI Elements

    private let backgroundView: UIView = {
        let view = UIView()
        return view
    }()

    private let currentTimeLabel: UILabel = {
        let label = UILabel()
        label.textColor = .white
        label.font = UIFont.systemFont(ofSize: 24, weight: .regular)
        label.textAlignment = .center
        label.translatesAutoresizingMaskIntoConstraints = false
        return label
    }()

    private let alarmTitleLabel: UILabel = {
        let label = UILabel()
        label.text = "ALARM"
        label.textColor = UIColor.white.withAlphaComponent(0.8)
        label.font = UIFont.systemFont(ofSize: 16, weight: .bold)
        label.textAlignment = .center
        label.translatesAutoresizingMaskIntoConstraints = false
        return label
    }()

    private let alarmTimeLabel: UILabel = {
        let label = UILabel()
        label.textColor = .white
        label.font = UIFont.systemFont(ofSize: 72, weight: .ultraLight)
        label.textAlignment = .center
        label.translatesAutoresizingMaskIntoConstraints = false
        return label
    }()

    private let alarmLabelLabel: UILabel = {
        let label = UILabel()
        label.textColor = UIColor.white.withAlphaComponent(0.9)
        label.font = UIFont.systemFont(ofSize: 20, weight: .regular)
        label.textAlignment = .center
        label.numberOfLines = 2
        label.translatesAutoresizingMaskIntoConstraints = false
        return label
    }()

    private let alarmIconView: UIImageView = {
        let imageView = UIImageView()
        imageView.image = UIImage(systemName: "alarm.fill")
        imageView.tintColor = UIColor.white.withAlphaComponent(0.8)
        imageView.contentMode = .scaleAspectFit
        imageView.translatesAutoresizingMaskIntoConstraints = false
        return imageView
    }()

    private let snoozeButton: UIButton = {
        let button = UIButton(type: .system)
        button.setTitle("SNOOZE", for: .normal)
        button.setTitleColor(.white, for: .normal)
        button.titleLabel?.font = UIFont.systemFont(ofSize: 16, weight: .bold)
        button.backgroundColor = UIColor.systemOrange
        button.layer.cornerRadius = 32
        button.layer.borderWidth = 2
        button.layer.borderColor = UIColor.systemOrange.withAlphaComponent(0.8).cgColor
        button.translatesAutoresizingMaskIntoConstraints = false
        return button
    }()

    private let dismissButton: UIButton = {
        let button = UIButton(type: .system)
        button.setTitle("DISMISS", for: .normal)
        button.setTitleColor(.white, for: .normal)
        button.titleLabel?.font = UIFont.systemFont(ofSize: 16, weight: .bold)
        button.backgroundColor = UIColor.systemGreen
        button.layer.cornerRadius = 32
        button.layer.borderWidth = 2
        button.layer.borderColor = UIColor.systemGreen.withAlphaComponent(0.8).cgColor
        button.translatesAutoresizingMaskIntoConstraints = false
        return button
    }()

    // MARK: - Lifecycle

    public override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
        setupActions()
        startAlarmSound()
        startVibration()
        updateTimeLabels()
        startAnimations()

        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            self.updateCurrentTime()
        }
    }

    public override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        stopAlarmSound()
        stopVibration()
        timer?.invalidate()
        animationTimer?.invalidate()
        soundTimer?.invalidate()
    }

    public override var prefersStatusBarHidden: Bool { true }

    public override var supportedInterfaceOrientations: UIInterfaceOrientationMask { .portrait }

    // MARK: - Configuration

    func configure(
        alarmId: Int,
        alarmTime: Int64,
        alarmLabel: String,
        alarmSound: String?,
        eventSink: FlutterEventSink?
    ) {
        self.alarmId = alarmId
        self.alarmTime = alarmTime
        self.alarmLabel = alarmLabel
        self.alarmSound = alarmSound
        self.eventSink = eventSink
    }

    // MARK: - UI Setup

    private func setupUI() {
        let gradientLayer = CAGradientLayer()
        gradientLayer.colors = [
            UIColor(red: 0.1, green: 0.1, blue: 0.18, alpha: 1.0).cgColor,
            UIColor(red: 0.09, green: 0.13, blue: 0.24, alpha: 1.0).cgColor,
            UIColor(red: 0.06, green: 0.06, blue: 0.14, alpha: 1.0).cgColor
        ]
        gradientLayer.locations = [0.0, 0.5, 1.0]
        gradientLayer.startPoint = CGPoint(x: 0, y: 0)
        gradientLayer.endPoint = CGPoint(x: 1, y: 1)
        gradientLayer.frame = view.bounds
        backgroundView.layer.insertSublayer(gradientLayer, at: 0)

        view.addSubview(backgroundView)
        backgroundView.translatesAutoresizingMaskIntoConstraints = false

        view.addSubview(currentTimeLabel)
        view.addSubview(alarmTitleLabel)
        view.addSubview(alarmIconView)
        view.addSubview(alarmTimeLabel)
        view.addSubview(alarmLabelLabel)
        view.addSubview(snoozeButton)
        view.addSubview(dismissButton)

        setupConstraints()
    }

    private func setupConstraints() {
        NSLayoutConstraint.activate([
            backgroundView.topAnchor.constraint(equalTo: view.topAnchor),
            backgroundView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            backgroundView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            backgroundView.bottomAnchor.constraint(equalTo: view.bottomAnchor),

            currentTimeLabel.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 48),
            currentTimeLabel.centerXAnchor.constraint(equalTo: view.centerXAnchor),

            alarmTitleLabel.topAnchor.constraint(equalTo: currentTimeLabel.bottomAnchor, constant: 8),
            alarmTitleLabel.centerXAnchor.constraint(equalTo: view.centerXAnchor),

            alarmIconView.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            alarmIconView.centerYAnchor.constraint(equalTo: view.centerYAnchor, constant: -80),
            alarmIconView.widthAnchor.constraint(equalToConstant: 48),
            alarmIconView.heightAnchor.constraint(equalToConstant: 48),

            alarmTimeLabel.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            alarmTimeLabel.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            alarmTimeLabel.leadingAnchor.constraint(greaterThanOrEqualTo: view.leadingAnchor, constant: 20),
            alarmTimeLabel.trailingAnchor.constraint(lessThanOrEqualTo: view.trailingAnchor, constant: -20),

            alarmLabelLabel.topAnchor.constraint(equalTo: alarmTimeLabel.bottomAnchor, constant: 16),
            alarmLabelLabel.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            alarmLabelLabel.leadingAnchor.constraint(greaterThanOrEqualTo: view.leadingAnchor, constant: 20),
            alarmLabelLabel.trailingAnchor.constraint(lessThanOrEqualTo: view.trailingAnchor, constant: -20),

            snoozeButton.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 40),
            snoozeButton.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -48),
            snoozeButton.heightAnchor.constraint(equalToConstant: 64),
            snoozeButton.trailingAnchor.constraint(equalTo: view.centerXAnchor, constant: -12),

            dismissButton.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -40),
            dismissButton.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -48),
            dismissButton.heightAnchor.constraint(equalToConstant: 64),
            dismissButton.leadingAnchor.constraint(equalTo: view.centerXAnchor, constant: 12),
        ])
    }

    private func setupActions() {
        snoozeButton.addTarget(self, action: #selector(snoozeButtonTapped), for: .touchUpInside)
        dismissButton.addTarget(self, action: #selector(dismissButtonTapped), for: .touchUpInside)
    }

    // MARK: - Actions

    @objc private func snoozeButtonTapped() {
        stopAlarmSound()
        stopVibration()
        eventSink?(["action": "snooze", "id": alarmId, "time": alarmTime])
        dismiss(animated: true)
    }

    @objc private func dismissButtonTapped() {
        stopAlarmSound()
        stopVibration()
        eventSink?(["action": "dismiss", "id": alarmId, "time": alarmTime])
        dismiss(animated: true)
    }

    // MARK: - Time Updates

    private func updateTimeLabels() {
        let formatter = DateFormatter()
        formatter.dateFormat = "h:mm a"
        let alarmDate = Date(timeIntervalSince1970: TimeInterval(alarmTime / 1000))
        alarmTimeLabel.text = formatter.string(from: alarmDate)
        alarmLabelLabel.text = alarmLabel
        updateCurrentTime()
    }

    private func updateCurrentTime() {
        let formatter = DateFormatter()
        formatter.dateFormat = "h:mm a"
        currentTimeLabel.text = formatter.string(from: Date())
    }

    // MARK: - Animations

    private func startAnimations() {
        let pulse = CABasicAnimation(keyPath: "opacity")
        pulse.fromValue = 1.0
        pulse.toValue = 0.3
        pulse.duration = 1.0
        pulse.autoreverses = true
        pulse.repeatCount = .infinity
        alarmIconView.layer.add(pulse, forKey: "pulse")
    }

    // MARK: - Audio

    private func startAlarmSound() {
        do {
            let audioSession = AVAudioSession.sharedInstance()
            try audioSession.setCategory(.playback, mode: .default)
            try audioSession.setActive(true)
        } catch {
            print("Error configuring audio session: \(error)")
        }

        if let soundValue = alarmSound, soundValue.hasPrefix("custom:") {
            let path = String(soundValue.dropFirst("custom:".count))
            playSound(at: URL(fileURLWithPath: path))
            return
        }

        let soundFile = alarmSound ?? "over_the_horizon"
        let soundName = (soundFile as NSString).deletingPathExtension

        if let url = Bundle.main.url(forResource: soundName, withExtension: "mp3") ??
                        Bundle.main.url(forResource: soundName, withExtension: "wav") ??
                        librarySoundsUrl(forName: soundName) {
            playSound(at: url)
            return
        }

        playSystemAlarmSound()
    }

    private func librarySoundsUrl(forName name: String) -> URL? {
        guard let library = FileManager.default.urls(for: .libraryDirectory, in: .userDomainMask).first else {
            return nil
        }
        let base = library.appendingPathComponent("Sounds").appendingPathComponent(name)
        for url in [base.appendingPathExtension("mp3"), base.appendingPathExtension("wav"), base] {
            if FileManager.default.fileExists(atPath: url.path) {
                return url
            }
        }
        return nil
    }

    private func playSound(at url: URL) {
        do {
            audioPlayer = try AVAudioPlayer(contentsOf: url)
            audioPlayer?.numberOfLoops = -1
            audioPlayer?.volume = 1.0
            audioPlayer?.play()
        } catch {
            print("Error playing alarm sound: \(error)")
            playSystemAlarmSound()
        }
    }

    private func playSystemAlarmSound() {
        AudioServicesPlaySystemSound(SystemSoundID(1005))
        soundTimer = Timer.scheduledTimer(withTimeInterval: 2.0, repeats: true) { _ in
            AudioServicesPlaySystemSound(SystemSoundID(1005))
        }
    }

    private func stopAlarmSound() {
        audioPlayer?.stop()
        audioPlayer = nil
        soundTimer?.invalidate()
        soundTimer = nil
    }

    // MARK: - Vibration

    private func startVibration() {
        let generator = UIImpactFeedbackGenerator(style: .heavy)
        generator.prepare()
        animationTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            generator.impactOccurred()
        }
    }

    private func stopVibration() {
        animationTimer?.invalidate()
        animationTimer = nil
    }
}
