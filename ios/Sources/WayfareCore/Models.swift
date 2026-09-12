import Foundation

public enum Category: String, Codable, CaseIterable, Sendable {
  case flights, stays, food, activities, transport, shopping, other
}

public enum SyncState: String, Codable, Sendable {
  case synced, pending, failed
}

public struct Trip: Codable, Equatable, Identifiable, Sendable {
  public var id: String
  public var userId: String
  public var name: String
  public var destination: String?
  public var startDate: String?
  public var endDate: String?
  public var budget: Decimal
  public var currency: String
  public var accent: String
  public var shareCode: String?
  public var coverPath: String?
  public var coverSubject: String?
  public var coverStatus: String
  public var createdAt: String

  public init(
    id: String = UUID().uuidString.lowercased(), userId: String = "", name: String = "",
    destination: String? = nil,
    startDate: String? = nil, endDate: String? = nil, budget: Decimal = 0, currency: String = "EUR",
    accent: String = "clay", shareCode: String? = nil, coverPath: String? = nil,
    coverSubject: String? = nil, coverStatus: String = "idle", createdAt: String = ""
  ) {
    self.id = id
    self.userId = userId
    self.name = name
    self.destination = destination
    self.startDate = startDate
    self.endDate = endDate
    self.budget = budget
    self.currency = currency
    self.accent = accent
    self.shareCode = shareCode
    self.coverPath = coverPath
    self.coverSubject = coverSubject
    self.coverStatus = coverStatus
    self.createdAt = createdAt
  }

  public init(from decoder: Decoder) throws {
    let c = try decoder.container(keyedBy: CodingKeys.self)
    id = try c.decode(String.self, forKey: .id)
    userId = try c.decode(String.self, forKey: .userId)
    name = try c.decode(String.self, forKey: .name)
    destination = try c.decodeIfPresent(String.self, forKey: .destination)
    startDate = try c.decodeIfPresent(String.self, forKey: .startDate)
    endDate = try c.decodeIfPresent(String.self, forKey: .endDate)
    budget = try c.decodeDecimal(forKey: .budget)
    currency = try c.decode(String.self, forKey: .currency)
    accent = try c.decode(String.self, forKey: .accent)
    shareCode = try c.decodeIfPresent(String.self, forKey: .shareCode)
    coverPath = try c.decodeIfPresent(String.self, forKey: .coverPath)
    coverSubject = try c.decodeIfPresent(String.self, forKey: .coverSubject)
    coverStatus = try c.decodeIfPresent(String.self, forKey: .coverStatus) ?? "idle"
    createdAt = try c.decode(String.self, forKey: .createdAt)
  }
}

public struct Expense: Codable, Equatable, Identifiable, Sendable {
  public var id: String
  public var tripId: String
  public var userId: String
  public var title: String
  public var amount: Decimal
  public var originalAmount: Decimal?
  public var originalCurrency: String?
  public var fxRate: Decimal?
  public var category: Category
  public var spentOn: String
  public var note: String?
  public var createdAt: String
  public var syncState: SyncState
  public var syncError: String?

  public init(
    id: String = UUID().uuidString.lowercased(), tripId: String = "", userId: String = "",
    title: String = "",
    amount: Decimal = 0, originalAmount: Decimal? = nil, originalCurrency: String? = nil,
    fxRate: Decimal? = nil, category: Category = .other, spentOn: String = Day.today,
    note: String? = nil, createdAt: String = "", syncState: SyncState = .synced,
    syncError: String? = nil
  ) {
    self.id = id
    self.tripId = tripId
    self.userId = userId
    self.title = title
    self.amount = amount
    self.originalAmount = originalAmount
    self.originalCurrency = originalCurrency
    self.fxRate = fxRate
    self.category = category
    self.spentOn = spentOn
    self.note = note
    self.createdAt = createdAt
    self.syncState = syncState
    self.syncError = syncError
  }

  public init(from decoder: Decoder) throws {
    let c = try decoder.container(keyedBy: CodingKeys.self)
    id = try c.decode(String.self, forKey: .id)
    tripId = try c.decode(String.self, forKey: .tripId)
    userId = try c.decode(String.self, forKey: .userId)
    title = try c.decode(String.self, forKey: .title)
    amount = try c.decodeDecimal(forKey: .amount)
    originalAmount = try c.decodeDecimalIfPresent(forKey: .originalAmount)
    originalCurrency = try c.decodeIfPresent(String.self, forKey: .originalCurrency)
    fxRate = try c.decodeDecimalIfPresent(forKey: .fxRate)
    category = try c.decode(Category.self, forKey: .category)
    spentOn = try c.decode(String.self, forKey: .spentOn)
    note = try c.decodeIfPresent(String.self, forKey: .note)
    createdAt = try c.decode(String.self, forKey: .createdAt)
    syncState = try c.decodeIfPresent(SyncState.self, forKey: .syncState) ?? .synced
    syncError = try c.decodeIfPresent(String.self, forKey: .syncError)
  }
}

public struct Member: Codable, Equatable, Identifiable, Sendable {
  public var tripId: String
  public var userId: String
  public var role: String
  public var joinedAt: String
  public var id: String { userId }
  public init(
    tripId: String = "", userId: String = "", role: String = "member", joinedAt: String = ""
  ) {
    self.tripId = tripId
    self.userId = userId
    self.role = role
    self.joinedAt = joinedAt
  }
}

public struct Profile: Codable, Equatable, Identifiable, Sendable {
  public var id: String
  public var displayName: String?
  public init(id: String = "", displayName: String? = nil) {
    self.id = id
    self.displayName = displayName
  }
}

extension KeyedDecodingContainer {
  fileprivate func decodeDecimal(forKey key: Key) throws -> Decimal {
    if let value = try? decode(Decimal.self, forKey: key) { return value }
    let string = try decode(String.self, forKey: key)
    guard let value = Decimal(string: string, locale: Locale(identifier: "en_US_POSIX")) else {
      throw DecodingError.dataCorruptedError(
        forKey: key, in: self, debugDescription: "Invalid decimal")
    }
    return value
  }

  fileprivate func decodeDecimalIfPresent(forKey key: Key) throws -> Decimal? {
    guard contains(key), try !decodeNil(forKey: key) else { return nil }
    return try decodeDecimal(forKey: key)
  }
}
