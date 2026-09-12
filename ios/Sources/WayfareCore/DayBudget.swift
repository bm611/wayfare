import Foundation

public enum Day {
  private static var calendar: Calendar {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = .current
    return calendar
  }

  public static var today: String { string(Date()) }

  public static func date(_ value: String) -> Date? {
    let parts = value.split(separator: "-").compactMap { Int($0) }
    guard parts.count == 3 else { return nil }
    return calendar.date(
      from: DateComponents(year: parts[0], month: parts[1], day: parts[2], hour: 12))
  }

  public static func string(_ date: Date) -> String {
    let parts = calendar.dateComponents([.year, .month, .day], from: date)
    return String(format: "%04d-%02d-%02d", parts.year!, parts.month!, parts.day!)
  }

  static func distance(_ from: String, _ to: String) -> Int? {
    guard let lhs = date(from), let rhs = date(to) else { return nil }
    return calendar.dateComponents([.day], from: lhs, to: rhs).day
  }
}

public enum TripPhase: Equatable, Sendable {
  case upcoming(days: Int)
  case active(day: Int, total: Int)
  case past, undated
}

public func tripPhase(_ trip: Trip, today: String = Day.today) -> TripPhase {
  guard trip.startDate != nil || trip.endDate != nil else { return .undated }
  let start = trip.startDate ?? today
  let end = trip.endDate ?? start
  guard let untilStart = Day.distance(today, start), let untilEnd = Day.distance(today, end) else {
    return .undated
  }
  if untilStart > 0 { return .upcoming(days: untilStart) }
  if untilEnd < 0 { return .past }
  return .active(
    day: (Day.distance(start, today) ?? 0) + 1, total: (Day.distance(start, end) ?? 0) + 1)
}

public struct BudgetSummary: Equatable, Sendable {
  public var spent: Decimal
  public var remaining: Decimal
  public var elapsedDays: Int
  public var perDay: Decimal?
  public var daysLeft: Int?
  public var availablePerDay: Decimal?
  public init(
    spent: Decimal, remaining: Decimal, elapsedDays: Int, perDay: Decimal?, daysLeft: Int?,
    availablePerDay: Decimal?
  ) {
    self.spent = spent
    self.remaining = remaining
    self.elapsedDays = elapsedDays
    self.perDay = perDay
    self.daysLeft = daysLeft
    self.availablePerDay = availablePerDay
  }
}

public func rounded(_ value: Decimal, scale: Int = 2) -> Decimal {
  var input = value
  var output = Decimal()
  NSDecimalRound(&output, &input, scale, .plain)
  return output
}

public func budgetSummary(_ trip: Trip, expenses: [Expense], today: String = Day.today)
  -> BudgetSummary
{
  let spent = rounded(
    expenses.lazy.filter { $0.syncState != .failed }.reduce(Decimal.zero) { $0 + $1.amount })
  let remaining = rounded(trip.budget - spent)
  let phase = tripPhase(trip, today: today)
  let elapsed: Int
  switch phase {
  case .active(let day, _): elapsed = day
  case .past:
    elapsed =
      trip.startDate.flatMap { start in trip.endDate.flatMap { Day.distance(start, $0) } }.map {
        $0 + 1
      } ?? 0
  default: elapsed = 0
  }
  let perDay = elapsed > 0 ? rounded(spent / Decimal(elapsed)) : nil
  let daysLeft: Int?
  if case .active(let day, let total) = phase, trip.endDate != nil {
    daysLeft = total - day + 1
  } else {
    daysLeft = nil
  }
  let available = daysLeft.flatMap { days -> Decimal? in
    guard days > 0, trip.budget > 0 else { return nil }
    return rounded(max(remaining, 0) / Decimal(days))
  }
  return BudgetSummary(
    spent: spent, remaining: remaining, elapsedDays: elapsed, perDay: perDay, daysLeft: daysLeft,
    availablePerDay: available)
}
