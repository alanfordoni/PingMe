import SwiftUI
import composeApp

@main
struct iOSApp: App {

    init() {
        KoinIosKt.doInitIosKoin()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
