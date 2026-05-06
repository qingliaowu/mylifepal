import Foundation

struct WatchHabit: Identifiable, Codable, Equatable {
    var id = UUID()
    var name: String
    var tinyAction: String
    var lastCompleted = ""
    var xp: Int
    var coins: Int
    var streak = 0
    var bestStreak = 0
    var completions = 0
}

@MainActor
final class WatchHabitStore: ObservableObject {
    @Published private(set) var totalXp: Int = 0
    @Published private(set) var coins: Int = 10
    @Published private(set) var habits: [WatchHabit] = []
    @Published private(set) var timerRunning = false
    @Published private(set) var timerRemaining = 25 * 60
    @Published private(set) var focusSessions = 0
    @Published private(set) var moodDate = ""
    @Published private(set) var moodScore = 3
    @Published private(set) var moodCheckins = 0

    private let stateKey = "mylifepal_watchos_state"
    private let calendar = Calendar.current
    private let focusDuration = 25 * 60
    private var timerEndTimestamp: TimeInterval = 0

    init() {
        load()
    }

    var level: Int {
        totalXp / 100 + 1
    }

    var completedToday: Int {
        habits.filter(isCompletedToday).count
    }

    var nextHabit: WatchHabit? {
        habits.first { !isCompletedToday($0) }
    }

    var timerProgress: Double {
        1 - Double(timerRemaining) / Double(max(1, focusDuration))
    }

    var timerDisplay: String {
        let minutes = timerRemaining / 60
        let seconds = timerRemaining % 60
        return String(format: "%02d:%02d", minutes, seconds)
    }

    var hasMoodToday: Bool {
        moodDate == todayString
    }

    var moodText: String {
        if !hasMoodToday {
            return "How are you?"
        }
        if moodScore <= 2 {
            return "Low"
        }
        if moodScore == 3 {
            return "Okay"
        }
        return "Good"
    }

    func complete(_ habit: WatchHabit) {
        guard let index = habits.firstIndex(where: { $0.id == habit.id }) else {
            return
        }
        guard !isCompletedToday(habits[index]) else {
            return
        }

        let continued = habits[index].lastCompleted == dateString(for: calendar.date(byAdding: .day, value: -1, to: Date()) ?? Date())
        habits[index].streak = continued ? habits[index].streak + 1 : 1
        habits[index].bestStreak = max(habits[index].bestStreak, habits[index].streak)
        habits[index].lastCompleted = todayString
        habits[index].completions += 1

        let gain = habits[index].xp + min(10, habits[index].streak * 2)
        totalXp += gain
        coins += habits[index].coins
        save()
    }

    func startTimer() {
        let remaining = max(1, timerRemaining)
        timerEndTimestamp = Date().addingTimeInterval(TimeInterval(remaining)).timeIntervalSince1970
        timerRunning = true
        save()
    }

    func pauseTimer() {
        syncTimer()
        timerRunning = false
        timerEndTimestamp = 0
        save()
    }

    func resetTimer() {
        timerRunning = false
        timerEndTimestamp = 0
        timerRemaining = focusDuration
        save()
    }

    func tickTimer() {
        guard timerRunning else {
            return
        }
        syncTimer()
    }

    func recordMood(_ score: Int) {
        let newDay = moodDate != todayString
        moodDate = todayString
        moodScore = score
        if newDay {
            moodCheckins += 1
            totalXp += 5
            coins += 3
        }
        save()
    }

    func isCompletedToday(_ habit: WatchHabit) -> Bool {
        habit.lastCompleted == todayString
    }

    private func load() {
        guard let data = UserDefaults.standard.data(forKey: stateKey) else {
            loadDefaults()
            return
        }
        do {
            let state = try JSONDecoder().decode(WatchState.self, from: data)
            totalXp = state.totalXp
            coins = state.coins
            habits = state.habits.isEmpty ? defaultHabits : state.habits
            timerRunning = state.timerRunning
            timerRemaining = state.timerRemaining <= 0 ? focusDuration : state.timerRemaining
            focusSessions = state.focusSessions
            timerEndTimestamp = state.timerEndTimestamp
            moodDate = state.moodDate
            moodScore = state.moodScore
            moodCheckins = state.moodCheckins
            syncTimer()
        } catch {
            loadDefaults()
        }
    }

    private func save() {
        let state = WatchState(
            totalXp: totalXp,
            coins: coins,
            habits: habits,
            timerRunning: timerRunning,
            timerRemaining: timerRemaining,
            timerEndTimestamp: timerEndTimestamp,
            focusSessions: focusSessions,
            moodDate: moodDate,
            moodScore: moodScore,
            moodCheckins: moodCheckins
        )
        guard let data = try? JSONEncoder().encode(state) else {
            return
        }
        UserDefaults.standard.set(data, forKey: stateKey)
    }

    private func loadDefaults() {
        totalXp = 0
        coins = 10
        habits = defaultHabits
        timerRunning = false
        timerRemaining = focusDuration
        timerEndTimestamp = 0
        focusSessions = 0
        moodDate = ""
        moodScore = 3
        moodCheckins = 0
        save()
    }

    private func syncTimer() {
        guard timerRunning else {
            return
        }
        let remaining = Int(ceil(timerEndTimestamp - Date().timeIntervalSince1970))
        if remaining <= 0 {
            completeTimer()
        } else {
            timerRemaining = remaining
        }
    }

    private func completeTimer() {
        timerRunning = false
        timerEndTimestamp = 0
        timerRemaining = focusDuration
        focusSessions += 1
        totalXp += 12
        coins += 8
        save()
    }

    private var defaultHabits: [WatchHabit] {
        [
            WatchHabit(name: "Hydrate", tinyAction: "Drink one glass", xp: 8, coins: 5),
            WatchHabit(name: "Walk", tinyAction: "Walk for 2 minutes", xp: 12, coins: 8),
            WatchHabit(name: "Breathe", tinyAction: "Take 5 slow breaths", xp: 8, coins: 5)
        ]
    }

    private var todayString: String {
        dateString(for: Date())
    }

    private func dateString(for date: Date) -> String {
        let components = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", components.year ?? 0, components.month ?? 0, components.day ?? 0)
    }
}

private struct WatchState: Codable {
    var totalXp: Int
    var coins: Int
    var habits: [WatchHabit]
    var timerRunning: Bool = false
    var timerRemaining: Int = 25 * 60
    var timerEndTimestamp: TimeInterval = 0
    var focusSessions: Int = 0
    var moodDate: String = ""
    var moodScore: Int = 3
    var moodCheckins: Int = 0
}
