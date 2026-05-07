import AppKit
import Foundation
import SwiftUI
import UniformTypeIdentifiers
import UserNotifications

enum MacSection: String, CaseIterable, Identifiable {
    case today = "Today"
    case timer = "Timer"
    case mood = "Mood"
    case habits = "Habits"
    case rewards = "Rewards"
    case progress = "Progress"
    case appearance = "Appearance"

    var id: String { rawValue }

    var symbol: String {
        switch self {
        case .today: return "sun.max"
        case .timer: return "timer"
        case .mood: return "heart.text.square"
        case .habits: return "checklist"
        case .rewards: return "gift"
        case .progress: return "chart.line.uptrend.xyaxis"
        case .appearance: return "paintpalette"
        }
    }
}

enum TimerMode: Int, CaseIterable, Codable, Identifiable {
    case focus
    case shortBreak
    case longBreak

    var id: Int { rawValue }

    var title: String {
        switch self {
        case .focus: return "Focus tomato"
        case .shortBreak: return "Short break"
        case .longBreak: return "Long break"
        }
    }

    var duration: TimeInterval {
        switch self {
        case .focus: return 25 * 60
        case .shortBreak: return 5 * 60
        case .longBreak: return 15 * 60
        }
    }
}

struct MacTheme: Codable, Equatable, Identifiable {
    var id: String { name }
    var name: String
    var primary: String
    var accent: String
    var background: String
}

struct MacHabit: Identifiable, Codable, Equatable {
    var id = UUID()
    var name = ""
    var icon = "*"
    var cue = ""
    var tinyAction = ""
    var identity = ""
    var reward = ""
    var attribute = "Mind"
    var lastCompleted = ""
    var reminderEnabled = false
    var reminderTime = "09:00"
    var xp = 8
    var coins = 5
    var streak = 0
    var bestStreak = 0
    var completions = 0
}

struct MacReward: Identifiable, Codable, Equatable {
    var id = UUID()
    var title = ""
    var cost = 40
    var claimedCount = 0
}

struct MacMoodEntry: Identifiable, Codable, Equatable {
    var id: String { date }
    var date = ""
    var mood = 3
    var energy = 3
    var stress = 3
    var note = ""
}

struct MacLifeState: Codable, Equatable {
    var schemaVersion = 1
    var totalXp = 0
    var coins = 25
    var gems = 1
    var monsterXp = 0
    var monsterBond = 12
    var theme = MacLifeStore.themePresets[0]
    var timerMode = TimerMode.focus
    var timerDuration: TimeInterval = TimerMode.focus.duration
    var timerRemaining: TimeInterval = TimerMode.focus.duration
    var timerEndAt: TimeInterval = 0
    var timerRunning = false
    var tomatoFocusSessions = 0
    var tomatoBreakSessions = 0
    var tomatoSessionsToday = 0
    var tomatoMinutesToday = 0
    var tomatoLastDate = ""
    var rewardClaimsToday = 0
    var rewardLastDate = ""
    var monsterName = "Milo"
    var monsterLastCareDate = ""
    var habits: [MacHabit] = []
    var rewards: [MacReward] = []
    var moodEntries: [MacMoodEntry] = []
    var inventory: [String] = []
    var claimedQuestKeys: [String] = []
    var attributeXp: [String: Int] = [:]
}

struct MacBackupFile: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    var text: String

    init(text: String = "") {
        self.text = text
    }

    init(configuration: ReadConfiguration) throws {
        guard let data = configuration.file.regularFileContents,
              let value = String(data: data, encoding: .utf8) else {
            throw CocoaError(.fileReadCorruptFile)
        }
        text = value
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: Data(text.utf8))
    }
}

@MainActor
final class MacLifeStore: ObservableObject {
    nonisolated static let attributes = ["Mind", "Body", "Craft", "Home", "Social"]
    nonisolated static let reminderTimes = ["07:00", "08:00", "09:00", "12:30", "17:30", "19:30", "21:30"]
    nonisolated static let themePresets = [
        MacTheme(name: "Forest", primary: "#2E7D68", accent: "#F9C74F", background: "#F5F7F1"),
        MacTheme(name: "Ocean", primary: "#1F6997", accent: "#42BEB6", background: "#F1F7F9"),
        MacTheme(name: "Berry", primary: "#814584", accent: "#EA7E65", background: "#F9F4F8"),
        MacTheme(name: "Sunrise", primary: "#B65C38", accent: "#F5B84A", background: "#FAF6F0"),
        MacTheme(name: "Graphite", primary: "#4B5B69", accent: "#51A39A", background: "#F5F6F7"),
        MacTheme(name: "Rose", primary: "#AF4B6A", accent: "#4E9C85", background: "#FBF5F7")
    ]

    @Published private(set) var state = MacLifeState()
    @Published var showHabitComposer = false
    @Published var showRewardComposer = false
    @Published var lastMessage = ""

    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()
    private let calendar = Calendar.current
    private let stateURL: URL

    init() {
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? FileManager.default.temporaryDirectory
        let folder = support.appendingPathComponent("MyLifePal", isDirectory: true)
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        stateURL = folder.appendingPathComponent("mylifepal-mac-state.json")
        load()
        requestNotificationPermission()
        scheduleHabitNotifications()
        scheduleTimerNotificationIfNeeded()
    }

    var today: String {
        dateString(Date())
    }

    var level: Int {
        state.totalXp / 100 + 1
    }

    var xpIntoLevel: Int {
        state.totalXp % 100
    }

    var completedToday: Int {
        state.habits.filter(isCompletedToday).count
    }

    var nextHabit: MacHabit? {
        state.habits.first { !isCompletedToday($0) }
    }

    var todayMood: MacMoodEntry? {
        state.moodEntries.first { $0.date == today }
    }

    var timerRemaining: TimeInterval {
        if state.timerRunning {
            return max(0, state.timerEndAt - Date().timeIntervalSince1970)
        }
        return max(0, state.timerRemaining)
    }

    var timerProgress: Double {
        let duration = max(1, state.timerDuration)
        return min(1, max(0, (duration - timerRemaining) / duration))
    }

    var timerDisplay: String {
        let seconds = Int(ceil(timerRemaining))
        return String(format: "%02d:%02d", seconds / 60, seconds % 60)
    }

    var primaryColor: Color {
        Color(hex: state.theme.primary)
    }

    var accentColor: Color {
        Color(hex: state.theme.accent)
    }

    var backgroundColor: Color {
        Color(hex: state.theme.background)
    }

    var companionLevel: Int {
        state.monsterXp / 90 + 1
    }

    var companionStage: String {
        if companionLevel >= 8 { return "Mythic" }
        if companionLevel >= 5 { return "Guardian" }
        if companionLevel >= 3 { return "Bloomling" }
        return "Hatchling"
    }

    var identityLine: String {
        if state.habits.isEmpty {
            return "Create one tiny loop to start."
        }
        if completedToday == state.habits.count {
            return "Today is clear. Your votes are in."
        }
        return "Tiny habits. Real-life levels."
    }

    var backupDocument: MacBackupFile {
        let backup = MacBackup(schema: "mylifepal.mac.backup", schemaVersion: 1, appName: "MyLifePal", exportedAt: ISO8601DateFormatter().string(from: Date()), state: state)
        let data = (try? encoder.encode(backup)) ?? Data()
        return MacBackupFile(text: String(data: data, encoding: .utf8) ?? "{}")
    }

    func tickTimer() {
        guard state.timerRunning else { return }
        if timerRemaining <= 0 {
            completeTimer()
        } else {
            objectWillChange.send()
        }
    }

    func isCompletedToday(_ habit: MacHabit) -> Bool {
        habit.lastCompleted == today
    }

    func complete(_ habit: MacHabit) {
        guard let index = state.habits.firstIndex(where: { $0.id == habit.id }) else { return }
        guard !isCompletedToday(state.habits[index]) else { return }

        let previous = state.habits[index].lastCompleted
        let continued = previous == dateString(calendar.date(byAdding: .day, value: -1, to: Date()) ?? Date())
        state.habits[index].streak = continued ? state.habits[index].streak + 1 : 1
        state.habits[index].bestStreak = max(state.habits[index].bestStreak, state.habits[index].streak)
        state.habits[index].lastCompleted = today
        state.habits[index].completions += 1

        let xpGain = state.habits[index].xp + min(10, state.habits[index].streak * 2)
        state.totalXp += xpGain
        state.coins += state.habits[index].coins
        addAttributeXp(state.habits[index].attribute, xpGain)
        growCompanion(8)
        maybeAwardClearDayGem()
        save(message: "+\(xpGain) XP, +\(state.habits[index].coins) coins")
    }

    func addHabit(_ habit: MacHabit) {
        var value = habit
        value.name = fallback(value.name, "Tiny habit")
        value.cue = fallback(value.cue, "After an existing routine")
        value.tinyAction = fallback(value.tinyAction, "do the 2-minute version")
        value.identity = fallback(value.identity, "I keep promises to myself.")
        value.reward = fallback(value.reward, "Pause and enjoy the win")
        state.habits.append(value)
        save(message: "Habit added")
    }

    func deleteHabit(_ habit: MacHabit) {
        state.habits.removeAll { $0.id == habit.id }
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: ["habit-\(habit.id.uuidString)"])
        save(message: "Habit deleted")
    }

    func addReward(_ reward: MacReward) {
        var value = reward
        value.title = fallback(value.title, "Reward")
        value.cost = max(1, value.cost)
        state.rewards.append(value)
        save(message: "Reward added")
    }

    func claimReward(_ reward: MacReward) {
        guard let index = state.rewards.firstIndex(where: { $0.id == reward.id }),
              state.coins >= state.rewards[index].cost else {
            return
        }
        state.coins -= state.rewards[index].cost
        state.rewards[index].claimedCount += 1
        state.rewardClaimsToday += 1
        state.rewardLastDate = today
        state.inventory.append("\(today): \(state.rewards[index].title)")
        state.monsterBond = min(100, state.monsterBond + 5)
        save(message: "Reward claimed")
    }

    func deleteReward(_ reward: MacReward) {
        state.rewards.removeAll { $0.id == reward.id }
        save(message: "Reward deleted")
    }

    func recordMood(mood: Int, energy: Int, stress: Int, note: String) {
        let entry = MacMoodEntry(date: today, mood: clamp(mood), energy: clamp(energy), stress: clamp(stress), note: note)
        if let index = state.moodEntries.firstIndex(where: { $0.date == today }) {
            state.moodEntries[index] = entry
            save(message: "Mood updated")
        } else {
            state.moodEntries.append(entry)
            state.totalXp += 8
            state.coins += 5
            addAttributeXp("Mind", 8)
            growCompanion(6)
            save(message: "+8 XP, +5 coins")
        }
    }

    func startTimer(mode: TimerMode? = nil) {
        ensureTimerDay()
        if let mode {
            state.timerMode = mode
            state.timerDuration = mode.duration
            state.timerRemaining = mode.duration
        }
        let remaining = state.timerRemaining > 0 ? state.timerRemaining : state.timerMode.duration
        state.timerDuration = state.timerMode.duration
        state.timerRemaining = remaining
        state.timerEndAt = Date().timeIntervalSince1970 + remaining
        state.timerRunning = true
        save(message: "Timer started")
    }

    func pauseTimer() {
        state.timerRemaining = timerRemaining
        state.timerEndAt = 0
        state.timerRunning = false
        save(message: "Timer paused")
    }

    func resetTimer() {
        state.timerDuration = state.timerMode.duration
        state.timerRemaining = state.timerDuration
        state.timerEndAt = 0
        state.timerRunning = false
        save(message: "Timer reset")
    }

    func selectTimerMode(_ mode: TimerMode) {
        state.timerMode = mode
        state.timerDuration = mode.duration
        state.timerRemaining = mode.duration
        state.timerRunning = false
        state.timerEndAt = 0
        save(message: "Timer mode changed")
    }

    func applyTheme(_ theme: MacTheme) {
        state.theme = theme
        save(message: "\(theme.name) colors applied")
    }

    func saveCustomTheme(primary: String, accent: String, background: String) {
        guard primary.isHexColor, accent.isHexColor, background.isHexColor else {
            lastMessage = "Use #RRGGBB colors"
            return
        }
        state.theme = MacTheme(name: "Custom", primary: primary.uppercased(), accent: accent.uppercased(), background: background.uppercased())
        save(message: "Custom colors saved")
    }

    func importBackup(document: MacBackupFile) {
        guard let data = document.text.data(using: .utf8) else {
            lastMessage = "Import failed"
            return
        }
        do {
            if let backup = try? decoder.decode(MacBackup.self, from: data) {
                state = normalize(backup.state)
            } else if let android = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                state = normalize(try stateFromJSONObject(android))
            } else {
                state = normalize(try decoder.decode(MacLifeState.self, from: data))
            }
            save(message: "Backup imported")
        } catch {
            lastMessage = "Import failed"
        }
    }

    func revealDataFolder() {
        NSWorkspace.shared.activateFileViewerSelecting([stateURL])
    }

    private func load() {
        guard let data = try? Data(contentsOf: stateURL),
              let decoded = try? decoder.decode(MacLifeState.self, from: data) else {
            state = defaultState()
            save(message: "")
            return
        }
        state = normalize(decoded)
        syncTimerOnLoad()
    }

    private func save(message: String) {
        state = normalize(state)
        guard let data = try? encoder.encode(state) else { return }
        try? data.write(to: stateURL, options: [.atomic])
        scheduleHabitNotifications()
        scheduleTimerNotificationIfNeeded()
        if !message.isEmpty {
            lastMessage = message
        }
    }

    private func completeTimer() {
        let wasFocus = state.timerMode == .focus
        state.timerRunning = false
        state.timerEndAt = 0
        state.timerRemaining = 0
        ensureTimerDay()
        if wasFocus {
            state.tomatoFocusSessions += 1
            state.tomatoSessionsToday += 1
            state.tomatoMinutesToday += Int(TimerMode.focus.duration / 60)
            state.totalXp += 18
            state.coins += 12
            addAttributeXp("Mind", 18)
            growCompanion(12)
        } else {
            state.tomatoBreakSessions += 1
        }
        save(message: wasFocus ? "+18 XP, +12 coins" : "Break complete")
    }

    private func syncTimerOnLoad() {
        if state.timerRunning {
            let remaining = state.timerEndAt - Date().timeIntervalSince1970
            if remaining <= 0 {
                completeTimer()
            } else {
                state.timerRemaining = remaining
            }
        }
    }

    private func ensureTimerDay() {
        if state.tomatoLastDate != today {
            state.tomatoLastDate = today
            state.tomatoSessionsToday = 0
            state.tomatoMinutesToday = 0
        }
        if state.rewardLastDate != today {
            state.rewardLastDate = today
            state.rewardClaimsToday = 0
        }
    }

    private func maybeAwardClearDayGem() {
        guard !state.habits.isEmpty, completedToday == state.habits.count else { return }
        let key = "clear:\(today)"
        if !state.claimedQuestKeys.contains(key) {
            state.claimedQuestKeys.append(key)
            state.gems += 1
            state.inventory.append("\(today): Clear-day gem")
        }
    }

    private func addAttributeXp(_ attribute: String, _ amount: Int) {
        state.attributeXp[attribute, default: 0] += amount
    }

    private func growCompanion(_ amount: Int) {
        state.monsterXp += amount
        state.monsterBond = min(100, state.monsterBond + max(2, amount / 5))
    }

    private func normalize(_ value: MacLifeState) -> MacLifeState {
        var normalized = value
        if normalized.habits.isEmpty {
            normalized.habits = defaultHabits
        }
        if normalized.rewards.isEmpty {
            normalized.rewards = defaultRewards
        }
        if normalized.tomatoLastDate.isEmpty {
            normalized.tomatoLastDate = today
        }
        if normalized.rewardLastDate.isEmpty {
            normalized.rewardLastDate = today
        }
        if !normalized.theme.primary.isHexColor {
            normalized.theme.primary = Self.themePresets[0].primary
        }
        if !normalized.theme.accent.isHexColor {
            normalized.theme.accent = Self.themePresets[0].accent
        }
        if !normalized.theme.background.isHexColor {
            normalized.theme.background = Self.themePresets[0].background
        }
        for attribute in Self.attributes where normalized.attributeXp[attribute] == nil {
            normalized.attributeXp[attribute] = 0
        }
        return normalized
    }

    private func defaultState() -> MacLifeState {
        var value = MacLifeState()
        value.habits = defaultHabits
        value.rewards = defaultRewards
        value.tomatoLastDate = today
        value.rewardLastDate = today
        for attribute in Self.attributes {
            value.attributeXp[attribute] = 0
        }
        return value
    }

    private var defaultHabits: [MacHabit] {
        [
            MacHabit(name: "Hydrate before coffee", icon: "*", cue: "After I start the kettle", tinyAction: "drink one glass of water", identity: "I protect my energy.", reward: "Make coffee after the glass is empty", attribute: "Body", xp: 8, coins: 5),
            MacHabit(name: "Read one page", icon: "*", cue: "After I sit down at night", tinyAction: "read one page", identity: "I am a reader, even on busy days.", reward: "Put a coin toward a book reward", attribute: "Mind", xp: 8, coins: 5),
            MacHabit(name: "One-surface reset", icon: "*", cue: "After dinner", tinyAction: "move five items back home", identity: "I make my space easier to live in.", reward: "Make tea or light a candle", attribute: "Home", xp: 14, coins: 9)
        ]
    }

    private var defaultRewards: [MacReward] {
        [
            MacReward(title: "Fancy coffee or tea", cost: 35),
            MacReward(title: "Guilt-free game session", cost: 75),
            MacReward(title: "Book fund deposit", cost: 120)
        ]
    }

    private func fallback(_ value: String, _ defaultValue: String) -> String {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? defaultValue : trimmed
    }

    private func clamp(_ value: Int) -> Int {
        min(5, max(1, value))
    }

    private func dateString(_ date: Date) -> String {
        let components = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", components.year ?? 0, components.month ?? 0, components.day ?? 0)
    }

    private func requestNotificationPermission() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
    }

    private func scheduleHabitNotifications() {
        let center = UNUserNotificationCenter.current()
        let identifiers = state.habits.map { "habit-\($0.id.uuidString)" }
        center.removePendingNotificationRequests(withIdentifiers: identifiers)
        for habit in state.habits where habit.reminderEnabled {
            let parts = habit.reminderTime.split(separator: ":").compactMap { Int($0) }
            guard parts.count == 2 else { continue }
            var components = DateComponents()
            components.hour = parts[0]
            components.minute = parts[1]
            let content = UNMutableNotificationContent()
            content.title = "MyLifePal reminder"
            content.body = "\(habit.cue): \(habit.tinyAction)"
            content.subtitle = habit.reward
            let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: true)
            let request = UNNotificationRequest(identifier: "habit-\(habit.id.uuidString)", content: content, trigger: trigger)
            center.add(request)
        }
    }

    private func scheduleTimerNotificationIfNeeded() {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: ["timer-complete"])
        guard state.timerRunning, timerRemaining > 1 else { return }
        let content = UNMutableNotificationContent()
        content.title = state.timerMode == .focus ? "Focus tomato complete" : "Break complete"
        content.body = state.timerMode == .focus ? "+18 XP, +12 coins, and companion growth." : "Ready for the next tiny action."
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: max(1, timerRemaining), repeats: false)
        center.add(UNNotificationRequest(identifier: "timer-complete", content: content, trigger: trigger))
    }

    private func stateFromJSONObject(_ object: [String: Any]) throws -> MacLifeState {
        let payload = object["state"] as? [String: Any] ?? object
        var result = defaultState()
        result.totalXp = payload["totalXp"] as? Int ?? result.totalXp
        result.coins = payload["coins"] as? Int ?? result.coins
        result.gems = payload["gems"] as? Int ?? result.gems
        result.monsterXp = payload["monsterXp"] as? Int ?? result.monsterXp
        result.monsterBond = payload["monsterBond"] as? Int ?? result.monsterBond
        result.theme = MacTheme(
            name: payload["themeName"] as? String ?? "Imported",
            primary: intColorToHex(payload["themePrimary"], fallback: result.theme.primary),
            accent: intColorToHex(payload["themeAccent"], fallback: result.theme.accent),
            background: intColorToHex(payload["themeBackground"], fallback: result.theme.background)
        )
        if let habits = payload["habits"] as? [[String: Any]] {
            result.habits = habits.map { item in
                MacHabit(
                    id: UUID(uuidString: item["id"] as? String ?? "") ?? UUID(),
                    name: item["name"] as? String ?? "Tiny habit",
                    icon: item["icon"] as? String ?? "*",
                    cue: item["cue"] as? String ?? "",
                    tinyAction: item["tinyAction"] as? String ?? "",
                    identity: item["identity"] as? String ?? "",
                    reward: item["reward"] as? String ?? "",
                    attribute: item["attribute"] as? String ?? "Mind",
                    lastCompleted: item["lastCompleted"] as? String ?? "",
                    reminderEnabled: item["reminderEnabled"] as? Bool ?? false,
                    reminderTime: item["reminderTime"] as? String ?? "09:00",
                    xp: item["xp"] as? Int ?? 8,
                    coins: item["coins"] as? Int ?? 5,
                    streak: item["streak"] as? Int ?? 0,
                    bestStreak: item["bestStreak"] as? Int ?? 0,
                    completions: item["completions"] as? Int ?? 0
                )
            }
        }
        if let rewards = payload["rewards"] as? [[String: Any]] {
            result.rewards = rewards.map { item in
                MacReward(
                    id: UUID(uuidString: item["id"] as? String ?? "") ?? UUID(),
                    title: item["title"] as? String ?? "Reward",
                    cost: item["cost"] as? Int ?? 40,
                    claimedCount: item["claimedCount"] as? Int ?? 0
                )
            }
        }
        if let moods = payload["moodEntries"] as? [[String: Any]] {
            result.moodEntries = moods.map { item in
                MacMoodEntry(date: item["date"] as? String ?? today, mood: item["mood"] as? Int ?? 3, energy: item["energy"] as? Int ?? 3, stress: item["stress"] as? Int ?? 3, note: item["note"] as? String ?? "")
            }
        }
        if let inventory = payload["inventory"] as? [String] {
            result.inventory = inventory
        }
        return result
    }

    private func intColorToHex(_ value: Any?, fallback: String) -> String {
        guard let intValue = value as? Int else { return fallback }
        let rgb = intValue & 0xFFFFFF
        return String(format: "#%06X", rgb)
    }
}

private struct MacBackup: Codable {
    var schema: String
    var schemaVersion: Int
    var appName: String
    var exportedAt: String
    var state: MacLifeState
}

extension String {
    var isHexColor: Bool {
        range(of: "^#[0-9A-Fa-f]{6}$", options: .regularExpression) != nil
    }
}

extension Color {
    init(hex: String) {
        let value = hex.isHexColor ? String(hex.dropFirst()) : "2E7D68"
        let scanner = Scanner(string: value)
        var rgb: UInt64 = 0
        scanner.scanHexInt64(&rgb)
        self.init(
            red: Double((rgb >> 16) & 0xFF) / 255.0,
            green: Double((rgb >> 8) & 0xFF) / 255.0,
            blue: Double(rgb & 0xFF) / 255.0
        )
    }
}
