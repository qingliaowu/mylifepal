import SwiftUI
import UniformTypeIdentifiers

struct ContentView: View {
    @EnvironmentObject private var store: MacLifeStore
    @State private var selectedSection: MacSection = .today
    @State private var exportingBackup = false
    @State private var importingBackup = false
    @State private var showingRewardComposer = false
    private let ticker = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    var body: some View {
        NavigationSplitView {
            sidebar
        } detail: {
            ZStack {
                store.backgroundColor.opacity(0.7).ignoresSafeArea()
                ScrollView {
                    VStack(alignment: .leading, spacing: 18) {
                        header
                        content
                    }
                    .padding(24)
                    .frame(maxWidth: .infinity, alignment: .topLeading)
                }
            }
            .toolbar {
                ToolbarItemGroup {
                    Button {
                        store.showHabitComposer = true
                    } label: {
                        Label("Add Habit", systemImage: "plus")
                    }

                    Button {
                        exportingBackup = true
                    } label: {
                        Label("Export", systemImage: "square.and.arrow.up")
                    }

                    Button {
                        importingBackup = true
                    } label: {
                        Label("Import", systemImage: "square.and.arrow.down")
                    }
                }
            }
        }
        .tint(store.primaryColor)
        .onReceive(ticker) { _ in
            store.tickTimer()
        }
        .sheet(isPresented: $store.showHabitComposer) {
            HabitComposerView()
                .environmentObject(store)
                .frame(minWidth: 520, minHeight: 680)
        }
        .sheet(isPresented: $showingRewardComposer) {
            RewardComposerView()
                .environmentObject(store)
                .frame(width: 460, height: 260)
        }
        .fileExporter(
            isPresented: $exportingBackup,
            document: store.backupDocument,
            contentType: .json,
            defaultFilename: "mylifepal-mac-backup-\(store.today).json"
        ) { result in
            if case .failure = result {
                store.lastMessage = "Export failed"
            }
        }
        .fileImporter(isPresented: $importingBackup, allowedContentTypes: [.json]) { result in
            switch result {
            case .success(let url):
                if let text = try? String(contentsOf: url, encoding: .utf8) {
                    store.importBackup(document: MacBackupFile(text: text))
                } else {
                    store.lastMessage = "Import failed"
                }
            case .failure:
                store.lastMessage = "Import failed"
            }
        }
    }

    private var sidebar: some View {
        List(MacSection.allCases, selection: $selectedSection) { section in
            Label(section.rawValue, systemImage: section.symbol)
                .tag(section)
        }
        .navigationTitle("MyLifePal")
        .safeAreaInset(edge: .bottom) {
            VStack(alignment: .leading, spacing: 8) {
                Text("Lv \(store.level)")
                    .font(.title2.weight(.bold))
                Text("\(store.state.coins) coins  |  \(store.state.gems) gems")
                    .foregroundStyle(.secondary)
                ProgressView(value: Double(store.xpIntoLevel), total: 100)
                    .tint(store.accentColor)
            }
            .padding()
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 6) {
                    Text(selectedSection.rawValue)
                        .font(.system(size: 34, weight: .bold))
                    Text(store.identityLine)
                        .font(.title3)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 6) {
                    Text("MyLifePal")
                        .font(.headline)
                        .foregroundStyle(store.primaryColor)
                    Text(store.lastMessage.isEmpty ? "Offline-first Mac app" : store.lastMessage)
                        .foregroundStyle(.secondary)
                }
            }

            HStack(spacing: 12) {
                MetricCard(value: "\(store.state.totalXp)", label: "total XP")
                MetricCard(value: "\(store.completedToday)/\(store.state.habits.count)", label: "done today")
                MetricCard(value: "\(store.state.tomatoSessionsToday)", label: "tomatoes")
                MetricCard(value: "\(store.companionLevel)", label: "companion")
            }
        }
    }

    @ViewBuilder
    private var content: some View {
        switch selectedSection {
        case .today:
            TodayView(selectedSection: $selectedSection)
        case .timer:
            TimerPanelView()
        case .mood:
            MoodPanelView()
        case .habits:
            HabitsPanelView()
        case .rewards:
            RewardsPanelView(showingRewardComposer: $showingRewardComposer)
        case .progress:
            ProgressPanelView()
        case .appearance:
            AppearancePanelView()
        }
    }
}

private struct TodayView: View {
    @EnvironmentObject private var store: MacLifeStore
    @Binding var selectedSection: MacSection

    var body: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 320), spacing: 16)], spacing: 16) {
            CoachCard(selectedSection: $selectedSection)
            CompanionCard()
            TimerSummaryCard()
            MoodSummaryCard()
        }

        SectionHeader(title: "Today", subtitle: "Complete the smallest promise that counts.")
        if store.state.habits.isEmpty {
            EmptyPanel(message: "No habits yet. Add one tiny loop to begin.")
        } else {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 360), spacing: 16)], spacing: 16) {
                ForEach(store.state.habits) { habit in
                    HabitCard(habit: habit)
                }
            }
        }
    }
}

private struct CoachCard: View {
    @EnvironmentObject private var store: MacLifeStore
    @Binding var selectedSection: MacSection

    var body: some View {
        CardView(style: .accent) {
            VStack(alignment: .leading, spacing: 14) {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 5) {
                        Text(headline)
                            .font(.title2.weight(.bold))
                        Text(reason)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    Text("\(todayScore)%")
                        .font(.headline.weight(.bold))
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .background(store.primaryColor)
                        .foregroundStyle(.white)
                        .clipShape(Capsule())
                }

                HStack(spacing: 10) {
                    Button(primaryActionTitle) {
                        runPrimaryAction()
                    }
                    .buttonStyle(.borderedProminent)

                    Button("Start focus") {
                        store.startTimer(mode: .focus)
                        selectedSection = .timer
                    }
                    .buttonStyle(.bordered)
                }
            }
        }
    }

    private var todayScore: Int {
        guard !store.state.habits.isEmpty else { return store.todayMood == nil ? 0 : 15 }
        let habitScore = Int((Double(store.completedToday) / Double(store.state.habits.count)) * 65)
        let moodScore = store.todayMood == nil ? 0 : 15
        let focusScore = min(20, store.state.tomatoSessionsToday * 8)
        return min(100, habitScore + moodScore + focusScore)
    }

    private var headline: String {
        if store.state.habits.isEmpty { return "Create one tiny loop" }
        if store.nextHabit == nil { return "Claim the day" }
        if let mood = store.todayMood, mood.stress >= 4 && mood.energy <= 2 { return "Use the rescue version" }
        return "Next useful action"
    }

    private var reason: String {
        if let habit = store.nextHabit {
            return "\(habit.cue): \(habit.tinyAction)"
        }
        return "Your habit votes are in. Spend coins or design tomorrow's first move."
    }

    private var primaryActionTitle: String {
        store.nextHabit == nil ? "Open rewards" : "Complete next"
    }

    private func runPrimaryAction() {
        if let habit = store.nextHabit {
            store.complete(habit)
        } else {
            selectedSection = .rewards
        }
    }
}

private struct CompanionCard: View {
    @EnvironmentObject private var store: MacLifeStore

    var body: some View {
        CardView(style: .normal) {
            HStack(spacing: 16) {
                CompanionMark()
                    .frame(width: 92, height: 92)
                VStack(alignment: .leading, spacing: 7) {
                    Text("\(store.state.monsterName) the \(store.companionStage)")
                        .font(.title2.weight(.bold))
                    Text("Level \(store.companionLevel) companion")
                        .foregroundStyle(store.primaryColor)
                    ProgressView(value: Double(store.state.monsterXp % 90), total: 90)
                        .tint(store.primaryColor)
                    Text("Bond \(store.state.monsterBond)/100")
                        .foregroundStyle(.secondary)
                }
            }
        }
    }
}

private struct TimerSummaryCard: View {
    @EnvironmentObject private var store: MacLifeStore

    var body: some View {
        CardView(style: .accent) {
            VStack(alignment: .leading, spacing: 12) {
                Text("Tomato Timer")
                    .font(.title2.weight(.bold))
                Text(store.timerDisplay)
                    .font(.system(size: 42, weight: .bold, design: .rounded))
                ProgressView(value: store.timerProgress)
                    .tint(store.accentColor)
                HStack {
                    Button(store.state.timerRunning ? "Pause" : "Start") {
                        if store.state.timerRunning {
                            store.pauseTimer()
                        } else {
                            store.startTimer()
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    Button("Reset") {
                        store.resetTimer()
                    }
                    .buttonStyle(.bordered)
                }
            }
        }
    }
}

private struct MoodSummaryCard: View {
    @EnvironmentObject private var store: MacLifeStore

    var body: some View {
        CardView(style: .normal) {
            VStack(alignment: .leading, spacing: 10) {
                Text("Emotion check-in")
                    .font(.title2.weight(.bold))
                Text(store.todayMood.map { moodLabel($0.mood) } ?? "No check-in today")
                    .font(.title3.weight(.semibold))
                if let entry = store.todayMood {
                    Text("Energy \(entry.energy)/5, stress \(entry.stress)/5")
                        .foregroundStyle(.secondary)
                } else {
                    Text("A quick check-in makes the habit loop more humane.")
                        .foregroundStyle(.secondary)
                }
            }
        }
    }
}

private struct TimerPanelView: View {
    @EnvironmentObject private var store: MacLifeStore

    var body: some View {
        CardView(style: .accent) {
            VStack(alignment: .leading, spacing: 18) {
                HStack {
                    VStack(alignment: .leading) {
                        Text(store.state.timerMode.title)
                            .font(.headline)
                            .foregroundStyle(store.primaryColor)
                        Text(store.timerDisplay)
                            .font(.system(size: 72, weight: .bold, design: .rounded))
                    }
                    Spacer()
                    Text(store.state.timerRunning ? "Running" : "Paused")
                        .font(.headline.weight(.bold))
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(store.primaryColor)
                        .foregroundStyle(.white)
                        .clipShape(Capsule())
                }

                ProgressView(value: store.timerProgress)
                    .tint(store.accentColor)

                HStack {
                    ForEach(TimerMode.allCases) { mode in
                        if mode == store.state.timerMode {
                            Button(mode.title) {
                                store.selectTimerMode(mode)
                            }
                            .buttonStyle(.borderedProminent)
                        } else {
                            Button(mode.title) {
                                store.selectTimerMode(mode)
                            }
                            .buttonStyle(.bordered)
                        }
                    }
                    Spacer()
                    Button(store.state.timerRunning ? "Pause" : "Start") {
                        if store.state.timerRunning {
                            store.pauseTimer()
                        } else {
                            store.startTimer()
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    Button("Reset") {
                        store.resetTimer()
                    }
                    .buttonStyle(.bordered)
                }
            }
        }

        LazyVGrid(columns: [GridItem(.adaptive(minimum: 200), spacing: 16)], spacing: 16) {
            MetricCard(value: "\(store.state.tomatoFocusSessions)", label: "focus tomatoes")
            MetricCard(value: "\(store.state.tomatoMinutesToday)", label: "focus minutes today")
            MetricCard(value: "\(store.state.tomatoBreakSessions)", label: "break sessions")
        }
    }
}

private struct MoodPanelView: View {
    @EnvironmentObject private var store: MacLifeStore
    @State private var mood = 3
    @State private var energy = 3
    @State private var stress = 3
    @State private var note = ""

    var body: some View {
        CardView(style: .normal) {
            VStack(alignment: .leading, spacing: 14) {
                Text("Daily emotion check-in")
                    .font(.title2.weight(.bold))
                HStack {
                    Picker("Mood", selection: $mood) {
                        ForEach(1...5, id: \.self) { Text("\($0) \(moodLabel($0))").tag($0) }
                    }
                    Picker("Energy", selection: $energy) {
                        ForEach(1...5, id: \.self) { Text("\($0)").tag($0) }
                    }
                    Picker("Stress", selection: $stress) {
                        ForEach(1...5, id: \.self) { Text("\($0)").tag($0) }
                    }
                }
                TextField("What affected your mood?", text: $note, axis: .vertical)
                    .textFieldStyle(.roundedBorder)
                Button(store.todayMood == nil ? "Record check-in" : "Update check-in") {
                    store.recordMood(mood: mood, energy: energy, stress: stress, note: note)
                }
                .buttonStyle(.borderedProminent)
            }
        }
        .onAppear {
            if let entry = store.todayMood {
                mood = entry.mood
                energy = entry.energy
                stress = entry.stress
                note = entry.note
            }
        }

        SectionHeader(title: "Recent feelings", subtitle: "Patterns become visible once they have a place to land.")
        ForEach(store.state.moodEntries.suffix(8).reversed()) { entry in
            CardView(style: .normal) {
                HStack {
                    VStack(alignment: .leading) {
                        Text("\(entry.date) - \(moodLabel(entry.mood))")
                            .font(.headline)
                        Text("Energy \(entry.energy)/5, stress \(entry.stress)/5")
                            .foregroundStyle(.secondary)
                        if !entry.note.isEmpty {
                            Text(entry.note)
                                .foregroundStyle(.secondary)
                        }
                    }
                    Spacer()
                    Text("\(entry.mood)/5")
                        .font(.headline.weight(.bold))
                }
            }
        }
    }
}

private struct HabitsPanelView: View {
    @EnvironmentObject private var store: MacLifeStore

    var body: some View {
        HStack {
            SectionHeader(title: "Habit Studio", subtitle: "Design cues, tiny actions, identity votes, rewards, and reminders.")
            Spacer()
            Button("Add Habit") {
                store.showHabitComposer = true
            }
            .buttonStyle(.borderedProminent)
        }

        if store.state.habits.isEmpty {
            EmptyPanel(message: "No habits yet.")
        } else {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 380), spacing: 16)], spacing: 16) {
                ForEach(store.state.habits) { habit in
                    HabitCard(habit: habit)
                }
            }
        }
    }
}

private struct HabitCard: View {
    @EnvironmentObject private var store: MacLifeStore
    let habit: MacHabit

    var body: some View {
        CardView(style: store.isCompletedToday(habit) ? .soft : .normal) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 5) {
                        Text("\(habit.icon) \(habit.name)")
                            .font(.title3.weight(.bold))
                        Text(habit.identity)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    Text(habit.attribute)
                        .font(.caption.weight(.bold))
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(store.primaryColor)
                        .foregroundStyle(.white)
                        .clipShape(Capsule())
                }

                VStack(alignment: .leading, spacing: 5) {
                    DetailLine(label: "Cue", value: habit.cue)
                    DetailLine(label: "Tiny action", value: habit.tinyAction)
                    DetailLine(label: "Reward", value: habit.reward)
                    DetailLine(label: "Reminder", value: habit.reminderEnabled ? "Daily at \(habit.reminderTime)" : "Off")
                }

                HStack {
                    MetricCard(value: "\(habit.streak)d", label: "streak")
                    MetricCard(value: "\(habit.bestStreak)d", label: "best")
                    MetricCard(value: "\(habit.completions)", label: "votes")
                }

                HStack {
                    Button(store.isCompletedToday(habit) ? "Done today" : "Complete") {
                        store.complete(habit)
                    }
                    .disabled(store.isCompletedToday(habit))
                    .buttonStyle(.borderedProminent)
                    Button("Delete") {
                        store.deleteHabit(habit)
                    }
                    .buttonStyle(.bordered)
                    .foregroundStyle(.red)
                }
            }
        }
    }
}

private struct RewardsPanelView: View {
    @EnvironmentObject private var store: MacLifeStore
    @Binding var showingRewardComposer: Bool

    var body: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 260), spacing: 16)], spacing: 16) {
            MetricCard(value: "\(store.state.coins)", label: "coins")
            MetricCard(value: "\(store.state.gems)", label: "gems")
            MetricCard(value: "\(store.state.inventory.count)", label: "inventory")
            MetricCard(value: "\(store.state.rewardClaimsToday)", label: "claimed today")
        }

        HStack {
            SectionHeader(title: "Reward Shop", subtitle: "Spend coins on concrete real-life rewards.")
            Spacer()
            Button("Add Reward") {
                showingRewardComposer = true
            }
            .buttonStyle(.borderedProminent)
        }

        LazyVGrid(columns: [GridItem(.adaptive(minimum: 340), spacing: 16)], spacing: 16) {
            ForEach(store.state.rewards) { reward in
                CardView(style: .normal) {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            VStack(alignment: .leading) {
                                Text(reward.title)
                                    .font(.title3.weight(.bold))
                                Text("\(reward.claimedCount) claimed")
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            Text("\(reward.cost) coins")
                                .font(.headline)
                                .foregroundStyle(store.primaryColor)
                        }
                        HStack {
                            Button(store.state.coins >= reward.cost ? "Claim Reward" : "Need coins") {
                                store.claimReward(reward)
                            }
                            .disabled(store.state.coins < reward.cost)
                            .buttonStyle(.borderedProminent)
                            Button("Delete") {
                                store.deleteReward(reward)
                            }
                            .buttonStyle(.bordered)
                        }
                    }
                }
            }
        }

        SectionHeader(title: "Inventory", subtitle: "Claimed rewards and symbolic life-game loot.")
        if store.state.inventory.isEmpty {
            EmptyPanel(message: "No inventory yet.")
        } else {
            ForEach(store.state.inventory.reversed(), id: \.self) { item in
                CardView(style: .soft) {
                    Text(item)
                        .font(.headline)
                }
            }
        }
    }
}

private struct ProgressPanelView: View {
    @EnvironmentObject private var store: MacLifeStore

    var body: some View {
        CardView(style: .normal) {
            VStack(alignment: .leading, spacing: 14) {
                Text("Level \(store.level)")
                    .font(.system(size: 42, weight: .bold))
                Text("\(store.xpIntoLevel) / 100 XP to next level")
                    .foregroundStyle(.secondary)
                ProgressView(value: Double(store.xpIntoLevel), total: 100)
                    .tint(store.primaryColor)
            }
        }

        SectionHeader(title: "Attributes", subtitle: "Each habit feeds one real-life skill.")
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 260), spacing: 16)], spacing: 16) {
            ForEach(MacLifeStore.attributes, id: \.self) { attribute in
                let xp = store.state.attributeXp[attribute, default: 0]
                CardView(style: .normal) {
                    VStack(alignment: .leading, spacing: 10) {
                        HStack {
                            Text(attribute)
                                .font(.headline)
                            Spacer()
                            Text("\(xp) XP")
                                .foregroundStyle(.secondary)
                        }
                        ProgressView(value: Double(xp % 100), total: 100)
                            .tint(store.primaryColor)
                    }
                }
            }
        }

        SectionHeader(title: "Achievements", subtitle: "Milestones unlock naturally as the system gets used.")
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 300), spacing: 16)], spacing: 16) {
            AchievementCard(title: "First spark", unlocked: store.state.habits.contains { $0.completions > 0 })
            AchievementCard(title: "Three-day identity", unlocked: store.state.habits.contains { $0.bestStreak >= 3 })
            AchievementCard(title: "First tomato", unlocked: store.state.tomatoFocusSessions >= 1)
            AchievementCard(title: "Mood journal", unlocked: store.state.moodEntries.count >= 7)
            AchievementCard(title: "Companion keeper", unlocked: store.companionLevel >= 3)
            AchievementCard(title: "LifePal level 5", unlocked: store.level >= 5)
        }
    }
}

private struct AppearancePanelView: View {
    @EnvironmentObject private var store: MacLifeStore
    @State private var primary = ""
    @State private var accent = ""
    @State private var background = ""

    var body: some View {
        CardView(style: .normal) {
            VStack(alignment: .leading, spacing: 14) {
                Text("Color theme")
                    .font(.title2.weight(.bold))
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: 12)], spacing: 12) {
                    ForEach(MacLifeStore.themePresets) { theme in
                        Button {
                            store.applyTheme(theme)
                            loadCurrent()
                        } label: {
                            HStack {
                                Circle().fill(Color(hex: theme.primary)).frame(width: 18, height: 18)
                                Text(theme.name)
                                Spacer()
                            }
                        }
                        .buttonStyle(.bordered)
                    }
                }

                Divider()

                HStack {
                    TextField("Primary #RRGGBB", text: $primary)
                    TextField("Accent #RRGGBB", text: $accent)
                    TextField("Background #RRGGBB", text: $background)
                    Button("Save Custom") {
                        store.saveCustomTheme(primary: primary, accent: accent, background: background)
                    }
                    .buttonStyle(.borderedProminent)
                }
            }
        }
        .onAppear(perform: loadCurrent)

        CardView(style: .soft) {
            HStack {
                Text("Data file")
                    .font(.headline)
                Spacer()
                Button("Reveal in Finder") {
                    store.revealDataFolder()
                }
                .buttonStyle(.bordered)
            }
        }
    }

    private func loadCurrent() {
        primary = store.state.theme.primary
        accent = store.state.theme.accent
        background = store.state.theme.background
    }
}

struct SettingsView: View {
    @EnvironmentObject private var store: MacLifeStore

    var body: some View {
        Form {
            Section("Data") {
                Button("Reveal Data File") {
                    store.revealDataFolder()
                }
            }
            Section("Appearance") {
                Text("Current theme: \(store.state.theme.name)")
            }
        }
        .padding(20)
        .frame(width: 420)
    }
}

private struct HabitComposerView: View {
    @EnvironmentObject private var store: MacLifeStore
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var icon = "*"
    @State private var cue = ""
    @State private var tinyAction = ""
    @State private var identity = ""
    @State private var reward = ""
    @State private var attribute = "Mind"
    @State private var reminderEnabled = false
    @State private var reminderTime = "09:00"

    var body: some View {
        Form {
            Section("Atomic habit") {
                TextField("Habit name", text: $name)
                TextField("Icon", text: $icon)
                TextField("Cue", text: $cue)
                TextField("Tiny action", text: $tinyAction)
                TextField("Identity vote", text: $identity)
                TextField("Reward", text: $reward)
            }
            Section("Game settings") {
                Picker("Attribute", selection: $attribute) {
                    ForEach(MacLifeStore.attributes, id: \.self) { Text($0).tag($0) }
                }
                Toggle("Daily reminder", isOn: $reminderEnabled)
                Picker("Reminder time", selection: $reminderTime) {
                    ForEach(MacLifeStore.reminderTimes, id: \.self) { Text($0).tag($0) }
                }
            }
            HStack {
                Button("Cancel") {
                    dismiss()
                }
                Spacer()
                Button("Add Habit") {
                    store.addHabit(MacHabit(name: name, icon: icon, cue: cue, tinyAction: tinyAction, identity: identity, reward: reward, attribute: attribute, reminderEnabled: reminderEnabled, reminderTime: reminderTime))
                    dismiss()
                }
                .buttonStyle(.borderedProminent)
            }
        }
        .padding(24)
    }
}

private struct RewardComposerView: View {
    @EnvironmentObject private var store: MacLifeStore
    @Environment(\.dismiss) private var dismiss
    @State private var title = ""
    @State private var cost = 40

    var body: some View {
        Form {
            TextField("Reward title", text: $title)
            Stepper("Cost: \(cost) coins", value: $cost, in: 1...999)
            HStack {
                Button("Cancel") {
                    dismiss()
                }
                Spacer()
                Button("Add Reward") {
                    store.addReward(MacReward(title: title, cost: cost))
                    dismiss()
                }
                .buttonStyle(.borderedProminent)
            }
        }
        .padding(24)
    }
}

private enum CardStyle {
    case normal
    case soft
    case accent
}

private struct CardView<Content: View>: View {
    @EnvironmentObject private var store: MacLifeStore
    var style: CardStyle = .normal
    @ViewBuilder var content: Content

    var body: some View {
        content
            .padding(18)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(background)
            .clipShape(RoundedRectangle(cornerRadius: 8))
            .overlay {
                RoundedRectangle(cornerRadius: 8)
                    .stroke(border, lineWidth: 1)
            }
    }

    private var background: Color {
        switch style {
        case .normal: return Color(nsColor: .controlBackgroundColor)
        case .soft: return store.primaryColor.opacity(0.12)
        case .accent: return store.accentColor.opacity(0.2)
        }
    }

    private var border: Color {
        switch style {
        case .normal: return Color(nsColor: .separatorColor).opacity(0.45)
        case .soft: return store.primaryColor.opacity(0.35)
        case .accent: return store.accentColor.opacity(0.55)
        }
    }
}

private struct MetricCard: View {
    let value: String
    let label: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(value)
                .font(.title3.weight(.bold))
            Text(label)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(nsColor: .controlBackgroundColor))
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

private struct SectionHeader: View {
    let title: String
    let subtitle: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.title2.weight(.bold))
            Text(subtitle)
                .foregroundStyle(.secondary)
        }
        .padding(.top, 6)
    }
}

private struct DetailLine: View {
    let label: String
    let value: String

    var body: some View {
        HStack(alignment: .top) {
            Text(label)
                .font(.subheadline.weight(.semibold))
            Text(value)
                .foregroundStyle(.secondary)
            Spacer()
        }
    }
}

private struct EmptyPanel: View {
    let message: String

    var body: some View {
        CardView(style: .normal) {
            Text(message)
                .foregroundStyle(.secondary)
        }
    }
}

private struct AchievementCard: View {
    @EnvironmentObject private var store: MacLifeStore
    let title: String
    let unlocked: Bool

    var body: some View {
        CardView(style: unlocked ? .soft : .normal) {
            HStack {
                Image(systemName: unlocked ? "checkmark.seal.fill" : "lock")
                    .foregroundStyle(unlocked ? store.primaryColor : .secondary)
                Text(title)
                    .font(.headline)
                Spacer()
                Text(unlocked ? "Unlocked" : "Locked")
                    .foregroundStyle(.secondary)
            }
        }
    }
}

private struct CompanionMark: View {
    @EnvironmentObject private var store: MacLifeStore

    var body: some View {
        ZStack {
            Circle()
                .fill(store.accentColor.opacity(0.26))
            Circle()
                .fill(store.primaryColor)
                .frame(width: 62, height: 62)
            Circle()
                .fill(.white)
                .frame(width: 9, height: 9)
                .offset(x: -13, y: -8)
            Circle()
                .fill(.white)
                .frame(width: 9, height: 9)
                .offset(x: 13, y: -8)
            Capsule()
                .fill(.white.opacity(0.88))
                .frame(width: 24, height: 9)
                .offset(y: 13)
        }
        .accessibilityLabel("\(store.state.monsterName), level \(store.companionLevel) \(store.companionStage)")
    }
}

private func moodLabel(_ value: Int) -> String {
    if value <= 1 { return "Rough" }
    if value == 2 { return "Low" }
    if value == 3 { return "Okay" }
    if value == 4 { return "Good" }
    return "Great"
}
