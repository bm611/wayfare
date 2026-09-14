import SwiftUI
import UIKit
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

  static let canvas = Color(light: 0xFFFFFF, dark: 0x181A1B)
  /// Subsurface tint for sections that should step back from the white canvas.
  static let softCloud = Color(light: 0xF7F7F7, dark: 0x242729)
  /// The 1pt workhorse: every card-to-card and row-to-row divider.
  static let hairline = Color(light: 0xDDDDDD, dark: 0x44484B)

  /// The system's near-black. Roughly 90% of all text, and never pure black.
  static let ink = Color(light: 0x222222, dark: 0xF3F3F2)
  /// Focused input text and one-step-down emphasis.
  static let charcoal = Color(light: 0x3F3F3F, dark: 0xE2E3E3)
  /// Secondary labels and subtitle copy.
  static let ash = Color(light: 0x6A6A6A, dark: 0xB6B9BB)
  /// Disabled controls and low-priority metadata.
  static let mute = Color(light: 0x929292, dark: 0x94999D)
  /// Tertiary dividers, icon strokes, placeholder avatars.
  static let stone = Color(light: 0xC1C1C1, dark: 0x70777C)

  static let errorRed = Color(light: 0xC13515, dark: 0xFF927D)
  static let deepError = Color(hex: 0xB32505)
  /// Legal and informational links — the one non-monochrome link colour.
  static let infoBlue = Color(hex: 0x428BFF)
}

extension Color {
  init(light: UInt32, dark: UInt32) {
    self.init(uiColor: UIColor { traits in
      let hex = traits.userInterfaceStyle == .dark ? dark : light
      return UIColor(red: Double((hex >> 16) & 255) / 255,
                     green: Double((hex >> 8) & 255) / 255,
                     blue: Double(hex & 255) / 255, alpha: 1)
    })
  }

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

// MARK: - Motion

extension Animation {
  /// A stronger ease-out than the system default. Reserved for content a tap
  /// reveals directly (search, filters) so it reads as an instant response,
  /// not a decorative entrance — mirrors Android's `EaseOutStrong`.
  static func easeOutStrong(_ duration: Double) -> Animation {
    .timingCurve(0.23, 1, 0.32, 1, duration: duration)
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
/// exists to set up. Pressing scales to 0.98 rather than tinting or lifting.
struct PrimaryButton: View {
  let title: String
  var busy = false
  var icon: String?
  let action: () -> Void
  var body: some View {
    Button(action: action) {
      HStack(spacing: 8) {
        if busy {
          ProgressView().tint(.white)
        } else {
          if let icon { Image(systemName: icon).font(.system(size: 16)) }
          Text(title).typeStyle(.labelLarge)
        }
      }
      .frame(maxWidth: .infinity).frame(minHeight: 48)
    }
    .buttonStyle(RauschButtonStyle())
    .accessibilityLabel(title).accessibilityValue(busy ? "Saving" : "")
    .disabled(busy)
  }
}

private struct RauschButtonStyle: ButtonStyle {
  @Environment(\.accessibilityReduceMotion) private var reduceMotion
  @Environment(\.isEnabled) private var enabled
  func makeBody(configuration: Configuration) -> some View {
    configuration.label
      .foregroundStyle(enabled ? Color.white : Palette.stone)
      .background(
        enabled ? Palette.rausch : Palette.softCloud,
        in: RoundedRectangle(cornerRadius: Radius.control))
      .scaleEffect(configuration.isPressed && !reduceMotion ? 0.98 : 1)
      .animation(reduceMotion ? nil : .spring(duration: 0.2), value: configuration.isPressed)
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
      .frame(maxWidth: .infinity).frame(minHeight: 48)
    }
    .buttonStyle(OutlineButtonStyle(radius: pill ? Radius.panel : Radius.control))
  }
}

private struct OutlineButtonStyle: ButtonStyle {
  @Environment(\.accessibilityReduceMotion) private var reduceMotion
  let radius: CGFloat
  func makeBody(configuration: Configuration) -> some View {
    configuration.label
      .foregroundStyle(Palette.ink)
      .background(Palette.canvas, in: RoundedRectangle(cornerRadius: radius))
      .overlay(RoundedRectangle(cornerRadius: radius).stroke(Palette.hairline, lineWidth: 1))
      .scaleEffect(configuration.isPressed && !reduceMotion ? 0.98 : 1)
      .animation(reduceMotion ? nil : .spring(duration: 0.2), value: configuration.isPressed)
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
  /// A toggle that is on: white with an Ink ring, the same switch to Ink a
  /// focused field makes, so the state reads at a glance without the accent.
  var active = false
  let action: () -> Void
  var body: some View {
    Button(action: action) {
      Image(systemName: symbol).font(.system(size: 16, weight: .medium))
        .foregroundStyle(Palette.ink).frame(width: 44, height: 44)
    }
    .buttonStyle(CircleButtonStyle(onPhotograph: onPhotograph, active: active))
    .accessibilityLabel(label)
  }
}

private struct CircleButtonStyle: ButtonStyle {
  @Environment(\.accessibilityReduceMotion) private var reduceMotion
  let onPhotograph: Bool
  let active: Bool
  func makeBody(configuration: Configuration) -> some View {
    configuration.label
      .background(active || onPhotograph ? Palette.canvas : Palette.softCloud, in: Circle())
      .overlay {
        if active {
          Circle().strokeBorder(Palette.ink, lineWidth: 1.5)
        } else if onPhotograph {
          Circle().stroke(Palette.hairline, lineWidth: 1)
        }
      }
      .scaleEffect(configuration.isPressed && !reduceMotion ? 0.98 : 1)
      .animation(reduceMotion ? nil : .spring(duration: 0.2), value: configuration.isPressed)
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

// MARK: - Inputs

extension View {
  /// A text input: white behind a hairline border at the control radius,
  /// switching to Ink on focus. The system never tints a field with the accent.
  func fieldChrome(focused: Bool = false, invalid: Bool = false) -> some View {
    padding(.horizontal, 16).frame(maxWidth: .infinity, minHeight: 48, alignment: .leading)
      .background(Palette.canvas, in: RoundedRectangle(cornerRadius: Radius.control))
      .overlay(
        RoundedRectangle(cornerRadius: Radius.control)
          .stroke(invalid ? Palette.errorRed : focused ? Palette.ink : Palette.hairline, lineWidth: 1))
  }
}

/// A label set above its input, the way the sign-in fields read. The visible
/// label is hidden from VoiceOver because the control inside carries it.
struct LabeledField<Content: View>: View {
  let label: String
  var focused = false
  var error: String?
  @ViewBuilder let content: Content
  var body: some View {
    VStack(alignment: .leading, spacing: 8) {
      Text(label).typeStyle(.bodyMedium).foregroundStyle(Palette.ash).accessibilityHidden(true)
      content.fieldChrome(focused: focused, invalid: error != nil)
      if let error { FieldError(text: error) }
    }
  }
}

struct WayfareTextField: View {
  let label: String
  @Binding var text: String
  var prompt = ""
  var error: String?
  /// A range makes the field grow vertically. Applied to the field alone, since
  /// a line limit set from outside would also stretch the label above it.
  var lines: ClosedRange<Int>?
  @FocusState private var focused: Bool
  var body: some View {
    LabeledField(label: label, focused: focused, error: error) {
      TextField(
        label, text: $text, prompt: Text(prompt).foregroundStyle(Palette.ash),
        axis: lines == nil ? .horizontal : .vertical
      )
      .lineLimit(lines ?? 1...1)
      .typeStyle(.bodyLarge).foregroundStyle(focused ? Palette.charcoal : Palette.ink)
      .focused($focused)
      .onChange(of: error) { _, message in if message != nil { focused = true } }
      .padding(.vertical, lines == nil ? 0 : 12)
    }
  }
}

/// A choice from a menu, drawn as a field rather than as a tinted control.
struct MenuField<Options: View>: View {
  let label: String
  let value: String
  @ViewBuilder let options: Options
  var body: some View {
    LabeledField(label: label) {
      Menu {
        options
      } label: {
        HStack(spacing: 8) {
          Text(value).typeStyle(.bodyLarge).foregroundStyle(Palette.ink).fixedSize(horizontal: false, vertical: true)
          Spacer(minLength: 8)
          Image(systemName: "chevron.up.chevron.down").font(.system(size: 12, weight: .medium))
            .foregroundStyle(Palette.ash)
        }
        .frame(minHeight: 48).contentShape(Rectangle())
      }
      .accessibilityLabel(label).accessibilityValue(value)
    }
  }
}

/// The reserve bar: a sheet's one primary action pinned under its content with
/// a hairline above, so it never scrolls away or sits inside a row. An error
/// shows directly above the button that caused it.
struct ActionBar<Content: View>: View {
  var error: String?
  @ViewBuilder let content: Content
  var body: some View {
    VStack(spacing: 0) {
      HairlineDivider()
      VStack(spacing: 12) {
        if let error { Notice(text: error, isError: true) }
        content
      }
      .padding(.horizontal, 24).padding(.vertical, 12)
      .frame(maxWidth: 700)
    }
    .frame(maxWidth: .infinity)
    .background(Palette.canvas)
  }
}

// MARK: - Photography

/// Cover photography at the aspect ratio the surface calls for: 4:3 in the
/// listing grid, 16:9 for a detail-page hero. Text never sits on top of it.
struct TripArtwork: View {
  let trip: Trip
  let url: URL?
  var aspect: CGFloat = 4 / 3
  var showStatus = true
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
          if showStatus && trip.coverStatus == "pending" {
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

/// A compact budget rail with an optional percentage readout on detail views.
struct BudgetMeter: View {
  let spent: Decimal
  let budget: Decimal
  var showLabel = false
  var fill: Color = Palette.rausch
  var track: Color = Palette.softCloud
  private var rawRatio: Double {
    guard budget > 0 else { return 0 }
    return max(
      (spent as NSDecimalNumber).doubleValue / (budget as NSDecimalNumber).doubleValue, 0)
  }
  private var ratio: Double {
    min(rawRatio, 1)
  }
  var body: some View {
    VStack(spacing: 8) {
      if showLabel {
        HStack {
          Text("Budget used").typeStyle(.bodySmall).foregroundStyle(Palette.ash)
          Spacer()
          Text("\(Int((rawRatio * 100).rounded()))%")
            .typeStyle(.labelMedium).foregroundStyle(Palette.ash).monospacedDigit()
        }
      }
      GeometryReader { geometry in
        ZStack(alignment: .leading) {
          Capsule().fill(track)
          Capsule().fill(rawRatio > 1 ? Palette.errorRed : fill)
            .frame(width: geometry.size.width * ratio)
        }
      }
      .frame(height: 4)
    }
    .accessibilityElement()
    .accessibilityLabel("Budget progress")
    .accessibilityValue("\(Int((rawRatio * 100).rounded())) percent of budget spent")
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

/// Destination leads; a distinct trip name adds context without repeating it.
struct TripIdentity: View {
  let trip: Trip
  var showPhase = false
  private var destination: String { trip.destination?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "" }
  var body: some View {
    VStack(alignment: .leading, spacing: 8) {
      HStack(alignment: .firstTextBaseline, spacing: 8) {
        if !destination.isEmpty {
          Image(systemName: "mappin.and.ellipse").font(.system(size: 17)).foregroundStyle(Palette.ash)
            .accessibilityHidden(true)
        }
        Text(destination.isEmpty ? trip.name : destination).typeStyle(.headlineSmall)
          .fixedSize(horizontal: false, vertical: true)
      }
      if !destination.isEmpty && destination.caseInsensitiveCompare(trip.name.trimmingCharacters(in: .whitespacesAndNewlines)) != .orderedSame {
        Text(trip.name).typeStyle(.bodyMedium).foregroundStyle(Palette.ash)
          .fixedSize(horizontal: false, vertical: true)
      }
      ViewThatFits(in: .horizontal) {
        HStack(spacing: 12) { dates }
        VStack(alignment: .leading, spacing: 6) { dates }
      }
    }
  }
  @ViewBuilder private var dates: some View {
    Label(dateRange(trip.startDate, trip.endDate), systemImage: "calendar")
      .typeStyle(.bodyMedium).foregroundStyle(Palette.ash).fixedSize(horizontal: false, vertical: true)
    if showPhase && tripPhase(trip) != .undated {
      Text(phaseLabel(trip)).typeStyle(.labelMedium).foregroundStyle(Palette.ash)
    }
  }
}

struct FieldError: View {
  let text: String
  var body: some View {
    Text(text).typeStyle(.bodySmall).foregroundStyle(Palette.errorRed)
      .fixedSize(horizontal: false, vertical: true)
      .accessibilityAddTraits(.updatesFrequently)
  }
}

struct TripLoadingSkeleton: View {
  var body: some View {
    VStack(alignment: .leading, spacing: 12) {
      RoundedRectangle(cornerRadius: 20).fill(Palette.softCloud).aspectRatio(8 / 5, contentMode: .fit)
      RoundedRectangle(cornerRadius: 4).fill(Palette.hairline).frame(width: 210, height: 24)
    }
    .padding(18).frame(maxWidth: .infinity, alignment: .leading)
    .background(Palette.softCloud, in: RoundedRectangle(cornerRadius: 35, style: .continuous))
    .accessibilityElement(children: .ignore).accessibilityLabel("Loading trips")
  }
}
