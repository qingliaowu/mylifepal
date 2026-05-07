import SwiftUI

@main
struct MyLifePalMacApp: App {
    @StateObject private var store = MacLifeStore()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(store)
                .frame(minWidth: 1080, minHeight: 720)
        }
        .commands {
            CommandGroup(after: .newItem) {
                Button("Add Habit") {
                    store.showHabitComposer = true
                }
                .keyboardShortcut("n", modifiers: [.command])

                Button("Start Focus Tomato") {
                    store.startTimer(mode: .focus)
                }
                .keyboardShortcut("t", modifiers: [.command])
            }
        }

        Settings {
            SettingsView()
                .environmentObject(store)
        }
    }
}
