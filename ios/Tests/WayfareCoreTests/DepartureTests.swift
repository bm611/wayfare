import Foundation
import Testing

@testable import WayfareCore

/// The furniture the Departure design adds: ticket stubs and the day-by-day
/// chart. Fixtures are shared with Android's `DepartureTest.kt`, so a trip has
/// to stamp the same code on both clients.
private func trip(name: String = "Lisbon & Porto", destination: String? = "Lisbon & Porto") -> Trip
{
  Trip(id: "t", userId: "u", name: name, destination: destination)
}

@Test func aTwoPartDestinationBecomesATwoLegRoute() {
  #expect(routeCode(trip()) == "LIS → POR")
  #expect(routeCode(trip(destination: "Tokyo and Kyoto")) == "TOK → KYO")
  #expect(routeCode(trip(destination: "Rome, Florence")) == "ROM → FLO")
}

@Test func aSingleDestinationIsOneStub() {
  #expect(routeCode(trip(destination: "Tokyo")) == "TOK")
}

@Test func moreThanTwoPlacesStillFitOnATicket() {
  #expect(routeCode(trip(destination: "Barcelona, Madrid, Seville")) == "BAR → MAD")
}

@Test func aBlankDestinationFallsBackToTheTripName() {
  #expect(routeCode(trip(name: "Honeymoon", destination: nil)) == "HON")
  #expect(routeCode(trip(name: "Honeymoon", destination: "")) == "HON")
}

@Test func punctuationAndAccentsNeverReachTheStub() {
  #expect(routeCode(trip(destination: "São Paulo")) == "SAO")
  #expect(routeCode(trip(name: "!!!", destination: "!!!")) == "TRIP")
}

@Test func daysAreTotalledNewestFirstAndScaledAgainstTheHeaviest() {
  let days = dayTotals([
    Expense(amount: 100, spentOn: "2026-09-10"),
    Expense(amount: 50, spentOn: "2026-09-10"),
    Expense(amount: 75, spentOn: "2026-09-11"),
  ])
  #expect(days.map(\.date) == ["2026-09-11", "2026-09-10"])
  #expect(days[1].amount == 150)
  #expect(days[1].share == 1)
  #expect(days[0].share == 0.5)
}

@Test func aFailedLineIsLeftOffTheDayChartAsItIsOffEveryTotal() {
  let days = dayTotals([
    Expense(amount: 100, spentOn: "2026-09-10"),
    Expense(amount: 40, spentOn: "2026-09-11", syncState: .failed),
  ])
  #expect(days.count == 1)
  #expect(days[0].date == "2026-09-10")
}

@Test func noExpensesMeansNoBarsRatherThanAnEmptyOne() {
  #expect(dayTotals([]).isEmpty)
}
