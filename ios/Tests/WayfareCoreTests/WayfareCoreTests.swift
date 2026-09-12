import Foundation
import Testing

@testable import WayfareCore

private func trip(
  start: String? = "2026-09-10", end: String? = "2026-09-14", budget: Decimal = 1000
) -> Trip {
  Trip(id: "t", userId: "u", name: "Lisbon", startDate: start, endDate: end, budget: budget)
}

@Test func androidTripPhasesAndDSTBoundary() {
  #expect(tripPhase(trip(), today: "2026-09-10") == .active(day: 1, total: 5))
  #expect(tripPhase(trip(), today: "2026-09-14") == .active(day: 5, total: 5))
  #expect(tripPhase(trip(), today: "2026-09-15") == .past)
  #expect(tripPhase(trip(start: "2026-09-17", end: nil), today: "2026-09-10") == .upcoming(days: 7))
  #expect(tripPhase(trip(start: nil, end: nil), today: "2026-09-10") == .undated)
  #expect(
    tripPhase(trip(start: "2026-03-28", end: "2026-03-31"), today: "2026-03-30")
      == .active(day: 3, total: 4))
}

@Test func budgetIncludesPendingAndExcludesFailed() {
  let expenses = [
    Expense(amount: 100), Expense(amount: 50, syncState: .pending),
    Expense(amount: 25, syncState: .failed),
  ]
  let summary = budgetSummary(trip(budget: 900), expenses: expenses, today: "2026-09-12")
  #expect(summary.spent == 150)
  #expect(summary.remaining == 750)
  #expect(summary.elapsedDays == 3)
  #expect(summary.perDay == 50)
  #expect(summary.daysLeft == 3)
  #expect(summary.availablePerDay == 250)
}

@Test func overBudgetAndPreTripBookingsKeepAndroidSemantics() {
  let expenses = [Expense(amount: Decimal(string: "901.01")!, spentOn: "2026-08-01")]
  let active = budgetSummary(trip(budget: 900), expenses: expenses, today: "2026-09-12")
  #expect(active.remaining == Decimal(string: "-1.01"))
  #expect(active.availablePerDay == 0)
  #expect(active.perDay == Decimal(string: "300.34"))
  let upcoming = budgetSummary(trip(budget: 900), expenses: expenses, today: "2026-09-09")
  #expect(upcoming.perDay == nil)
  #expect(upcoming.daysLeft == nil)
  #expect(upcoming.spent == Decimal(string: "901.01"))
  #expect(FxSnapshot.fallback.rates["IDR"] == Decimal(string: "20511.18"))
}

@Test func fxUsesEightRatePlacesAndHalfUpMoneyRounding() {
  let rates: [String: Decimal] = [
    "EUR": 1, "USD": Decimal(string: "1.1578")!, "GBP": Decimal(string: "0.8587")!,
  ]
  #expect(rateBetween(from: "USD", to: "GBP", rates: rates) == Decimal(string: "0.74166523"))
  #expect(rateBetween(from: "GBP", to: "USD", rates: rates) == Decimal(string: "1.34831722"))
  #expect(
    convert(amount: Decimal(string: "1.005")!, from: "EUR", to: "EUR", rates: rates)
      == Decimal(string: "1.01"))
  #expect(rateBetween(from: "BAD", to: "EUR", rates: ["BAD": 0, "EUR": 1]) == nil)
}

@Test func JSONAllowsDecimalStringsAndDefaultsLocalExpenseFields() throws {
  let json =
    #"{"id":"e","trip_id":"t","user_id":"u","title":"Tea","amount":"12.30","original_amount":"10.25","original_currency":"GBP","fx_rate":"1.2","category":"food","spent_on":"2026-09-10","note":null,"created_at":"now"}"#
    .data(using: .utf8)!
  let decoder = JSONDecoder()
  decoder.keyDecodingStrategy = .convertFromSnakeCase
  let expense = try decoder.decode(Expense.self, from: json)
  #expect(expense.amount == Decimal(string: "12.30"))
  #expect(expense.syncState == .synced)
  #expect(expense.syncError == nil)
}
