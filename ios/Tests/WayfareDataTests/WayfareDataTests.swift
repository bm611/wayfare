import Foundation
import Testing
import WayfareCore

@testable import WayfareData

private final class StubProtocol: URLProtocol, @unchecked Sendable {
  @MainActor static var handler: (URLRequest) throws -> (Int, Data) = { _ in
    throw URLError(.notConnectedToInternet)
  }
  @MainActor static var requests: [URLRequest] = []
  override class func canInit(with request: URLRequest) -> Bool { true }
  override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
  override func startLoading() {
    Task { @MainActor in
      Self.requests.append(request)
      do {
        let (status, data) = try Self.handler(request)
        let response = HTTPURLResponse(
          url: request.url!, statusCode: status, httpVersion: nil, headerFields: nil)!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: data)
        client?.urlProtocolDidFinishLoading(self)
      } catch { client?.urlProtocol(self, didFailWithError: error) }
    }
  }
  override func stopLoading() {}
}

@Suite(.serialized) @MainActor
struct DataTests {
  private func makeStore(directory: URL) -> AppStore {
    let config = URLSessionConfiguration.ephemeral
    config.protocolClasses = [StubProtocol.self]
    return AppStore(
      configuration: .init(url: URL(string: "https://test.invalid")!, key: "public-test-key"),
      http: URLSession(configuration: config), defaults: UserDefaults(suiteName: "wayfare-tests")!,
      directory: directory, readSession: { nil }, writeSession: { _ in }, clearSession: {})
  }
  private func account(_ id: String = "alice") -> Session {
    Session(
      accessToken: "test-token", refreshToken: "test-refresh",
      expiresAt: Date().addingTimeInterval(3600), user: User(id: id))
  }
  private func temporary() -> URL {
    FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
  }

  @Test func offlineExpenseSurvivesRestartAndReconcilesWithoutSecondInsert() async throws {
    let directory = temporary()
    defer { try? FileManager.default.removeItem(at: directory) }
    StubProtocol.requests = []
    StubProtocol.handler = { _ in throw URLError(.notConnectedToInternet) }
    let store = makeStore(directory: directory)
    try store.adopt(account())
    let expense = Expense(
      tripId: "trip", userId: "alice", title: "Train to Porto", amount: Decimal(string: "37.42")!)
    try await store.saveExpense(expense, isNew: true)
    #expect(store.expenses.first?.syncState == .pending)
    let restarted = makeStore(directory: directory)
    try restarted.adopt(account())
    #expect(restarted.expenses.first?.amount == Decimal(string: "37.42"))
    #expect(restarted.expenses.first?.id == expense.id)
    StubProtocol.requests = []
    // Simulates an earlier POST succeeding but its response being lost.
    StubProtocol.handler = { request in
      #expect(request.httpMethod == "GET")
      var remote = expense
      remote.id = remote.id.lowercased()  // Postgres UUID output is lowercase.
      return (200, try AppStore.encoder.encode([remote]))
    }
    try await restarted.retryExpense(restarted.expenses[0])
    #expect(restarted.expenses.count == 1)
    #expect(restarted.expenses[0].syncState == .synced)
    #expect(StubProtocol.requests.count == 1)
  }

  @Test func rejectionStaysVisibleAndNeverAutomaticallyRetries() async throws {
    let directory = temporary()
    defer { try? FileManager.default.removeItem(at: directory) }
    let store = makeStore(directory: directory)
    try store.adopt(account())
    StubProtocol.handler = { request in
      request.httpMethod == "POST"
        ? (403, Data(#"{"message":"Membership revoked"}"#.utf8)) : (200, Data("[]".utf8))
    }
    try await store.saveExpense(
      Expense(id: "rejected", tripId: "gone", title: "Ferry", amount: 19.75), isNew: true)
    #expect(store.expenses.first?.syncState == .failed)
    StubProtocol.requests = []
    await store.refresh()
    #expect(store.expenses.first?.syncState == .failed)
    #expect(store.expenses.first?.syncError == "Membership revoked")
    #expect(!StubProtocol.requests.contains { $0.httpMethod == "POST" })
    let otherAccount = makeStore(directory: directory)
    try otherAccount.adopt(account("bob"))
    #expect(otherAccount.expenses.isEmpty)
  }

  @Test func failedDiskWriteDoesNotAcknowledgeOrSendExpense() async throws {
    let directory = temporary()
    try Data("not a directory".utf8).write(to: directory)
    defer { try? FileManager.default.removeItem(at: directory) }
    StubProtocol.requests = []
    let store = makeStore(directory: directory)
    try store.adopt(account())
    do {
      try await store.saveExpense(Expense(title: "Coffee", amount: 3.45), isNew: true)
      Issue.record("Saving should fail when the outbox cannot be persisted")
    } catch {}
    #expect(store.expenses.isEmpty)
    #expect(StubProtocol.requests.isEmpty)
  }

  @Test func payloadOmitsLocalStateAndClearsNullableFields() async throws {
    let directory = temporary()
    defer { try? FileManager.default.removeItem(at: directory) }
    let store = makeStore(directory: directory)
    try store.adopt(account())
    let expense = Expense(
      id: "edited", tripId: "trip", userId: "alice", title: "Museum", amount: 12.65)
    StubProtocol.handler = { request in
      #expect(request.httpMethod == "PATCH")
      let data: Data
      if let body = request.httpBody {
        data = body
      } else {
        let stream = try #require(request.httpBodyStream)
        stream.open()
        defer { stream.close() }
        var bytes = [UInt8](repeating: 0, count: 4096)
        let count = stream.read(&bytes, maxLength: bytes.count)
        data = Data(bytes.prefix(max(0, count)))
      }
      let body = try #require(JSONSerialization.jsonObject(with: data) as? [String: Any])
      #expect(body["note"] is NSNull)
      #expect(body["original_currency"] is NSNull)
      #expect(body["sync_state"] == nil)
      #expect(body["created_at"] == nil)
      #expect(body["user_id"] == nil)
      #expect(body["id"] == nil)
      return (200, try AppStore.encoder.encode([expense]))
    }
    try await store.saveExpense(expense, isNew: false)
    #expect(store.expenses.first?.title == "Museum")
  }

  @Test func responseFromPreviousAccountCannotPopulateNewAccount() async throws {
    let directory = temporary()
    defer { try? FileManager.default.removeItem(at: directory) }
    let store = makeStore(directory: directory)
    try store.adopt(account())
    let trip = Trip(id: "private-trip", userId: "alice", name: "Private holiday")
    StubProtocol.handler = { _ in
      try store.adopt(account("bob"))
      return (200, try AppStore.encoder.encode([trip]))
    }
    do {
      _ = try await store.saveTrip(trip, isNew: true)
      Issue.record("An old account's response must be rejected")
    } catch {}
    #expect(store.userId == "bob")
    #expect(store.trips.isEmpty)
    StubProtocol.handler = { _ in throw URLError(.notConnectedToInternet) }
  }

  @Test func insertConflictReconcilesSameUUID() async throws {
    let directory = temporary()
    defer { try? FileManager.default.removeItem(at: directory) }
    let store = makeStore(directory: directory)
    try store.adopt(account())
    let expense = Expense(
      id: "same-id", tripId: "trip", userId: "alice", title: "Lunch", amount: 17)
    var reads = 0
    StubProtocol.requests = []
    StubProtocol.handler = { request in
      if request.httpMethod == "POST" { return (409, Data("Conflict".utf8)) }
      reads += 1
      return (200, try AppStore.encoder.encode(reads == 1 ? [] : [expense]))
    }
    try await store.saveExpense(expense, isNew: true)
    #expect(store.expenses.count == 1)
    #expect(store.expenses.first?.id == "same-id")
    #expect(store.expenses.first?.syncState == .synced)
    #expect(StubProtocol.requests.filter { $0.httpMethod == "POST" }.count == 1)
    #expect(reads == 2)
  }

  @Test func listFetchesBeyondDefaultPageAndAuthRedirectUsesQuery() async throws {
    let directory = temporary()
    defer { try? FileManager.default.removeItem(at: directory) }
    let store = makeStore(directory: directory)
    try store.adopt(account())
    StubProtocol.handler = { request in
      let parts = URLComponents(url: request.url!, resolvingAgainstBaseURL: false)!
      let offset = parts.queryItems?.first { $0.name == "offset" }?.value
      return (200, try JSONEncoder().encode(offset == "0" ? Array(0..<500) : [500, 501, 502]))
    }
    let rows: [Int] = try await store.restList("expenses?order=id")
    #expect(rows.count == 503)
    #expect(rows.last == 502)
    StubProtocol.handler = { request in
      #expect(request.url?.path == "/auth/v1/recover")
      let query = URLComponents(url: request.url!, resolvingAgainstBaseURL: false)?.queryItems
      #expect(query?.first { $0.name == "redirect_to" }?.value == "wayfare-ios://auth")
      return (200, Data("{}".utf8))
    }
    try await store.resetPassword(email: "traveller@example.test")
  }
}
