import SwiftUI
import WayfareCore

/// Matches `--stamp-*` in the web index.css; the dark tints are the portfolio's.
private func tintColor(_ tint: StampTint) -> Color {
  switch tint {
  case .peach: Color(light: 0xFCD5B9, dark: 0x4E2F1C)
  case .sky: Color(light: 0xC7E7F9, dark: 0x1C3645)
  case .sage: Color(light: 0xCDEAD2, dark: 0x223928)
  case .lilac: Color(light: 0xE1D8F9, dark: 0x332B44)
  }
}

/// The ink a stamp is pressed in: its own tint, taken deep enough to read as
/// text on it (and pale enough on the dark tints). Matches `--stamp-*-ink`.
private func inkColor(_ tint: StampTint) -> Color {
  switch tint {
  case .peach: Color(light: 0x8A4424, dark: 0xF6C7A6)
  case .sky: Color(light: 0x1F5A7A, dark: 0xA9D6F0)
  case .sage: Color(light: 0x2F6B3C, dark: 0xB5DFBE)
  case .lilac: Color(light: 0x5B4596, dark: 0xD0C3F5)
  }
}

/// The place a stamp names: the model's label once it has drawn one, until then
/// the place as typed, less any region after a comma.
func stampLabel(_ trip: Trip) -> String {
  if trip.coverStatus == "ready", let art = trip.coverArt, !art.label.isEmpty { return art.label }
  let place = trip.destination?.split(separator: ",").first
    .map { $0.trimmingCharacters(in: .whitespaces) } ?? ""
  return (place.isEmpty ? trip.name : place).uppercased()
}

/// Stamps already pressed onto a card this session, so scrolling a lazy list
/// back over one does not press it again.
@MainActor private var pressedStamps = Set<String>()

private let width: CGFloat = 104
private let markSize: CGFloat = 52

/// A trip's passport stamp: a tinted card with a dashed inner rule, the drawing,
/// the place and the month, all pressed in the tint's own ink and set at the
/// trip's own angle. Mirrors the web
/// `Stamp.tsx`. Until the drawing lands it is the same stamp, empty, with its
/// rule breathing while the server draws.
struct Stamp: View {
  let trip: Trip
  @Environment(\.accessibilityReduceMotion) private var reduceMotion
  @State private var pressed = false

  private var style: StampStyle { StampStyle(tripId: trip.id) }
  private var art: StampArt? { trip.coverStatus == "ready" ? trip.coverArt : nil }
  private var label: String { stampLabel(trip) }

  private var ink: Color { inkColor(style.tint) }

  var body: some View {
    VStack(spacing: 5) {
      StampDrawing(paths: art?.paths ?? [])
        .stroke(ink, style: StrokeStyle(lineWidth: 1.75 * markSize / 64, lineCap: .round, lineJoin: .round))
        .frame(width: markSize, height: markSize)
        .opacity(art == nil ? 0 : 1)
        .animation(.easeOut(duration: 0.36), value: art)
      Text(label)
        .font(.custom(Cereal.semibold, size: 13)).tracking(0.26)
        .lineLimit(1).truncationMode(.tail)
        .foregroundStyle(ink)
      // An undated stamp still gets its date line, so it never looks unfinished.
      Text(stampMonth(trip.startDate ?? trip.endDate) ?? "DATES OPEN")
        .font(.system(size: 10, design: .monospaced)).tracking(0.8)
        .foregroundStyle(ink.opacity(0.75))
    }
    .padding(.horizontal, 10).padding(.top, 14).padding(.bottom, 12)
    .frame(width: width)
    .background(tintColor(style.tint), in: shape(corner: 18, width: width))
    .overlay { rule.padding(5) }
    .rotationEffect(.degrees(style.tilt))
    .scaleEffect(pressed ? 1 : 1.12)
    .opacity(pressed ? 1 : 0)
    .onAppear {
      guard !pressed else { return }
      if reduceMotion || pressedStamps.contains(trip.id) {
        pressed = true
      } else {
        withAnimation(.easeOut(duration: 0.42)) { pressed = true }
      }
      pressedStamps.insert(trip.id)
    }
    .accessibilityHidden(true)
  }

  @ViewBuilder private var rule: some View {
    let dashed = shape(corner: 13, width: width - 10)
      .stroke(ink.opacity(0.35), style: StrokeStyle(lineWidth: 1.5, dash: [4.5, 3]))
    if trip.coverStatus == "pending" && !reduceMotion {
      dashed.phaseAnimator([1.0, 0.35]) { content, phase in content.opacity(phase) } animation: { _ in
        .easeInOut(duration: 0.8)
      }
    } else {
      dashed
    }
  }

  /// Square, or an arch whose top is a full semicircle, as on the web stamps.
  private func shape(corner: CGFloat, width: CGFloat) -> UnevenRoundedRectangle {
    let top = style.arched ? width / 2 : corner
    return UnevenRoundedRectangle(
      topLeadingRadius: top, bottomLeadingRadius: corner, bottomTrailingRadius: corner,
      topTrailingRadius: top, style: .continuous)
  }
}

/// The stamp's line drawing, scaled from the 64×64 grid to whatever frame it gets.
private struct StampDrawing: Shape {
  let paths: [String]

  func path(in rect: CGRect) -> Path {
    var path = Path()
    // Already validated server side; a path that still fails to parse is skipped, not fatal.
    for d in paths {
      guard let segments = parseStampPath(d) else { continue }
      for segment in segments {
        switch segment {
        case let .move(x, y): path.move(to: CGPoint(x: x, y: y))
        case let .line(x, y): path.addLine(to: CGPoint(x: x, y: y))
        case let .quad(cx, cy, x, y):
          path.addQuadCurve(to: CGPoint(x: x, y: y), control: CGPoint(x: cx, y: cy))
        case let .cubic(c1x, c1y, c2x, c2y, x, y):
          path.addCurve(
            to: CGPoint(x: x, y: y), control1: CGPoint(x: c1x, y: c1y),
            control2: CGPoint(x: c2x, y: c2y))
        case .close: path.closeSubpath()
        }
      }
    }
    let scale = rect.width / 64
    return path.applying(CGAffineTransform(scaleX: scale, y: scale).translatedBy(x: rect.minX / scale, y: rect.minY / scale))
  }
}
