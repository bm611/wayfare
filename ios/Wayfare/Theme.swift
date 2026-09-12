import SwiftUI
import WayfareCore

/// The web app's boarding-pass palette, matched hex for hex against
/// `android/app/src/main/java/com/wayfare/app/ui/Theme.kt` so the two clients
/// stay in step. Every value here has an exact Compose counterpart.
enum Palette {
  static let clay = Color(hex: 0xB5543C)
  static let clayDeep = Color(hex: 0x8D3D29)
  static let clayWash = Color(hex: 0xF4E3DC)
  /// Accent buttons ink their label in warmed-off-white, never pure white.
  static let onClay = Color(hex: 0xFFF8F4)

  static let paper = Color(hex: 0xF7F2E9)
  static let paperDeep = Color(hex: 0xEFE7D9)
  static let card = Color(hex: 0xFFFCF6)
  static let ink = Color(hex: 0x1A1714)
  static let soft = Color(hex: 0x6F6459)
  static let faint = Color(hex: 0x786D61)
  static let line = Color(hex: 0xE3D9CA)
  static let lineSoft = Color(hex: 0xEFE7DB)
  static let success = Color(hex: 0x55713F)
}

extension Color {
  init(hex: UInt32) {
    self.init(
      red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255,
      blue: Double(hex & 0xFF) / 255)
  }
}

// MARK: - Typography

/// Manrope ships as a variable font whose default instance is ExtraLight (200),
/// so registering it as-is would render the whole app at hairline weight. The
/// bundled faces are static instances pinned to the four weights Compose's
/// `TravelFont` actually asks for, referenced here by PostScript name.
enum Manrope {
  static let regular = "Manrope-Regular"
  static let medium = "Manrope-Medium"
  static let semibold = "Manrope-SemiBold"
  static let bold = "Manrope-Bold"
}

/// Mirrors `WayfareTypography` in the Android theme. Sizes are the Compose `sp`
/// values verbatim and `tracking` is its `letterSpacing`; `relativeTo` keeps
/// Dynamic Type working, which `sp` gets for free on Android.
struct TypeStyle {
  let face: String
  let size: CGFloat
  let tracking: CGFloat
  let relativeTo: Font.TextStyle
  var font: Font { .custom(face, size: size, relativeTo: relativeTo) }

  static let displaySmall = TypeStyle(
    face: Manrope.bold, size: 34, tracking: -0.6, relativeTo: .largeTitle)
  static let headlineMedium = TypeStyle(
    face: Manrope.bold, size: 28, tracking: -0.4, relativeTo: .title)
  static let headlineSmall = TypeStyle(
    face: Manrope.bold, size: 22, tracking: -0.2, relativeTo: .title2)
  static let titleLarge = TypeStyle(
    face: Manrope.semibold, size: 19, tracking: 0, relativeTo: .title3)
  static let titleMedium = TypeStyle(
    face: Manrope.semibold, size: 16, tracking: 0, relativeTo: .headline)
  static let bodyLarge = TypeStyle(
    face: Manrope.regular, size: 16, tracking: 0, relativeTo: .body)
  static let bodyMedium = TypeStyle(
    face: Manrope.regular, size: 14, tracking: 0, relativeTo: .subheadline)
  static let bodySmall = TypeStyle(
    face: Manrope.regular, size: 13, tracking: 0, relativeTo: .footnote)
  static let labelLarge = TypeStyle(
    face: Manrope.semibold, size: 15, tracking: 0, relativeTo: .callout)
  static let labelMedium = TypeStyle(
    face: Manrope.semibold, size: 12, tracking: 0.2, relativeTo: .caption)
  static let labelSmall = TypeStyle(
    face: Manrope.bold, size: 11, tracking: 0.4, relativeTo: .caption2)
}

extension View {
  /// Applies a Compose text style: face, size and tracking together, since
  /// setting the font without its letterSpacing loses half the character.
  func typeStyle(_ style: TypeStyle) -> some View {
    font(style.font).tracking(style.tracking)
  }
}

func money(_ amount: Decimal, _ currency: String = "EUR") -> String {
  let formatter = NumberFormatter()
  formatter.numberStyle = .currency
  formatter.currencyCode = currency
  return formatter.string(from: amount as NSDecimalNumber) ?? "\(amount) \(currency)"
}

func phaseLabel(_ trip: Trip) -> String {
  switch tripPhase(trip) {
  case .active(let day, let total): "Day \(day) of \(total)"
  case .upcoming(let days): "In \(days) \(days == 1 ? "day" : "days")"
  case .past: "A chapter well spent"
  case .undated: "Dates open"
  }
}

extension WayfareCore.Category {
  var label: String { rawValue.capitalized }
  var symbol: String {
    switch self {
    case .flights: "airplane"
    case .stays: "bed.double"
    case .food: "fork.knife"
    case .activities: "sun.max"
    case .transport: "tram"
    case .shopping: "bag"
    case .other: "ellipsis.circle"
    }
  }
}

struct PrimaryButton: View {
  let title: String
  var busy = false
  let action: () -> Void
  var body: some View {
    Button(action: action) {
      HStack {
        if busy { ProgressView().tint(Palette.card) }
        Text(title).typeStyle(.labelLarge)
      }
      .frame(maxWidth: .infinity).padding(.vertical, 15)
    }
    .buttonStyle(.plain)
    .foregroundStyle(Palette.card)
    .background(Palette.clay, in: Capsule())
    .disabled(busy)
  }
}

struct Notice: View {
  let text: String
  var body: some View {
    Label(text, systemImage: "info.circle")
      .typeStyle(.bodyMedium).foregroundStyle(Palette.clay)
      .frame(maxWidth: .infinity, alignment: .leading).padding(14)
      .background(Palette.clay.opacity(0.08), in: RoundedRectangle(cornerRadius: 12))
      .accessibilityAddTraits(.updatesFrequently)
  }
}

struct TripArtwork: View {
  let trip: Trip
  let url: URL?
  var body: some View {
    ZStack {
      Palette.line
      Image(systemName: "globe.europe.africa")
        .font(.system(size: 100, weight: .ultraLight)).foregroundStyle(Palette.clay.opacity(0.45))
      AsyncImage(url: url) { image in
        image.resizable().scaledToFill()
      } placeholder: {
        Color.clear
      }
      if trip.coverStatus == "pending" {
        Text("Developing your cover…").typeStyle(.bodySmall).padding(8)
          .background(Palette.card, in: Capsule()).frame(maxHeight: .infinity, alignment: .bottom)
          .padding(12)
      }
    }
    .frame(height: 190).clipped()
    .accessibilityLabel(trip.destination.map { "Destination cover for \($0)" } ?? "Trip cover")
  }
}

extension View {
  func paperScreen() -> some View {
    scrollContentBackground(.hidden).background(Palette.paper).foregroundStyle(Palette.ink)
  }
}

// MARK: - Dates

private let months = [
  "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC",
]

/// "SEP 12", matching `shortDate` in the Android Format.kt. Dates arrive as
/// plain `yyyy-MM-dd` strings, so this parses rather than reformats a Date and
/// cannot drift across time zones.
func shortDate(_ value: String) -> String? {
  let parts = value.split(separator: "-").compactMap { Int($0) }
  guard parts.count == 3, (1...12).contains(parts[1]) else { return nil }
  return "\(months[parts[1] - 1]) \(parts[2])"
}

func dateRange(_ start: String?, _ end: String?) -> String {
  switch (start.flatMap(shortDate), end.flatMap(shortDate)) {
  case (nil, nil): "Dates open"
  case (let start?, nil): "From \(start)"
  case (nil, let end?): "Until \(end)"
  case (let start?, let end?): "\(start) — \(end)"
  }
}

// MARK: - Card furniture

func phaseColor(_ trip: Trip) -> Color {
  switch tripPhase(trip) {
  case .active: Palette.success
  case .upcoming: Palette.clay
  default: Palette.faint
  }
}

/// One small fact under a card title: a 14pt glyph and its label, kept on one line.
struct MetaLabel: View {
  let symbol: String
  let text: String
  var body: some View {
    HStack(spacing: 5) {
      Image(systemName: symbol).font(.system(size: 12)).foregroundStyle(Palette.faint)
      Text(text).typeStyle(.bodySmall).foregroundStyle(Palette.soft).lineLimit(1)
    }
  }
}

/// Place and dates are two separate facts, so each gets its own glyph rather
/// than being run together by a dot. A destination long enough to crowd the
/// dates drops them to a second line instead of truncating them away.
struct TripMeta: View {
  let trip: Trip
  var body: some View {
    ViewThatFits(in: .horizontal) {
      HStack(spacing: 14) { labels }
      VStack(alignment: .leading, spacing: 5) { labels }
    }
  }
  @ViewBuilder private var labels: some View {
    if let place = trip.destination, !place.isEmpty {
      MetaLabel(symbol: "mappin.and.ellipse", text: place)
    }
    MetaLabel(symbol: "calendar", text: dateRange(trip.startDate, trip.endDate))
  }
}

/// The boarding strip: a 6pt capsule that fills toward the budget.
struct BudgetMeter: View {
  let spent: Decimal
  let budget: Decimal
  private var ratio: Double {
    guard budget > 0 else { return 0 }
    let value =
      (spent as NSDecimalNumber).doubleValue / (budget as NSDecimalNumber).doubleValue
    return min(max(value, 0), 1)
  }
  var body: some View {
    GeometryReader { geometry in
      ZStack(alignment: .leading) {
        Capsule().fill(Palette.lineSoft)
        Capsule().fill(Palette.clay).frame(width: geometry.size.width * ratio)
      }
    }
    .frame(height: 6)
    .animation(.easeInOut(duration: 0.45), value: ratio)
    .accessibilityElement()
    .accessibilityLabel("Budget progress")
    .accessibilityValue("\(Int(ratio * 100)) percent of budget spent")
  }
}

/// Rides on the cover so the phase costs the content no line of its own.
struct PhasePill: View {
  let trip: Trip
  var body: some View {
    HStack(spacing: 6) {
      Circle().fill(phaseColor(trip)).frame(width: 6, height: 6)
      Text(phaseLabel(trip)).typeStyle(.labelMedium).foregroundStyle(Palette.ink)
    }
    .padding(.horizontal, 10).padding(.vertical, 5)
    .background(Palette.card.opacity(0.94), in: Capsule())
  }
}
