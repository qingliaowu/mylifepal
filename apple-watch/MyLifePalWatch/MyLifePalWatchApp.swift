import SwiftUI

@main
struct MyLifePalWatchApp: App {
    @StateObject private var store = WatchHabitStore()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(store)
        }
    }
}
