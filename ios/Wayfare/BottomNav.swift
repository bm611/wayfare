import SwiftUI

/// The three places the app goes. "Tape" is a trip's ledger; "You" is the account.
enum NavTab: String, CaseIterable, Identifiable {
  case trips, tape, you
  var id: String { rawValue }
  var label: String {
    switch self {
    case .trips: "Trips"
    case .tape: "Tape"
    case .you: "You"
    }
  }
}

/// Where the single navigation stack can go. Overview and Tape are two views of
/// one trip, so the tape carries the trip's id rather than standing alone.
enum TripRoute: Hashable {
  case overview(String)
  case tape(String, addOnOpen: Bool)
  case account
}

/// The bottom bar: a floating capsule, glass on iOS 26 and a material below it.
/// Its glyphs are drawn rather than iconised — two stacked boarding-pass stubs
/// for Trips, three ragged receipt lines for Tape, a plain ring for You. The
/// active tab takes an amber pill, so the one solid amber on screen stays the
/// action button.
struct WayfareBottomBar: View {
  let current: NavTab
  let onSelect: (NavTab) -> Void

  var body: some View {
    HStack(spacing: 0) {
      ForEach(NavTab.allCases) { tab in
        Button { onSelect(tab) } label: {
          VStack(spacing: 6) {
            glyph(tab, tint: tab == current ? Palette.amber : Palette.slate)
              .frame(width: 56, height: 28)
              .background {
                if tab == current {
                  Capsule().fill(Palette.amber.opacity(0.22))
                }
              }
            Text(tab.label).typeStyle(.labelSmall)
              .fontWeight(tab == current ? .semibold : .regular)
              .foregroundStyle(tab == current ? Palette.amber : Palette.slate)
          }
          .frame(maxWidth: .infinity)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(tab == current ? [.isSelected] : [])
      }
    }
    .padding(.vertical, 10).padding(.horizontal, 8)
    .glassChrome(Capsule())
    .padding(.horizontal, 16)
    .animation(.spring(duration: 0.25), value: current)
  }

  @ViewBuilder private func glyph(_ tab: NavTab, tint: Color) -> some View {
    switch tab {
    case .trips:
      VStack(spacing: 3) {
        ForEach(0..<2, id: \.self) { _ in
          RoundedRectangle(cornerRadius: 2).fill(tint).frame(width: 18, height: 6)
        }
      }
    case .tape:
      // Ragged, like a receipt torn off mid-line.
      VStack(spacing: 3) {
        ForEach([20.0, 20.0, 13.0], id: \.self) { width in
          Rectangle().fill(tint).frame(width: width, height: 2.5)
        }
      }
    case .you:
      Circle().strokeBorder(tint, lineWidth: 2.5).frame(width: 16, height: 16)
    }
  }
}
