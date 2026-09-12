import Foundation

/// The furniture the "Departure" design adds on top of the ledger: the ticket
/// stub's route code and the day-by-day breakdown. Both mirror the Kotlin
/// helpers in `core/Format.kt` and `core/Budget.kt` name for name.

private let routeSeparators = CharacterSet(charactersIn: "&,/→+–—")

/// The three-letter stub on a boarding pass — "LIS → POR" for Lisbon & Porto,
/// "TOK" for Tokyo. Derived from the destination, falling back to the trip name
/// so a card never shows an empty stub.
///
/// Deliberately naive: it is stencilling that evokes an airport code, not a
/// lookup of the real one. "Porto" becoming OPO would need a database, and a
/// wrong code looks far worse than an honest abbreviation.
public func routeCode(_ trip: Trip) -> String {
  let source = trip.destination.flatMap { $0.isEmpty ? nil : $0 } ?? trip.name
  let codes =
    source
    // " and " is a word rather than a mark, so it is swapped for one first.
    .replacingOccurrences(
      of: #"\band\b"#, with: ",", options: [.regularExpression, .caseInsensitive]
    )
    .replacingOccurrences(of: " - ", with: ",")
    .components(separatedBy: routeSeparators)
    .compactMap { segment -> String? in
      // Folded to ASCII first: "São" has to stamp as SAO, not SÃO, because a
      // three-letter code is meant to read like an airport's.
      let letters = segment.folding(options: .diacriticInsensitive, locale: Locale(identifier: "en_US_POSIX"))
        .unicodeScalars
        .filter { CharacterSet.alphanumerics.contains($0) }
        .map(String.init)
        .joined()
      guard !letters.isEmpty else { return nil }
      return letters.prefix(3).uppercased()
    }
    .prefix(2)
  return codes.isEmpty ? "TRIP" : codes.joined(separator: " → ")
}

/// One bar in the day-by-day view: what a single date cost, and how it compares.
public struct DayTotal: Equatable, Identifiable, Sendable {
  public var date: String
  public var amount: Decimal
  public var share: Double
  public var id: String { date }
  public init(date: String, amount: Decimal, share: Double) {
    self.date = date
    self.amount = amount
    self.share = share
  }
}

/// The day-by-day breakdown, newest first. `share` measures each day against the
/// heaviest day rather than against the trip total, so the tallest bar always
/// fills the row and the shape of the trip stays readable on a phone.
public func dayTotals(_ expenses: [Expense]) -> [DayTotal] {
  let totals = Dictionary(
    grouping: expenses.filter { $0.syncState != .failed }, by: \.spentOn
  ).mapValues { rows in rows.reduce(Decimal.zero) { $0 + $1.amount } }
  guard let peak = totals.values.max(), peak > 0 else { return [] }
  return totals.keys.sorted().reversed().map { day in
    let amount = totals[day] ?? 0
    let share =
      (amount as NSDecimalNumber).doubleValue / (peak as NSDecimalNumber).doubleValue
    return DayTotal(date: day, amount: amount, share: min(max(share, 0), 1))
  }
}
