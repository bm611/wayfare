import SwiftUI
import WayfareCore

enum Palette {
  static let paper = Color(red: 247 / 255, green: 242 / 255, blue: 233 / 255)
  static let card = Color(red: 1, green: 252 / 255, blue: 246 / 255)
  static let ink = Color(red: 26 / 255, green: 23 / 255, blue: 20 / 255)
  static let soft = Color(red: 111 / 255, green: 100 / 255, blue: 89 / 255)
  static let clay = Color(red: 181 / 255, green: 84 / 255, blue: 60 / 255)
  static let line = Color(red: 227 / 255, green: 217 / 255, blue: 202 / 255)
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
        Text(title).fontWeight(.semibold)
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
      .font(.subheadline).foregroundStyle(Palette.clay)
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
        Text("Developing your cover…").font(.caption).padding(8)
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
