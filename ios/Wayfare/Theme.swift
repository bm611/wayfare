import SwiftUI
import WayfareCore

/// Airbnb design language, shared token-for-token with the native Android theme.
/// One accent, one type family, disciplined grayscale for everything else — the
/// photography is meant to carry the colour.
enum Palette {
  /// The signature coral-pink. Primary CTAs and the active-tab indicator only.
  static let rausch = Color(hex: 0xFF385C)
  /// Pressed and active states of anything filled with ``rausch``.
  static let rauschDeep = Color(hex: 0xE00B41)
  /// Product-tier accents. The only colours allowed beside ``rausch``.
  static let plusMagenta = Color(hex: 0x92174D)
  static let luxePurple = Color(hex: 0x460479)

  static let canvas = Color(hex: 0xFFFFFF)
  /// Subsurface tint for sections that should step back from the white canvas.
  static let softCloud = Color(hex: 0xF7F7F7)
  /// The 1pt workhorse: every card-to-card and row-to-row divider.
  static let hairline = Color(hex: 0xDDDDDD)

  /// The system's near-black. Roughly 90% of all text, and never pure black.
  static let ink = Color(hex: 0x222222)
  /// Focused input text and one-step-down emphasis.
  static let charcoal = Color(hex: 0x3F3F3F)
  /// Secondary labels and subtitle copy.
  static let ash = Color(hex: 0x6A6A6A)
  /// Disabled controls and low-priority metadata.
  static let mute = Color(hex: 0x929292)
  /// Tertiary dividers, icon strokes, placeholder avatars.
  static let stone = Color(hex: 0xC1C1C1)

  static let errorRed = Color(hex: 0xC13515)
  static let deepError = Color(hex: 0xB32505)
  /// Legal and informational links — the one non-monochrome link colour.
  static let infoBlue = Color(hex: 0x428BFF)
}

extension Color {
  init(hex: UInt32) {
    self.init(
      red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255,
      blue: Double(hex & 0xFF) / 255)
  }
}

// MARK: - Typography

/// Airbnb Cereal VF is proprietary; Manrope is the closest face already bundled
/// with both clients. Only 500/600/700 are used — the system has no 400-regular,
/// so body copy sits a notch heavier than the platform default.
enum Cereal {
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

  /// Page display, e.g. "Your trips".
  static let displaySmall = TypeStyle(
    face: Cereal.bold, size: 32, tracking: -0.6, relativeTo: .largeTitle)
  /// Section heading.
  static let headlineMedium = TypeStyle(
    face: Cereal.bold, size: 28, tracking: -0.5, relativeTo: .title)
  /// Subsection heading / content divider.
  static let headlineSmall = TypeStyle(
    face: Cereal.semibold, size: 22, tracking: -0.44, relativeTo: .title2)
  /// Listing title.
  static let titleLarge = TypeStyle(
    face: Cereal.semibold, size: 20, tracking: -0.18, relativeTo: .title3)
  /// Subtitle bold: host name, city name.
  static let titleMedium = TypeStyle(
    face: Cereal.semibold, size: 16, tracking: 0, relativeTo: .headline)
  /// Body medium — the system's "regular".
  static let bodyLarge = TypeStyle(
    face: Cereal.medium, size: 16, tracking: 0, relativeTo: .body)
  /// Caption medium: metadata and subtitle lines.
  static let bodyMedium = TypeStyle(
    face: Cereal.medium, size: 14, tracking: 0, relativeTo: .subheadline)
  /// Caption small: dates, micro-metadata.
  static let bodySmall = TypeStyle(
    face: Cereal.medium, size: 13, tracking: 0, relativeTo: .footnote)
  /// Button large.
  static let labelLarge = TypeStyle(
    face: Cereal.medium, size: 16, tracking: 0, relativeTo: .callout)
  /// Caption bold: numeric stats, small-text emphasis.
  static let labelMedium = TypeStyle(
    face: Cereal.semibold, size: 14, tracking: 0, relativeTo: .subheadline)
  /// Compact badge. Sentence case: the system allows no uppercase above 8pt.
  static let labelSmall = TypeStyle(
    face: Cereal.semibold, size: 11, tracking: 0, relativeTo: .caption2)
  /// The one uppercase role in the system: price footnotes and decimal tails.
  static let superscript = TypeStyle(
    face: Cereal.bold, size: 8, tracking: 0.32, relativeTo: .caption2)
}

extension View {
  /// Applies a Compose text style: face, size and tracking together, since
  /// setting the font without its letterSpacing loses half the character.
  func typeStyle(_ style: TypeStyle) -> some View {
    font(style.font).tracking(style.tracking)
  }
}

// MARK: - Radius and elevation

/// The system's radius scale. Circular icon buttons and avatars use `.circle`
/// instead — 50% is the signature geometry and never approximated.
enum Radius {
  /// Inline tags and chips.
  static let chip: CGFloat = 4
  /// Buttons, inputs, dropdowns.
  static let control: CGFloat = 8
  /// Listing photography, generic containers, badges.
  static let card: CGFloat = 14
  /// Pill buttons, hero images, the booking panel.
  static let panel: CGFloat = 20
  /// The search pill and extra-large containers.
  static let pill: CGFloat = 32
}

extension View {
  /// The signature three-layer lift: three low-opacity shadows that read as one
  /// cohesive elevation. Panels and sheets only — never a listing card.
  func panelElevation() -> some View {
    self
      .shadow(color: .black.opacity(0.02), radius: 0.5, x: 0, y: 0)
      .shadow(color: .black.opacity(0.04), radius: 3, x: 0, y: 2)
      .shadow(color: .black.opacity(0.10), radius: 4, x: 0, y: 4)
  }

  func canvasScreen() -> some View {
    scrollContentBackground(.hidden).background(Palette.canvas).foregroundStyle(Palette.ink)
  }
}

/// A 1pt hairline. Every card-to-card and row-to-row separation in the system.
struct HairlineDivider: View {
  var body: some View {
    Rectangle().fill(Palette.hairline).frame(height: 1)
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
  case .past: "Past trip"
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

// MARK: - Buttons

/// The Rausch CTA. One per surface: the moment the whole grayscale palette
/// exists to set up. Pressing scales to 0.92 rather than tinting or lifting.
struct PrimaryButton: View {
  let title: String
  var busy = false
  var icon: String?
  let action: () -> Void
  var body: some View {
    Button(action: action) {
      HStack(spacing: 8) {
        if busy {
          ProgressView().tint(Palette.canvas)
        } else {
          if let icon { Image(systemName: icon).font(.system(size: 16)) }
          Text(title).typeStyle(.labelLarge)
        }
      }
      .frame(maxWidth: .infinity).frame(height: 48)
    }
    .buttonStyle(RauschButtonStyle())
    .disabled(busy)
  }
}

private struct RauschButtonStyle: ButtonStyle {
  @Environment(\.isEnabled) private var enabled
  func makeBody(configuration: Configuration) -> some View {
    configuration.label
      .foregroundStyle(enabled ? Palette.canvas : Palette.stone)
      .background(
        enabled ? Palette.rausch : Palette.softCloud,
        in: RoundedRectangle(cornerRadius: Radius.control))
      .scaleEffect(configuration.isPressed ? 0.92 : 1)
      .animation(.spring(duration: 0.2), value: configuration.isPressed)
  }
}

/// White, hairline-bordered, ink label. Every action that is not the one CTA.
struct SecondaryButton: View {
  let title: String
  var pill = false
  var icon: String?
  let action: () -> Void
  var body: some View {
    Button(action: action) {
      HStack(spacing: 8) {
        if let icon { Image(systemName: icon).font(.system(size: 16)) }
        Text(title).typeStyle(.labelLarge)
      }
      .frame(maxWidth: .infinity).frame(height: 48)
    }
    .buttonStyle(OutlineButtonStyle(radius: pill ? Radius.panel : Radius.control))
  }
}

private struct OutlineButtonStyle: ButtonStyle {
  let radius: CGFloat
  func makeBody(configuration: Configuration) -> some View {
    configuration.label
      .foregroundStyle(Palette.ink)
      .background(Palette.canvas, in: RoundedRectangle(cornerRadius: radius))
      .overlay(RoundedRectangle(cornerRadius: radius).stroke(Palette.hairline, lineWidth: 1))
      .scaleEffect(configuration.isPressed ? 0.92 : 1)
      .animation(.spring(duration: 0.2), value: configuration.isPressed)
  }
}

/// The circular icon button that recurs throughout the system — back, share,
/// options, carousel controls. Always 50%, never any other geometry.
struct CircleIconButton: View {
  let symbol: String
  let label: String
  /// On photography the button turns white and takes a hairline ring so it
  /// separates from whatever colour happens to sit behind it.
  var onPhotograph = false
  let action: () -> Void
  var body: some View {
    Button(action: action) {
      Image(systemName: symbol).font(.system(size: 16, weight: .medium))
        .foregroundStyle(Palette.ink).frame(width: 44, height: 44)
    }
    .buttonStyle(CircleButtonStyle(onPhotograph: onPhotograph))
    .accessibilityLabel(label)
  }
}

private struct CircleButtonStyle: ButtonStyle {
  let onPhotograph: Bool
  func makeBody(configuration: Configuration) -> some View {
    configuration.label
      .background(onPhotograph ? Palette.canvas : Palette.softCloud, in: Circle())
      .overlay {
        if onPhotograph { Circle().stroke(Palette.hairline, lineWidth: 1) }
      }
      .scaleEffect(configuration.isPressed ? 0.92 : 1)
      .animation(.spring(duration: 0.2), value: configuration.isPressed)
  }
}

// MARK: - Feedback

struct Notice: View {
  let text: String
  var isError = false
  var body: some View {
    Text(text)
      .typeStyle(.bodyMedium).foregroundStyle(isError ? Palette.errorRed : Palette.ash)
      .frame(maxWidth: .infinity, alignment: .leading).padding(14)
      .background(isError ? Palette.canvas : Palette.softCloud,
                  in: RoundedRectangle(cornerRadius: Radius.control))
      .overlay {
        if isError {
          RoundedRectangle(cornerRadius: Radius.control).stroke(Palette.errorRed, lineWidth: 1)
        }
      }
      .accessibilityAddTraits(.updatesFrequently)
  }
}

// MARK: - Photography

/// Cover photography at the aspect ratio the surface calls for: 4:3 in the
/// listing grid, 16:9 for a detail-page hero. Text never sits on top of it.
struct TripArtwork: View {
  let trip: Trip
  let url: URL?
  var aspect: CGFloat = 4 / 3
  var body: some View {
    Color.clear
      .aspectRatio(aspect, contentMode: .fit)
      .overlay {
        ZStack {
          // A placeholder should read as an unloaded image, not as a second
          // accent colour competing with Rausch.
          Palette.softCloud
          Image(systemName: "photo")
            .font(.system(size: 28, weight: .light))
            .foregroundStyle(Palette.stone)
          AsyncImage(url: url) { image in
            image.resizable().scaledToFill()
          } placeholder: {
            Color.clear
          }
          if trip.coverStatus == "pending" {
            Text("Developing your cover…").typeStyle(.labelSmall).foregroundStyle(Palette.ink)
              .padding(.horizontal, 10).padding(.vertical, 6)
              .background(Palette.canvas, in: RoundedRectangle(cornerRadius: Radius.card))
              .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
              .padding(12)
          }
        }
      }
      .clipped()
      .accessibilityLabel(trip.destination.map { "Destination cover for \($0)" } ?? "Trip cover")
  }
}

// MARK: - Dates

private let months = [
  "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
]

/// "Sep 12", matching `shortDate` in the Android Format.kt. Dates arrive as
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

/// One small fact beside a glyph, kept on one line.
struct MetaLabel: View {
  let symbol: String
  let text: String
  var body: some View {
    HStack(spacing: 6) {
      Image(systemName: symbol).font(.system(size: 12)).foregroundStyle(Palette.ash)
      Text(text).typeStyle(.bodyMedium).foregroundStyle(Palette.ash).lineLimit(1)
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
      VStack(alignment: .leading, spacing: 4) { labels }
    }
  }
  @ViewBuilder private var labels: some View {
    if let place = trip.destination, !place.isEmpty {
      MetaLabel(symbol: "mappin.and.ellipse", text: place)
    }
    MetaLabel(symbol: "calendar", text: dateRange(trip.startDate, trip.endDate))
  }
}

/// The boarding strip. Rausch is the one place a figure earns colour, so the
/// fill uses it and the track stays hairline.
struct BudgetMeter: View {
  let spent: Decimal
  let budget: Decimal
  var fill: Color = Palette.rausch
  var track: Color = Palette.hairline
  private var ratio: Double {
    guard budget > 0 else { return 0 }
    let value =
      (spent as NSDecimalNumber).doubleValue / (budget as NSDecimalNumber).doubleValue
    return min(max(value, 0), 1)
  }
  var body: some View {
    GeometryReader { geometry in
      ZStack(alignment: .leading) {
        Capsule().fill(track)
        Capsule().fill(fill).frame(width: geometry.size.width * ratio)
      }
    }
    .frame(height: 4)
    .accessibilityElement()
    .accessibilityLabel("Budget progress")
    .accessibilityValue("\(Int(ratio * 100)) percent of budget spent")
  }
}

/// The award-badge lockup: a white pill riding the corner of a photograph.
struct PhaseBadge: View {
  let trip: Trip
  var body: some View {
    Text(phaseLabel(trip)).typeStyle(.labelSmall).foregroundStyle(Palette.ink)
      .padding(.horizontal, 10).padding(.vertical, 6)
      .background(Palette.canvas, in: RoundedRectangle(cornerRadius: Radius.card))
  }
}
