import SwiftUI
import WayfareCore

/// The third tab. Everything that is about the account rather than about a trip
/// used to live in a toolbar menu on the trips list; this gives it somewhere to
/// be, which is what the design's third tab is for.
struct AccountScreen: View {
  @Environment(AppStore.self) private var store
  @Binding var path: [TripRoute]
  @State private var joining = false
  @State private var recovery = false
  @State private var signout = false

  private var unsynced: Int { store.expenses.filter { $0.syncState != .synced }.count }

  var body: some View {
    ScrollView {
      VStack(alignment: .leading, spacing: 0) {
        Brand().padding(.horizontal, 8).padding(.top, 8)
        Text("You").typeStyle(.displaySmall).padding(.horizontal, 8).padding(.top, 14)

        HStack(spacing: 14) {
          Text(initial)
            .font(.system(size: 18, weight: .bold)).foregroundStyle(Palette.night)
            .frame(width: 48, height: 48).background(Palette.amber, in: Circle())
          VStack(alignment: .leading, spacing: 2) {
            Text(store.name(for: store.userId ?? "")).typeStyle(.titleMedium).lineLimit(1)
            if let email = store.accountEmail {
              Text(email).typeStyle(.bodyMedium).foregroundStyle(Palette.slate).lineLimit(1)
            }
          }
          Spacer(minLength: 0)
        }
        .padding(20)
        .background(Palette.card, in: RoundedRectangle(cornerRadius: 28))
        .padding(.top, 22)

        MonoLabel(text: "ACTIONS", color: Palette.slate, tracking: 2)
          .padding(.horizontal, 8).padding(.top, 26).padding(.bottom, 10)

        VStack(spacing: 0) {
          row("ticket", "Join a trip", "Redeem an eight-character invite code") { joining = true }
          HairlineDivider().padding(.horizontal, 20)
          row(
            "arrow.clockwise", "Refresh everything",
            "Pull the latest trips and lines from the server"
          ) { Task { await store.refresh() } }
          if unsynced > 0 {
            HairlineDivider().padding(.horizontal, 20)
            row(
              "exclamationmark.arrow.triangle.2.circlepath", "Unsynced lines",
              "\(unsynced) \(unsynced == 1 ? "line has" : "lines have") not reached the server"
            ) { recovery = true }
          }
          HairlineDivider().padding(.horizontal, 20)
          row(
            "rectangle.portrait.and.arrow.right", "Sign out",
            "Clears cached trips and any unsynced lines from this device",
            tint: Palette.errorRed
          ) { signout = true }
        }
        .background(Palette.card, in: RoundedRectangle(cornerRadius: 28))

        Spacer(minLength: 40)
      }
      .frame(maxWidth: 700).frame(maxWidth: .infinity)
      .padding(.horizontal, 16)
    }
    .canvasScreen().refreshable { await store.refresh() }
    .navigationTitle("You").navigationBarTitleDisplayMode(.inline)
    .toolbar(.hidden, for: .navigationBar)
    .sheet(isPresented: $joining) { JoinForm { path = [.overview($0)] } }
    .sheet(isPresented: $recovery) { UnsyncedScreen() }
    .confirmationDialog("Sign out?", isPresented: $signout, titleVisibility: .visible) {
      Button("Sign out and clear this device", role: .destructive) {
        Task { try? await store.signOut() }
      }
    } message: {
      Text(
        "Cached trips and any lines still waiting to sync will be removed from this device. Sync pending lines first to keep them."
      )
    }
  }

  private var initial: String {
    let name = store.name(for: store.userId ?? "")
    return name.first.map { String($0).uppercased() } ?? "W"
  }

  private func row(
    _ symbol: String, _ title: String, _ subtitle: String, tint: Color = Palette.steel,
    action: @escaping () -> Void
  ) -> some View {
    Button(action: action) {
      HStack(spacing: 16) {
        Image(systemName: symbol).font(.system(size: 18)).foregroundStyle(tint).frame(width: 22)
        VStack(alignment: .leading, spacing: 2) {
          Text(title).typeStyle(.titleMedium)
            .foregroundStyle(tint == Palette.errorRed ? Palette.errorRed : Palette.paper)
          Text(subtitle).typeStyle(.bodySmall).foregroundStyle(Palette.slate)
            .fixedSize(horizontal: false, vertical: true)
        }
        Spacer(minLength: 0)
      }
      .frame(maxWidth: .infinity, alignment: .leading).padding(20)
    }.buttonStyle(.plain)
  }
}
