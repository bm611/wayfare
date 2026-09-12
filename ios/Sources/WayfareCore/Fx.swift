import Foundation

public func rateBetween(from: String, to: String, rates: [String: Decimal]) -> Decimal? {
  if from == to { return 1 }
  guard let fromRate = rates[from], fromRate > 0, let toRate = rates[to], toRate > 0 else {
    return nil
  }
  return rounded(toRate / fromRate, scale: 8)
}

public func convert(amount: Decimal, from: String, to: String, rates: [String: Decimal]) -> Decimal?
{
  rateBetween(from: from, to: to, rates: rates).map { rounded(amount * $0) }
}

public struct FxSnapshot: Codable, Sendable {
  public var rates: [String: Decimal]
  public var date: String
  public var fetchedAt: Date
  public var stale: Bool { Date().timeIntervalSince(fetchedAt) > 12 * 60 * 60 }
  public init(rates: [String: Decimal], date: String, fetchedAt: Date) {
    self.rates = rates
    self.date = date
    self.fetchedAt = fetchedAt
  }

  public static let fallback = FxSnapshot(
    rates: [
      "EUR": "1", "USD": "1.1578", "GBP": "0.8587", "CHF": "0.9424", "SEK": "11.1575",
      "NOK": "10.809",
      "DKK": "7.475", "PLN": "4.3265", "CZK": "24.191", "HUF": "368.2", "RON": "5.2558",
      "ISK": "140.6",
      "TRY": "55.9145", "JPY": "184.78", "CNY": "7.7822", "HKD": "9.0801", "SGD": "1.4741",
      "KRW": "1577.57", "INR": "109.962", "IDR": "20511.18", "MYR": "4.6839", "PHP": "72.415",
      "THB": "38.468", "ILS": "3.5064", "AUD": "1.6199", "NZD": "1.9868", "CAD": "1.6122",
      "BRL": "5.9635", "MXN": "19.6835", "ZAR": "18.6341",
    ].mapValues { Decimal(string: $0, locale: Locale(identifier: "en_US_POSIX"))! },
    date: "2026-09-02 snapshot", fetchedAt: Date(timeIntervalSince1970: 0))
}
