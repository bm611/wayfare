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
      // Compose sets bodyLarge as the default text style; anything that does not
      // name its own style should inherit Manrope rather than fall back to SF.
      .typeStyle(.bodyLarge)
      .tint(Palette.accentInk)
      .task { await store.start() }
      .task(id: scenePhase) {
        guard scenePhase == .active else { return }
        await store.refreshFX()
        await store.refresh()
        // Foreground retry is also the iOS offline queue's retry opportunity.
        var ticks = 0
        while !Task.isCancelled {
          do { try await Task.sleep(for: .seconds(30)) } catch { return }
          ticks += 1
          if ticks.isMultiple(of: 10) { await store.refresh() }
          else { await store.refreshPending() }
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
