import SwiftUI
import WayfareCore

@main
struct WayfareApp: App {
  @State private var store = AppStore()
  @Environment(\.scenePhase) private var scenePhase

  var body: some Scene {
    WindowGroup {
      Group {
        if store.userId == nil || store.recovery {
          AuthScreen()
        } else {
          TripsScreen().id(store.userId)
        }
      }
      .environment(store)
      .tint(Palette.clay)
      .preferredColorScheme(.light)
      .task { await store.start() }
      .task(id: scenePhase) {
        guard scenePhase == .active else { return }
        await store.refreshFX()
        await store.refresh()
        // Foreground retry is also the iOS offline queue's retry opportunity.
        while !Task.isCancelled {
          do { try await Task.sleep(for: .seconds(30)) } catch { return }
          await store.refresh()
        }
      }
      .onOpenURL { url in
        if url.scheme == "wayfare-ios", url.host == "join" {
          store.pendingInvite = url.lastPathComponent.uppercased()
        } else {
          Task { await store.handleURL(url) }
        }
      }
    }
  }
}
