import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var store: WatchHabitStore
    private let ticker = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                header

                if let nextHabit = store.nextHabit {
                    focusCard(nextHabit)
                } else {
                    clearedCard
                }

                timerCard

                moodCard

                HStack(spacing: 8) {
                    stat("\(store.completedToday)/\(store.habits.count)", "today")
                    stat("\(store.totalXp)", "XP")
                }

                ForEach(store.habits) { habit in
                    habitRow(habit)
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 12)
        }
        .background(Color(red: 0.06, green: 0.09, blue: 0.08))
        .onReceive(ticker) { _ in
            store.tickTimer()
        }
    }

    private var header: some View {
        VStack(spacing: 8) {
            Text("MyLifePal")
                .font(.system(size: 24, weight: .bold))

            Text("Lv \(store.level)  |  \(store.coins) coins")
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(Color.black)
                .padding(.horizontal, 12)
                .padding(.vertical, 7)
                .background(Color(red: 0.98, green: 0.78, blue: 0.31))
                .clipShape(Capsule())

            ProgressView(value: Double(store.totalXp % 100), total: 100)
                .tint(Color(red: 0.98, green: 0.78, blue: 0.31))
        }
    }

    private func focusCard(_ habit: WatchHabit) -> some View {
        VStack(spacing: 8) {
            Text("Next tiny action")
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(Color(red: 0.98, green: 0.78, blue: 0.31))

            Text(habit.name)
                .font(.system(size: 21, weight: .bold))
                .multilineTextAlignment(.center)

            Text(habit.tinyAction)
                .font(.system(size: 15))
                .foregroundStyle(Color(red: 0.72, green: 0.78, blue: 0.74))
                .multilineTextAlignment(.center)

            Button("Complete") {
                store.complete(habit)
            }
            .buttonStyle(.borderedProminent)
            .tint(Color(red: 0.27, green: 0.63, blue: 0.51))
        }
        .cardStyle()
    }

    private var clearedCard: some View {
        VStack(spacing: 6) {
            Text("Today cleared")
                .font(.system(size: 21, weight: .bold))
            Text("Your tiny votes are in.")
                .font(.system(size: 15))
                .foregroundStyle(Color(red: 0.72, green: 0.78, blue: 0.74))
        }
        .cardStyle()
    }

    private var moodCard: some View {
        VStack(spacing: 8) {
            Text("Emotion check-in")
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(Color(red: 0.98, green: 0.78, blue: 0.31))

            Text(store.moodText)
                .font(.system(size: 21, weight: .bold))

            Text("\(store.moodCheckins) check-ins")
                .font(.system(size: 12))
                .foregroundStyle(Color(red: 0.72, green: 0.78, blue: 0.74))

            HStack(spacing: 6) {
                Button("Low") {
                    store.recordMood(2)
                }
                .buttonStyle(.bordered)
                .tint(Color(red: 0.84, green: 0.55, blue: 0.25))

                Button("Okay") {
                    store.recordMood(3)
                }
                .buttonStyle(.bordered)
                .tint(Color(red: 0.98, green: 0.78, blue: 0.31))

                Button("Good") {
                    store.recordMood(4)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color(red: 0.27, green: 0.63, blue: 0.51))
            }
        }
        .cardStyle()
    }

    private var timerCard: some View {
        VStack(spacing: 8) {
            Text("Tomato Timer")
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(Color(red: 0.98, green: 0.78, blue: 0.31))

            Text(store.timerDisplay)
                .font(.system(size: 28, weight: .bold))

            Text(store.timerRunning ? "Focus running" : "\(store.focusSessions) tomatoes done")
                .font(.system(size: 12))
                .foregroundStyle(Color(red: 0.72, green: 0.78, blue: 0.74))

            ProgressView(value: store.timerProgress, total: 1)
                .tint(Color(red: 0.98, green: 0.78, blue: 0.31))

            HStack(spacing: 8) {
                Button(store.timerRunning ? "Pause" : "Start") {
                    if store.timerRunning {
                        store.pauseTimer()
                    } else {
                        store.startTimer()
                    }
                }
                .buttonStyle(.borderedProminent)
                .tint(store.timerRunning ? Color(red: 0.11, green: 0.16, blue: 0.13) : Color(red: 0.27, green: 0.63, blue: 0.51))

                Button("Reset") {
                    store.resetTimer()
                }
                .buttonStyle(.bordered)
                .tint(Color(red: 0.98, green: 0.78, blue: 0.31))
            }
        }
        .cardStyle()
    }

    private func stat(_ value: String, _ label: String) -> some View {
        VStack(spacing: 2) {
            Text(value)
                .font(.system(size: 16, weight: .bold))
            Text(label)
                .font(.system(size: 11))
                .foregroundStyle(Color(red: 0.72, green: 0.78, blue: 0.74))
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
        .background(Color(red: 0.11, green: 0.16, blue: 0.13))
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    private func habitRow(_ habit: WatchHabit) -> some View {
        VStack(spacing: 7) {
            Text(habit.name)
                .font(.system(size: 17, weight: .bold))
                .multilineTextAlignment(.center)

            Text("\(habit.streak)d streak  |  +\(habit.xp) XP")
                .font(.system(size: 12))
                .foregroundStyle(Color(red: 0.72, green: 0.78, blue: 0.74))

            if !store.isCompletedToday(habit) {
                Button("Done") {
                    store.complete(habit)
                }
                .buttonStyle(.bordered)
                .tint(Color(red: 0.98, green: 0.78, blue: 0.31))
            }
        }
        .cardStyle(completed: store.isCompletedToday(habit))
    }
}

private extension View {
    func cardStyle(completed: Bool = false) -> some View {
        self
            .frame(maxWidth: .infinity)
            .padding(14)
            .background(completed ? Color(red: 0.14, green: 0.23, blue: 0.18) : Color(red: 0.11, green: 0.16, blue: 0.13))
            .clipShape(RoundedRectangle(cornerRadius: 18))
            .overlay {
                RoundedRectangle(cornerRadius: 18)
                    .stroke(Color(red: 0.22, green: 0.29, blue: 0.25), lineWidth: 1)
            }
    }
}
