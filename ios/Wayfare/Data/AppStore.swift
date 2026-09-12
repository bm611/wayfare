import CryptoKit
import Foundation
import Observation
import Security
import WayfareCore

#if canImport(AuthenticationServices)
  import AuthenticationServices
#endif
#if canImport(Network)
  import Network
#endif
#if canImport(UIKit)
  import UIKit
#elseif canImport(AppKit)
  import AppKit
#endif

/// The app's deliberately small Supabase REST client and account-scoped cache.
@MainActor @Observable public final class AppStore {
  public private(set) var trips: [Trip] = []
  public private(set) var expenses: [Expense] = []
  public private(set) var members: [Member] = []
  public private(set) var profiles: [Profile] = []
  public private(set) var loading = false
  public var notice: String?
  public private(set) var recovery = false
  public var pendingInvite: String {
    didSet { defaults.set(pendingInvite, forKey: Self.inviteKey) }
  }
  public private(set) var fx: FxSnapshot = .fallback
  public let configured: Bool
  public var userId: String? { session?.user.id }

  private let configuration: Configuration
  private let http: URLSession
  private let defaults: UserDefaults
  private let directory: URL
  private let readSession: () -> Session?
  private let writeSession: (Session) throws -> Void
  private let clearSession: () -> Void
  private var revision = 0
  private var session: Session?
  private var epoch = UUID()
  private var sessionRefreshTask: Task<Session, Error>?
  private var dataRefreshTask: Task<Void, Never>?
  private var dataRefreshID: UUID?
  private var outboxSyncTask: Task<Void, Never>?
  private var outboxSyncID: UUID?
  private var outbox: [String: Expense] = [:]
  private var monitor: AnyObject?
  #if canImport(AuthenticationServices)
    private var webSession: ASWebAuthenticationSession?
  #endif

  public convenience init(
    session: URLSession = .shared, bundle: Bundle = .main,
    defaults: UserDefaults = .standard
  ) {
    self.init(
      configuration: Configuration(bundle: bundle), http: session, defaults: defaults,
      directory: Self.cacheDirectory, readSession: Keychain.load,
      writeSession: Keychain.save, clearSession: Keychain.clear)
  }

  init(
    configuration: Configuration, http: URLSession, defaults: UserDefaults, directory: URL,
    readSession: @escaping () -> Session?, writeSession: @escaping (Session) throws -> Void,
    clearSession: @escaping () -> Void
  ) {
    self.configuration = configuration
    configured =
      configuration.url != nil
      && !configuration.key.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    self.http = http
    self.defaults = defaults
    self.directory = directory
    self.readSession = readSession
    self.writeSession = writeSession
    self.clearSession = clearSession
    pendingInvite = defaults.string(forKey: Self.inviteKey) ?? ""
    fx =
      (try? Self.decoder.decode(
        FxSnapshot.self, from: Data(contentsOf: directory.appendingPathComponent("fx.json"))))
      ?? .fallback
  }

  public func start() async {
    guard configured else {
      notice = "Missing Supabase configuration."
      return
    }
    session = readSession()
    if let id = userId { loadSnapshot(id) }
    startNetworkMonitor()
    await refresh()
  }

  public func signIn(email: String, password: String) async throws {
    let body = [
      "email": email.trimmingCharacters(in: .whitespacesAndNewlines), "password": password,
    ]
    try await acceptAuth(request: try authRequest("token?grant_type=password", body: body))
  }

  public func signUp(name: String, email: String, password: String) async throws {
    let body: [String: Any] = [
      "email": email.trimmingCharacters(in: .whitespacesAndNewlines),
      "password": password,
      "data": ["display_name": name.trimmingCharacters(in: .whitespacesAndNewlines)],
    ]
    let marker = epoch
    let response: AuthResponse = try await send(
      try authRequest(
        "signup", query: ["redirect_to": try callbackURL().absoluteString], body: body),
      authenticated: false)
    guard marker == epoch else { throw StoreError.accountChanged }
    if let newSession = response.sessionValue { try adopt(newSession) }
    notice = response.sessionValue == nil ? "Check your inbox to confirm your account." : nil
    if response.sessionValue != nil { await refresh() }
  }

  public func resetPassword(email: String) async throws {
    try await sendVoid(
      try authRequest(
        "recover", query: ["redirect_to": try callbackURL().absoluteString],
        body: ["email": email.trimmingCharacters(in: .whitespacesAndNewlines)]),
      authenticated: false)
    notice = "Password reset email sent."
  }

  public func resendConfirmation(email: String) async throws {
    try await sendVoid(
      try authRequest(
        "resend", query: ["redirect_to": try callbackURL().absoluteString],
        body: ["type": "signup", "email": email.trimmingCharacters(in: .whitespacesAndNewlines)]),
      authenticated: false)
    notice = "Confirmation email sent again."
  }

  public func updatePassword(_ password: String) async throws {
    var request = try authRequest("user", body: ["password": password])
    request.httpMethod = "PUT"
    try await sendVoid(request)
    recovery = false
    notice = "Password updated."
  }

  public func signOut() async throws {
    let old = userId
    var logout: URLRequest?
    if let token = session?.accessToken {
      logout = try? authRequest("logout", body: [:])
      logout?.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
    }
    epoch = UUID()
    sessionRefreshTask?.cancel()
    sessionRefreshTask = nil
    dataRefreshTask?.cancel()
    dataRefreshTask = nil
    dataRefreshID = nil
    outboxSyncTask?.cancel()
    outboxSyncTask = nil
    outboxSyncID = nil
    session = nil
    clearSession()
    trips = []
    expenses = []
    members = []
    profiles = []
    outbox = [:]
    pendingInvite = ""
    recovery = false
    if let old, FileManager.default.fileExists(atPath: snapshotURL(old).path) {
      do { try FileManager.default.removeItem(at: snapshotURL(old)) } catch {
        notice = "Signed out, but cached data could not be removed: \(message(error))"
      }
    }
    if let logout { try? await sendVoid(logout, authenticated: false) }
  }

  public func joinTrip(code: String) async throws -> String {
    let value: String = try await rest(
      "rpc/join_trip", method: "POST",
      body: ["p_code": code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()])
    await refresh()
    return value
  }

  public func saveTrip(_ trip: Trip, isNew: Bool) async throws -> String {
    guard let id = userId else { throw StoreError.notSignedIn }
    let payload = TripPayload(trip: trip, userId: id, includeID: isNew)
    let rows: [Trip] = try await rest(
      "trips" + (isNew ? "" : "?id=eq.\(trip.id)"), method: isNew ? "POST" : "PATCH", body: payload,
      prefer: "return=representation")
    guard let saved = rows.first else { throw StoreError.emptyResponse }
    let old = trips
    replace(saved, in: &trips)
    do { try persist() } catch {
      trips = old
      throw error
    }
    return saved.id
  }

  public func deleteTrip(_ trip: Trip) async throws {
    try await restVoid("trips?id=eq.\(trip.id)", method: "DELETE")
    let old = (trips, expenses, members)
    trips.removeAll { $0.id == trip.id }
    expenses.removeAll { $0.tripId == trip.id }
    members.removeAll { $0.tripId == trip.id }
    do { try persist() } catch {
      (trips, expenses, members) = old
      throw error
    }
  }

  public func saveExpense(_ expense: Expense, isNew: Bool) async throws {
    guard let id = userId else { throw StoreError.notSignedIn }
    if !isNew {
      guard expense.userId == id, expense.syncState == .synced else { throw StoreError.notSignedIn }
      let rows: [Expense] = try await rest(
        "expenses?id=eq.\(expense.id)", method: "PATCH",
        body: ExpensePayload(expense, userId: id, includeID: false), prefer: "return=representation"
      )
      guard var saved = rows.first else { throw StoreError.emptyResponse }
      saved.syncState = .synced
      saved.syncError = nil
      let old = expenses
      replace(saved, in: &expenses)
      do { try persist() } catch {
        expenses = old
        throw error
      }
      return
    }
    var pending = expense
    pending.userId = id
    pending.syncState = .pending
    pending.syncError = nil
    let oldExpenses = expenses
    let oldOutbox = outbox
    outbox[pending.id] = pending
    replace(pending, in: &expenses)
    do { try persist() } catch {
      expenses = oldExpenses
      outbox = oldOutbox
      throw error
    }  // durable before network
    await syncOutbox()
  }

  public func deleteExpense(_ expense: Expense) async throws {
    if expense.syncState == .pending { throw StoreError.pendingCannotBeDiscarded }
    let oldExpenses = expenses
    let oldOutbox = outbox
    if expense.syncState != .synced {
      outbox.removeValue(forKey: expense.id)
    } else {
      try await restVoid("expenses?id=eq.\(expense.id)", method: "DELETE")
    }
    expenses.removeAll { $0.id == expense.id }
    do { try persist() } catch {
      expenses = oldExpenses
      outbox = oldOutbox
      throw error
    }
  }

  public func removeMember(tripId: String, userId: String) async throws {
    try await restVoid("trip_members?trip_id=eq.\(tripId)&user_id=eq.\(userId)", method: "DELETE")
    let old = members
    members.removeAll { $0.tripId == tripId && $0.userId == userId }
    do { try persist() } catch {
      members = old
      throw error
    }
  }

  public func retryExpense(_ expense: Expense) async throws {
    guard self.userId == expense.userId else { throw StoreError.notSignedIn }
    var copy = expense
    copy.syncState = .pending
    copy.syncError = nil
    let oldExpenses = expenses
    let oldOutbox = outbox
    outbox[copy.id] = copy
    replace(copy, in: &expenses)
    do { try persist() } catch {
      expenses = oldExpenses
      outbox = oldOutbox
      throw error
    }
    await syncOutbox()
  }

  public func discardExpense(_ expense: Expense) async throws {
    guard expense.syncState == .failed else { throw StoreError.pendingCannotBeDiscarded }
    let oldExpenses = expenses
    let oldOutbox = outbox
    outbox.removeValue(forKey: expense.id)
    expenses.removeAll { $0.id == expense.id }
    do { try persist() } catch {
      expenses = oldExpenses
      outbox = oldOutbox
      throw error
    }
  }

  public func requestCover(tripId: String) async throws {
    guard let url = configuration.cover else { throw StoreError.notConfigured }
    var request = URLRequest(url: url)
    request.httpMethod = "POST"
    request.httpBody = try JSONSerialization.data(withJSONObject: ["tripId": tripId])
    request.setValue("application/json", forHTTPHeaderField: "Content-Type")
    try await sendVoid(request)
    if let index = trips.firstIndex(where: { $0.id == tripId }) {
      let old = trips
      trips[index].coverStatus = "pending"
      do { try persist() } catch {
        trips = old
        throw error
      }
    }
  }

  public func refresh() async {
    if let dataRefreshTask {
      await dataRefreshTask.value
      return
    }
    let id = UUID()
    let task = Task { @MainActor [weak self] in
      guard let self else { return }
      await self.performRefresh()
    }
    dataRefreshID = id
    dataRefreshTask = task
    await task.value
    if dataRefreshID == id {
      dataRefreshTask = nil
      dataRefreshID = nil
    }
  }

  private func performRefresh() async {
    guard let account = userId else { return }
    let marker = epoch
    loading = true
    defer { loading = false }
    await syncOutbox()
    guard marker == epoch else { return }
    let expectedRevision = revision
    do {
      async let t: [Trip] = restList("trips?order=created_at.desc,id")
      async let e: [Expense] = restList("expenses?order=spent_on.desc,created_at.desc,id")
      async let m: [Member] = restList("trip_members?order=joined_at.asc,trip_id,user_id")
      async let p: [Profile] = restList("profiles?order=id")
      let (remoteTrips, remoteExpenses, remoteMembers, remoteProfiles) = try await (t, e, m, p)
      guard marker == epoch, account == userId, revision == expectedRevision else { return }
      trips = remoteTrips
      members = remoteMembers
      profiles = remoteProfiles
      let localUnsynced = expenses.filter { $0.syncState != .synced }
      expenses = remoteExpenses
      for item in localUnsynced where !expenses.contains(where: { $0.id == item.id }) {
        replace(item, in: &expenses)
      }
      guard marker == epoch, account == userId else { return }
      do { try persist() } catch { notice = "Could not save refreshed data: \(message(error))" }
    } catch { if marker == epoch { notice = message(error) } }
  }

  public func refreshFX() async {
    guard fx.stale, let url = URL(string: "https://api.frankfurter.dev/v1/latest?base=EUR") else {
      return
    }
    do {
      let (data, response) = try await http.data(from: url)
      guard (response as? HTTPURLResponse)?.statusCode == 200 else { return }
      let result = try JSONDecoder().decode(FXResponse.self, from: data)
      fx = FxSnapshot(
        rates: ["EUR": 1].merging(result.rates) { _, new in new }, date: result.date,
        fetchedAt: Date())
      try? Self.atomicEncode(fx, to: directory.appendingPathComponent("fx.json"))
    } catch { /* bundled/cached rates remain usable */  }
  }

  public func handleURL(_ url: URL) async {
    guard let expected = try? callbackURL(), url.scheme == expected.scheme,
      url.host == expected.host
    else { return }
    let marker = epoch
    let values = Self.parameters(url)
    if let error = values["error_description"] ?? values["error"] {
      notice = error
      return
    }
    recovery = values["type"] == "recovery"
    do {
      if let code = values["code"] {
        let verifier = defaults.string(forKey: Self.verifierKey) ?? ""
        try await acceptAuth(
          request: try authRequest(
            "token?grant_type=pkce", body: ["auth_code": code, "code_verifier": verifier]))
      } else if let access = values["access_token"], let refresh = values["refresh_token"] {
        var userRequest = try authRequest("user", body: nil)
        userRequest.httpMethod = "GET"
        let user: User = try await send(
          userRequest, authenticated: false,
          bearerOverride: access)
        guard marker == epoch else { throw StoreError.accountChanged }
        try adopt(
          Session(
            accessToken: access, refreshToken: refresh,
            expiresAt: Date().addingTimeInterval(
              TimeInterval(Int(values["expires_in"] ?? "3600") ?? 3600)), user: user))
        await self.refresh()
      }
    } catch { notice = message(error) }
  }

  public func coverURL(_ path: String?) -> URL? {
    guard let path, let base = configuration.url else { return nil }
    return base.appendingPathComponent("storage/v1/object/public/trip-covers")
      .appendingPathComponent(path)
  }

  public func name(for userId: String) -> String {
    profiles.first { $0.id == userId }?.displayName?.trimmingCharacters(in: .whitespacesAndNewlines)
      .nilIfEmpty ?? "Traveller"
  }

  public func signInWithGoogle() async throws {
    #if canImport(AuthenticationServices)
      let verifier = Self.randomURLSafe(64)
      let challenge = Self.sha256URLSafe(verifier)
      defaults.set(verifier, forKey: Self.verifierKey)
      guard
        var parts = URLComponents(
          url: try authBaseURL().appendingPathComponent("authorize"), resolvingAgainstBaseURL: false
        )
      else { throw StoreError.notConfigured }
      parts.queryItems = [
        URLQueryItem(name: "provider", value: "google"),
        URLQueryItem(name: "redirect_to", value: try callbackURL().absoluteString),
        URLQueryItem(name: "code_challenge", value: challenge),
        URLQueryItem(name: "code_challenge_method", value: "s256"),
      ]
      guard let url = parts.url else { throw StoreError.notConfigured }
      let callback: URL = try await withCheckedThrowingContinuation { continuation in
        let web = ASWebAuthenticationSession(
          url: url, callbackURLScheme: (try? callbackURL().scheme) ?? "wayfare-ios"
        ) { [weak self] url, error in
          Task { @MainActor in
            self?.webSession = nil
            if let error {
              continuation.resume(throwing: error)
            } else if let url {
              continuation.resume(returning: url)
            } else {
              continuation.resume(throwing: StoreError.emptyResponse)
            }
          }
        }
        web.prefersEphemeralWebBrowserSession = false
        web.presentationContextProvider = OAuthPresentation.shared
        webSession = web  // retain through callback
        guard web.start() else {
          webSession = nil
          continuation.resume(throwing: StoreError.oauthStart)
          return
        }
      }
      await handleURL(callback)
      if userId == nil { throw StoreError.emptyResponse }
    #else
      throw StoreError.oauthUnavailable
    #endif
  }
}

// MARK: - REST, refresh and persistence

extension AppStore {
  static let inviteKey = "wayfare.pendingInvite", verifierKey = "wayfare.oauthVerifier"
  struct Configuration {
    let url: URL?, key: String, cover: URL?, web: URL?
    init(bundle: Bundle) {
      url = Self.httpsURL(bundle.object(forInfoDictionaryKey: "SUPABASE_URL") as? String)
      key = bundle.object(forInfoDictionaryKey: "SUPABASE_PUBLISHABLE_KEY") as? String ?? ""
      cover = Self.httpsURL(bundle.object(forInfoDictionaryKey: "COVER_ENDPOINT") as? String)
      web = Self.httpsURL(bundle.object(forInfoDictionaryKey: "WEB_URL") as? String)
    }
    init(url: URL, key: String) {
      self.url = url
      self.key = key
      cover = nil
      web = nil
    }
    static func httpsURL(_ value: String?) -> URL? {
      guard let value, !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
        let url = URL(string: value), url.scheme?.lowercased() == "https",
        url.host?.isEmpty == false
      else { return nil }
      return url
    }
  }

  func authBaseURL() throws -> URL {
    guard let url = configuration.url else { throw StoreError.notConfigured }
    return url.appendingPathComponent("auth/v1")
  }
  func callbackURL() throws -> URL {
    var parts = URLComponents()
    parts.scheme = "wayfare-ios"
    parts.host = "auth"
    guard let url = parts.url else { throw StoreError.notConfigured }
    return url
  }
  func authRequest(_ path: String, query: [String: String] = [:], body: [String: Any]?) throws
    -> URLRequest
  {
    let pieces = path.split(separator: "?", maxSplits: 1).map(String.init)
    var components = URLComponents(
      url: try authBaseURL().appendingPathComponent(pieces[0]), resolvingAgainstBaseURL: false)
    if pieces.count == 2 { components?.percentEncodedQuery = pieces[1] }
    var items = components?.queryItems ?? []
    items += query.map { URLQueryItem(name: $0.key, value: $0.value) }
    components?.queryItems = items
    guard let url = components?.url else { throw StoreError.notConfigured }
    var request = URLRequest(url: url)
    request.httpMethod = "POST"
    if let body { request.httpBody = try JSONSerialization.data(withJSONObject: body) }
    request.setValue(configuration.key, forHTTPHeaderField: "apikey")
    request.setValue("application/json", forHTTPHeaderField: "Content-Type")
    return request
  }

  func rest<T: Decodable, B: Encodable>(
    _ path: String, method: String = "GET", body: B? = Optional<Data>.none, prefer: String? = nil
  ) async throws -> T {
    guard let base = configuration.url,
      let url = URL(
        string: base.absoluteString.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
          + "/rest/v1/" + path)
    else { throw StoreError.notConfigured }
    var request = URLRequest(url: url)
    request.httpMethod = method
    if let body {
      request.httpBody = try Self.encoder.encode(body)
      request.setValue("application/json", forHTTPHeaderField: "Content-Type")
    }
    if let prefer { request.setValue(prefer, forHTTPHeaderField: "Prefer") }
    return try await send(request)
  }
  func rest<T: Decodable>(_ path: String) async throws -> T {
    try await rest(path, method: "GET", body: Optional<Data>.none)
  }
  func restVoid(_ path: String, method: String) async throws {
    let _: Empty = try await rest(path, method: method, body: Optional<Data>.none)
  }

  func restList<T: Decodable>(_ path: String) async throws -> [T] {
    var rows: [T] = []
    // Supabase caps responses; never silently truncate a traveller's ledger.
    while true {
      let page: [T] = try await rest(path + "&limit=500&offset=\(rows.count)")
      rows += page
      if page.count < 500 { return rows }
    }
  }

  func send<T: Decodable>(
    _ original: URLRequest, authenticated: Bool = true, bearerOverride: String? = nil
  ) async throws -> T {
    let marker = epoch
    var request = original
    request.setValue(configuration.key, forHTTPHeaderField: "apikey")
    if let bearerOverride {
      request.setValue("Bearer \(bearerOverride)", forHTTPHeaderField: "Authorization")
    } else if authenticated {
      request.setValue(
        "Bearer \(try await validSession().accessToken)", forHTTPHeaderField: "Authorization")
      guard marker == epoch else { throw StoreError.accountChanged }
    }
    let (data, response) = try await http.data(for: request)
    if authenticated { guard marker == epoch else { throw StoreError.accountChanged } }
    let status = (response as? HTTPURLResponse)?.statusCode ?? 0
    guard 200..<300 ~= status else {
      throw HTTPFailure(status: status, detail: Self.errorDetail(data))
    }
    if T.self == Empty.self { return Empty() as! T }
    return try Self.decoder.decode(T.self, from: data)
  }

  func sendVoid(_ request: URLRequest, authenticated: Bool = true) async throws {
    let _: Empty = try await send(request, authenticated: authenticated)
  }
  func acceptAuth(request: URLRequest) async throws {
    let marker = epoch
    let response: AuthResponse = try await send(request, authenticated: false)
    guard marker == epoch else { throw StoreError.accountChanged }
    guard let value = response.sessionValue else { throw StoreError.emptyResponse }
    try adopt(value)
    await refresh()
  }
  func validSession() async throws -> Session {
    guard let current = session else { throw StoreError.notSignedIn }
    if current.expiresAt.timeIntervalSinceNow > 60 { return current }
    if let sessionRefreshTask { return try await sessionRefreshTask.value }
    let expected = epoch
    let token = current.refreshToken
    let task = Task { @MainActor [weak self] () throws -> Session in
      guard let self else { throw StoreError.notSignedIn }
      let response: AuthResponse = try await self.send(
        try self.authRequest("token?grant_type=refresh_token", body: ["refresh_token": token]),
        authenticated: false)
      guard expected == self.epoch, let next = response.sessionValue else {
        throw StoreError.accountChanged
      }
      try self.adopt(next)
      return next
    }
    sessionRefreshTask = task
    defer { sessionRefreshTask = nil }
    return try await task.value
  }
  func adopt(_ value: Session) throws {
    try writeSession(value)
    if userId != value.user.id {
      epoch = UUID()
      trips = []
      expenses = []
      members = []
      profiles = []
      outbox = [:]
      loadSnapshot(value.user.id)
    }
    session = value
  }

  func syncOutbox() async {
    if let outboxSyncTask {
      await outboxSyncTask.value
      return
    }
    let id = UUID()
    let task = Task { @MainActor [weak self] in
      guard let self else { return }
      await self.performSyncOutbox()
    }
    outboxSyncID = id
    outboxSyncTask = task
    await task.value
    if outboxSyncID == id {
      outboxSyncTask = nil
      outboxSyncID = nil
    }
  }

  func performSyncOutbox() async {
    guard let account = userId else { return }
    let marker = epoch
    for item in outbox.values.filter({ $0.syncState == .pending }).sorted(by: {
      $0.createdAt < $1.createdAt
    }) {
      guard marker == epoch, account == userId else { return }
      do {
        let existing: [Expense] = try await rest("expenses?id=eq.\(item.id)&limit=1")
        guard marker == epoch, account == userId else { return }
        let remote: Expense
        if let found = existing.first {
          remote = found
        } else {
          do {
            let inserted: [Expense] = try await rest(
              "expenses", method: "POST",
              body: ExpensePayload(item, userId: account, includeID: true),
              prefer: "return=representation")
            guard let first = inserted.first else { throw StoreError.emptyResponse }
            remote = first
          } catch let error as HTTPFailure where error.status == 409 {
            let reconciled: [Expense] = try await rest("expenses?id=eq.\(item.id)&limit=1")
            guard let first = reconciled.first else { throw error }
            remote = first
          }
        }
        guard marker == epoch, account == userId else { return }
        var synced = remote
        synced.syncState = .synced
        synced.syncError = nil
        outbox.removeValue(forKey: item.id)
        replace(synced, in: &expenses)
        do { try persist() } catch {
          notice = "Expense synced, but its local cache could not be saved: \(message(error))"
        }
      } catch {
        guard marker == epoch, account == userId else { return }
        var copy = item
        copy.syncError = message(error)
        if let failure = error as? HTTPFailure, [400, 403, 404].contains(failure.status) {
          copy.syncState = .failed
          outbox.removeValue(forKey: item.id)
        } else {
          copy.syncState = .pending
        }  // includes auth: retain for a later login/refresh
        if copy.syncState == .failed {
          notice = copy.syncError
        } else if let failure = error as? HTTPFailure, failure.status < 500
        { /* auth/non-retry now */
        }
        replace(copy, in: &expenses)
        if outbox[item.id] != nil { outbox[item.id] = copy }
        do { try persist() } catch {
          notice = "Could not save expense recovery data: \(message(error))"
        }
      }
    }
  }

  func persist() throws {
    guard let id = userId else { return }
    revision += 1
    try Self.atomicEncode(
      Snapshot(
        trips: trips, expenses: expenses, members: members, profiles: profiles,
        outbox: Array(outbox.values)), to: snapshotURL(id))
  }
  func loadSnapshot(_ id: String) {
    guard let data = try? Data(contentsOf: snapshotURL(id)),
      let value = try? Self.decoder.decode(Snapshot.self, from: data)
    else { return }
    trips = value.trips
    expenses = value.expenses
    members = value.members
    profiles = value.profiles
    outbox = Dictionary(uniqueKeysWithValues: value.outbox.map { ($0.id, $0) })
  }
  func snapshotURL(_ id: String) -> URL { directory.appendingPathComponent("account-\(id).json") }
  static var cacheDirectory: URL {
    let root = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
      .appendingPathComponent("Wayfare", isDirectory: true)
    try? FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
    return root
  }
  static func atomicEncode<T: Encodable>(_ value: T, to url: URL) throws {
    try FileManager.default.createDirectory(
      at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
    #if os(iOS)
      try encoder.encode(value).write(
        to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    #else
      try encoder.encode(value).write(to: url, options: .atomic)
    #endif
    var values = URLResourceValues()
    values.isExcludedFromBackup = true
    var mutable = url
    try mutable.setResourceValues(values)
  }
  static let encoder: JSONEncoder = {
    let x = JSONEncoder()
    x.keyEncodingStrategy = .convertToSnakeCase
    x.dateEncodingStrategy = .iso8601
    return x
  }()
  static let decoder: JSONDecoder = {
    let x = JSONDecoder()
    x.keyDecodingStrategy = .convertFromSnakeCase
    x.dateDecodingStrategy = .iso8601
    return x
  }()
  static func errorDetail(_ data: Data) -> String {
    let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
    return object?["error_description"] as? String ?? object?["message"] as? String ?? String(
      data: data, encoding: .utf8) ?? "Request failed"
  }
  func message(_ error: Error) -> String {
    (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
  }
  func replace<T: Identifiable>(_ value: T, in values: inout [T]) where T.ID: Equatable {
    if let i = values.firstIndex(where: { $0.id == value.id }) {
      values[i] = value
    } else {
      values.append(value)
    }
  }

  func startNetworkMonitor() {
    #if canImport(Network)
      let value = NWPathMonitor()
      monitor = value
      value.pathUpdateHandler = { [weak self] path in
        guard path.status == .satisfied else { return }
        Task { @MainActor in await self?.refresh() }
      }
      value.start(queue: DispatchQueue(label: "wayfare.network"))
    #endif
  }
}

private struct Snapshot: Codable {
  let trips: [Trip]
  let expenses: [Expense]
  let members: [Member]
  let profiles: [Profile]
  let outbox: [Expense]
}
private struct FXResponse: Decodable {
  let date: String
  let rates: [String: Decimal]
}
struct User: Codable { let id: String }
struct Session: Codable {
  let accessToken: String
  let refreshToken: String
  let expiresAt: Date
  let user: User
}
private struct AuthResponse: Decodable {
  let accessToken: String?
  let refreshToken: String?
  let expiresIn: Double?
  let user: User?
  var sessionValue: Session? {
    guard let accessToken, let refreshToken, let user else { return nil }
    return Session(
      accessToken: accessToken, refreshToken: refreshToken,
      expiresAt: Date().addingTimeInterval(expiresIn ?? 3600), user: user)
  }
}
private struct Empty: Codable {}
private struct HTTPFailure: LocalizedError {
  let status: Int
  let detail: String
  var errorDescription: String? { detail }
}
private enum StoreError: LocalizedError {
  case notConfigured, notSignedIn, emptyResponse, accountChanged, oauthStart, oauthUnavailable,
    pendingCannotBeDiscarded
  var errorDescription: String? {
    switch self {
    case .notConfigured: "Wayfare is not configured."
    case .notSignedIn: "Not signed in."
    case .emptyResponse: "The server returned no data."
    case .accountChanged: "The account changed during the request."
    case .oauthStart: "Could not open Google sign-in."
    case .oauthUnavailable: "Google sign-in is unavailable."
    case .pendingCannotBeDiscarded:
      "A pending expense may already have reached the server. Retry it before discarding."
    }
  }
}

/// Explicit wire payloads prevent local sync fields (and server-owned fields) leaking into PostgREST.
private struct TripPayload: Encodable {
  let id: String?
  let userId: String
  let name: String
  let destination, startDate, endDate: String?
  let budget: Decimal
  let currency, accent: String
  init(trip: Trip, userId: String, includeID: Bool) {
    id = includeID ? trip.id : nil
    self.userId = userId
    name = trip.name
    destination = trip.destination
    startDate = trip.startDate
    endDate = trip.endDate
    budget = trip.budget
    currency = trip.currency
    accent = trip.accent
  }
  enum CodingKeys: String, CodingKey {
    case id, userId, name, destination, startDate, endDate, budget, currency, accent
  }
  func encode(to encoder: Encoder) throws {
    var c = encoder.container(keyedBy: CodingKeys.self)
    if let id {
      try c.encode(id, forKey: .id)
      try c.encode(userId, forKey: .userId)
    }
    try c.encode(name, forKey: .name)
    try c.encode(destination, forKey: .destination)
    try c.encode(startDate, forKey: .startDate)
    try c.encode(endDate, forKey: .endDate)
    try c.encode(budget, forKey: .budget)
    try c.encode(currency, forKey: .currency)
    try c.encode(accent, forKey: .accent)
  }
}
private struct ExpensePayload: Encodable {
  let id: String?
  let tripId, userId, title: String
  let amount: Decimal
  let originalAmount: Decimal?
  let originalCurrency: String?
  let fxRate: Decimal?
  let category: String
  let spentOn: String
  let note: String?
  init(_ x: Expense, userId: String, includeID: Bool) {
    id = includeID ? x.id : nil
    tripId = x.tripId
    self.userId = userId
    title = x.title
    amount = x.amount
    originalAmount = x.originalAmount
    originalCurrency = x.originalCurrency
    fxRate = x.fxRate
    category = x.category.rawValue
    spentOn = x.spentOn
    note = x.note
  }
  enum CodingKeys: String, CodingKey {
    case id, tripId, userId, title, amount, originalAmount, originalCurrency, fxRate, category,
      spentOn, note
  }
  func encode(to encoder: Encoder) throws {
    var c = encoder.container(keyedBy: CodingKeys.self)
    if let id {
      try c.encode(id, forKey: .id)
      try c.encode(tripId, forKey: .tripId)
      try c.encode(userId, forKey: .userId)
    }
    try c.encode(title, forKey: .title)
    try c.encode(amount, forKey: .amount)
    try c.encode(originalAmount, forKey: .originalAmount)
    try c.encode(originalCurrency, forKey: .originalCurrency)
    try c.encode(fxRate, forKey: .fxRate)
    try c.encode(category, forKey: .category)
    try c.encode(spentOn, forKey: .spentOn)
    try c.encode(note, forKey: .note)
  }
}

private enum Keychain {
  static let service = "app.wayfare.session", account = "current"
  static func save(_ session: Session) throws {
    let data = try JSONEncoder().encode(session)
    let identity: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
      kSecAttrAccount as String: account,
    ]
    let status = SecItemUpdate(
      identity as CFDictionary, [kSecValueData as String: data] as CFDictionary)
    if status == errSecSuccess { return }
    guard status == errSecItemNotFound else { throw StoreError.notConfigured }
    let query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
      kSecAttrAccount as String: account,
      kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
      kSecValueData as String: data,
    ]
    guard SecItemAdd(query as CFDictionary, nil) == errSecSuccess else {
      throw StoreError.notConfigured
    }
  }
  static func load() -> Session? {
    let query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
      kSecAttrAccount as String: account, kSecReturnData as String: true,
      kSecMatchLimit as String: kSecMatchLimitOne,
    ]
    var result: CFTypeRef?
    guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
      let data = result as? Data
    else { return nil }
    return try? JSONDecoder().decode(Session.self, from: data)
  }
  static func clear() {
    SecItemDelete(
      [
        kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
        kSecAttrAccount as String: account,
      ] as CFDictionary)
  }
}

extension String { fileprivate var nilIfEmpty: String? { isEmpty ? nil : self } }
extension AppStore {
  fileprivate static func parameters(_ url: URL) -> [String: String] {
    var components = URLComponents(url: url, resolvingAgainstBaseURL: false)
    var result: [String: String] = [:]
    for item in components?.queryItems ?? [] {
      if let value = item.value { result[item.name] = value }
    }
    components = URLComponents(string: "?" + (url.fragment ?? ""))
    for item in components?.queryItems ?? [] { result[item.name] = item.value }
    return result
  }
  fileprivate static func randomURLSafe(_ count: Int) -> String {
    var bytes = [UInt8](repeating: 0, count: count)
    _ = SecRandomCopyBytes(kSecRandomDefault, count, &bytes)
    return Data(bytes).base64URLEncoded
  }
  fileprivate static func sha256URLSafe(_ string: String) -> String {
    Data(SHA256.hash(data: Data(string.utf8))).base64URLEncoded
  }
}
extension Data {
  fileprivate var base64URLEncoded: String {
    base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(
      of: "/", with: "_"
    ).replacingOccurrences(of: "=", with: "")
  }
}

#if canImport(AuthenticationServices)
  private final class OAuthPresentation: NSObject, ASWebAuthenticationPresentationContextProviding {
    static let shared = OAuthPresentation()
    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
      #if canImport(UIKit)
        return UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
          .first(where: { $0.activationState == .foregroundActive })?.windows.first(
            where: \.isKeyWindow) ?? UIWindow()
      #elseif canImport(AppKit)
        return NSApplication.shared.keyWindow ?? NSApplication.shared.windows.first ?? NSWindow()
      #endif
    }
  }
#endif
