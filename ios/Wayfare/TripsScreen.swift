import SwiftUI
import WayfareCore

struct TripsScreen: View {
  @Environment(AppStore.self) private var store
  @State private var path: [String] = []
  @State private var newTrip = false
  @State private var joining = false
  @State private var signout = false
  @State private var recovery = false

  private var sections: [(String, [Trip])] {
    ["Active trips", "Upcoming trips", "Dates open", "Past trips"].map { title in
      (
        title,
        store.trips.filter { trip in
          switch tripPhase(trip) {
          case .active: title == "Active trips"
          case .upcoming: title == "Upcoming trips"
          case .undated: title == "Dates open"
          case .past: title == "Past trips"
          }
        }
      )
    }
  }

  var body: some View {
    NavigationStack(path: $path) {
      ScrollView {
        LazyVStack(alignment: .leading, spacing: 22) {
          Text("Your next chapter.").font(.largeTitle.bold())
          Text(
            store.trips.isEmpty
              ? "Your first journey is waiting."
              : "\(store.trips.count) trips. All your plans, in one place."
          )
          .foregroundStyle(Palette.soft)
          HStack(spacing: 20) {
            PrimaryButton(title: "New trip") { newTrip = true }
            Button("Join a friend") { joining = true }.fontWeight(.semibold)
          }
          if let notice = store.notice { Notice(text: notice) }
          if store.expenses.contains(where: { $0.syncState != .synced }) {
            Button("Review unsynced expenses", systemImage: "arrow.triangle.2.circlepath") {
              recovery = true
            }
          }
          if store.loading && store.trips.isEmpty {
            ProgressView("Opening your ledger…").frame(maxWidth: .infinity).padding(40)
          } else if store.trips.isEmpty {
            ContentUnavailableView(
              "Good trips start here", systemImage: "globe.europe.africa",
              description: Text("Add a trip, set a budget, and log each cost as it lands."))
          }
          ForEach(sections, id: \.0) { title, trips in
            if !trips.isEmpty {
              Text(title).font(.title2.bold()).padding(.top, 6)
              ForEach(trips) { trip in
                NavigationLink(value: trip.id) { ticket(trip) }.buttonStyle(.plain)
              }
            }
          }
        }.padding(20).frame(maxWidth: 700).frame(maxWidth: .infinity)
      }
      .paperScreen().refreshable { await store.refresh() }
      .navigationTitle("wayfare").navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .topBarTrailing) {
          Menu {
            Button("Join a trip", systemImage: "ticket") { joining = true }
            Button("Refresh", systemImage: "arrow.clockwise") { Task { await store.refresh() } }
            Button(
              "Sign out", systemImage: "rectangle.portrait.and.arrow.right", role: .destructive
            ) { signout = true }
          } label: {
            Image(systemName: "person.crop.circle").accessibilityLabel("Account")
          }
        }
      }
      .navigationDestination(for: String.self) { TripDetailScreen(tripId: $0) }
      .sheet(isPresented: $newTrip) { TripForm(trip: Trip(), isNew: true) { path.append($0) } }
      .sheet(isPresented: $joining) { JoinForm { path.append($0) } }
      .sheet(isPresented: $recovery) { UnsyncedScreen() }
      .onChange(of: store.pendingInvite, initial: true) { _, code in
        if !code.isEmpty { joining = true }
      }
      .confirmationDialog("Sign out?", isPresented: $signout, titleVisibility: .visible) {
        Button("Sign out and clear this device", role: .destructive) {
          Task { try? await store.signOut() }
        }
      } message: {
        Text(
          "Cached trips and any unsynced expenses will be removed from this device. Sync pending entries first to keep them."
        )
      }
    }
  }

  private func ticket(_ trip: Trip) -> some View {
    let expenses = store.expenses.filter { $0.tripId == trip.id }
    let summary = budgetSummary(trip, expenses: expenses)
    return VStack(alignment: .leading, spacing: 0) {
      TripArtwork(trip: trip, url: store.coverURL(trip.coverPath))
      VStack(alignment: .leading, spacing: 10) {
        Text(phaseLabel(trip).uppercased()).font(.caption.weight(.bold)).foregroundStyle(
          Palette.clay)
        Text(trip.name).font(.title2.bold())
        Text(trip.destination ?? "Destination open").foregroundStyle(Palette.soft)
        Divider().overlay(Palette.line)
        HStack {
          VStack(alignment: .leading) {
            Text("SPENT").font(.caption2).foregroundStyle(Palette.soft)
            Text(money(summary.spent, trip.currency)).font(.headline).monospacedDigit()
          }
          Spacer()
          Text(trip.budget > 0 ? "of \(money(trip.budget, trip.currency))" : "No budget limit")
            .font(.subheadline).foregroundStyle(Palette.soft)
          Image(systemName: "arrow.up.right")
        }
      }.padding(20)
    }
    .background(Palette.card, in: RoundedRectangle(cornerRadius: 22))
    .clipShape(RoundedRectangle(cornerRadius: 22))
    .overlay(RoundedRectangle(cornerRadius: 22).stroke(Palette.line, lineWidth: 1))
  }
}

struct JoinForm: View {
  @Environment(AppStore.self) private var store
  @Environment(\.dismiss) private var dismiss
  @State private var code = ""
  @State private var busy = false
  @State private var error: String?
  let onJoin: (String) -> Void
  var body: some View {
    NavigationStack {
      Form {
        Section("Your invitation") {
          Text("Enter the eight-character code your travel companion sent you.")
          TextField("Invite code", text: $code).textInputAutocapitalization(.characters)
            .autocorrectionDisabled()
        }
        if let error { Notice(text: error) }
        PrimaryButton(title: "Join trip", busy: busy) {
          let clean = code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
          guard clean.count == 8, clean.allSatisfy({ $0.isASCII && ($0.isLetter || $0.isNumber) })
          else {
            error = "Enter all eight letters and numbers."
            return
          }
          busy = true
          Task {
            defer { busy = false }
            do {
              let id = try await store.joinTrip(code: clean)
              store.pendingInvite = ""
              dismiss()
              onJoin(id)
            } catch { self.error = error.localizedDescription }
          }
        }
      }.paperScreen().navigationTitle("Join a trip").navigationBarTitleDisplayMode(.inline)
        .toolbar {
          ToolbarItem(placement: .cancellationAction) {
            Button("Cancel") {
              store.pendingInvite = ""
              dismiss()
            }.disabled(busy)
          }
        }
    }.onAppear { code = store.pendingInvite }.interactiveDismissDisabled(busy)
  }
}

// Kept outside trip navigation: removed membership must not hide a failed entry.
struct UnsyncedScreen: View {
  @Environment(AppStore.self) private var store
  @Environment(\.dismiss) private var dismiss
  @State private var deleting: Expense?
  @State private var busy = false
  @State private var error: String?
  var body: some View {
    NavigationStack {
      List {
        if let error { Notice(text: error) }
        ForEach(store.expenses.filter { $0.syncState != .synced }) { expense in
          VStack(alignment: .leading, spacing: 10) {
            Text(expense.title).font(.headline)
            Text("Ledger amount: \(money(expense.amount)) · \(expense.spentOn)").font(.subheadline)
            Text(
              expense.syncState == .failed
                ? "Not saved. Excluded from totals." : "Saved on this device. Waiting to sync.")
            if let reason = expense.syncError {
              Text(reason).font(.caption).foregroundStyle(Palette.soft)
            }
            Button("Try again") { run { try await store.retryExpense(expense) } }
            if expense.syncState == .failed {
              Button("Discard", role: .destructive) { deleting = expense }
            }
          }.padding(.vertical, 8)
        }
      }.paperScreen().disabled(busy).navigationTitle("Unsynced expenses")
        .toolbar {
          ToolbarItem(placement: .confirmationAction) {
            Button("Done") { dismiss() }.disabled(busy)
          }
        }
        .confirmationDialog(
          "Discard this unsaved expense?",
          isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }),
          titleVisibility: .visible
        ) {
          if let expense = deleting {
            Button("Discard expense", role: .destructive) {
              run { try await store.discardExpense(expense) }
            }
          }
        }
    }.interactiveDismissDisabled(busy)
  }
  private func run(_ action: @escaping () async throws -> Void) {
    busy = true
    Task {
      defer { busy = false }
      do { try await action() } catch { self.error = error.localizedDescription }
    }
  }
}
