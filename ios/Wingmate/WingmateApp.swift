import SwiftUI

@main
struct WingmateApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .preferredColorScheme(.dark)
                .tint(Color(red: 1.0, green: 0.36, blue: 0.54))
        }
    }
}
