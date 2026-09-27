import Foundation

/// A trip's passport-stamp drawing, as stored in `trips.cover_art`. The server
/// has already validated and normalised every path to absolute M/L/C/Q/Z on a
/// 0 0 64 64 grid.
public struct StampArt: Codable, Equatable, Sendable {
  public var v: Int
  public var label: String
  public var paths: [String]
  public var fallback: Bool

  public init(v: Int = 1, label: String = "", paths: [String] = [], fallback: Bool = false) {
    self.v = v
    self.label = label
    self.paths = paths
    self.fallback = fallback
  }

  public init(from decoder: Decoder) throws {
    let c = try decoder.container(keyedBy: CodingKeys.self)
    v = try c.decodeIfPresent(Int.self, forKey: .v) ?? 1
    label = try c.decodeIfPresent(String.self, forKey: .label) ?? ""
    paths = try c.decodeIfPresent([String].self, forKey: .paths) ?? []
    fallback = try c.decodeIfPresent(Bool.self, forKey: .fallback) ?? false
  }
}

public enum StampTint: Int, CaseIterable, Sendable { case peach, sky, sage, lilac }

/// How a trip's stamp sits on its card. Ported from `stampStyle` in the web
/// `lib/stamp.ts`; `StampTests` pins the same fixtures as the web and Android.
public struct StampStyle: Equatable, Sendable {
  public let tint: StampTint
  public let tilt: Double
  public let arched: Bool

  public init(tint: StampTint, tilt: Double, arched: Bool) {
    self.tint = tint
    self.tilt = tilt
    self.arched = arched
  }

  public init(tripId: String) {
    let sum = tripId.utf16.reduce(0) { $0 + Int($1) }
    self.init(
      tint: StampTint(rawValue: sum % 4)!, tilt: [-3, 2.5, -1.5][(sum / 4) % 3],
      arched: (sum / 12) % 2 == 1)
  }
}

private let stampMonths = [
  "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC",
]

/// "SEP 2026" from a trip's `yyyy-MM-dd` start date; nil for an undated trip.
public func stampMonth(_ start: String?) -> String? {
  guard let start, start.count >= 7,
    let month = Int(start.dropFirst(5).prefix(2)), (1...12).contains(month)
  else { return nil }
  return "\(stampMonths[month - 1]) \(start.prefix(4))"
}

/// One command of normalised stamp path data, in grid units.
public enum StampSegment: Equatable, Sendable {
  case move(Double, Double)
  case line(Double, Double)
  case quad(Double, Double, Double, Double)
  case cubic(Double, Double, Double, Double, Double, Double)
  case close
}

/// Reads the path data the server writes: absolute M, L, Q, C and Z only.
/// Anything else means the row did not come from our validator, so the whole
/// path is refused rather than drawn half right.
public func parseStampPath(_ d: String) -> [StampSegment]? {
  let arity: [Character: Int] = ["M": 2, "L": 2, "Q": 4, "C": 6, "Z": 0]
  var tokens = d.split(whereSeparator: { $0 == " " || $0 == "," || $0.isNewline })[...]
  var segments: [StampSegment] = []
  while let token = tokens.popFirst() {
    // Commands are glued to their first number ("M4"), as the server writes them.
    guard let command = token.first, let count = arity[command] else { return nil }
    var numbers: [Double] = []
    let rest = token.dropFirst()
    if !rest.isEmpty {
      guard let n = Double(rest) else { return nil }
      numbers.append(n)
    }
    while numbers.count < count, let next = tokens.first, let n = Double(next) {
      numbers.append(n)
      tokens.removeFirst()
    }
    guard numbers.count == count, numbers.allSatisfy({ (0...64).contains($0) }) else { return nil }
    switch command {
    case "M": segments.append(.move(numbers[0], numbers[1]))
    case "L": segments.append(.line(numbers[0], numbers[1]))
    case "Q": segments.append(.quad(numbers[0], numbers[1], numbers[2], numbers[3]))
    case "C":
      segments.append(
        .cubic(numbers[0], numbers[1], numbers[2], numbers[3], numbers[4], numbers[5]))
    default: segments.append(.close)
    }
  }
  guard case .move = segments.first else { return nil }
  return segments
}
