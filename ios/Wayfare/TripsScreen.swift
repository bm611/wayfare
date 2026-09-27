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
        // Cards read as distinct objects only if the canvas between them is
        // wide enough to be read as canvas.
        LazyVStack(alignment: .leading, spacing: 24) {
          topNav
          VStack(alignment: .leading, spacing: 16) {
            Text("Your trips").typeStyle(.displaySmall)
            if store.trips.isEmpty {
              Text("Your first journey is waiting.").typeStyle(.bodyLarge)
                .foregroundStyle(Palette.ash)
            } else {
              passportSummary
            }
          }.padding(.horizontal, 24)
          if let notice = store.notice { Notice(text: notice).padding(.horizontal, 24) }
          if store.expenses.contains(where: { $0.syncState != .synced }) {
            SecondaryButton(title: "Review unsynced expenses", pill: true) { recovery = true }
              .padding(.horizontal, 24)
          }
          if store.loading && store.trips.isEmpty {
            TripLoadingSkeleton().padding(.horizontal, 24)
          } else if store.trips.isEmpty {
            emptyState.padding(.horizontal, 24)
          }
          ForEach(sections, id: \.0) { title, trips in
            if !trips.isEmpty {
              HStack(spacing: 8) {
                Text(title).typeStyle(.headlineSmall)
                Text("\(trips.count)").typeStyle(.bodyLarge).foregroundStyle(Palette.ash)
              }.padding(.horizontal, 24).padding(.top, 8)
              // Trips still to come get a full card; finished ones are
              // collected, stamps on a passport page.
              if title == "Past trips" {
                stampPage(trips).padding(.horizontal, 24)
              } else {
                ForEach(trips) { trip in
                  NavigationLink(value: trip.id) { listingCard(trip) }
                    .buttonStyle(.plain).padding(.horizontal, 24)
                }
              }
            }
          }
          Spacer(minLength: 12)
        }.frame(maxWidth: 700).frame(maxWidth: .infinity)
      }
      .canvasScreen().refreshable { await store.refresh() }
      .toolbar(.hidden, for: .navigationBar)
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

  /// Top nav: the accent wordmark, then add and account as circular controls.
  private var topNav: some View {
    HStack(spacing: 8) {
      HStack(spacing: 8) {
        Image(systemName: "airplane.departure").font(.system(size: 20, weight: .medium))
        Text("wayfare").typeStyle(.headlineSmall)
      }.foregroundStyle(Palette.accentInk)
      Spacer(minLength: 0)
      Menu {
        Button("New trip", systemImage: "plus") { newTrip = true }
        Button("Join with a code", systemImage: "ticket") { joining = true }
      } label: {
        Image(systemName: "plus").font(.system(size: 16, weight: .semibold))
          .foregroundStyle(Palette.onAccent).frame(width: 44, height: 44)
          .background(Palette.accent, in: Circle())
      }.accessibilityLabel("Add a trip")
      Menu {
        Button("Refresh", systemImage: "arrow.clockwise") { Task { await store.refresh() } }
        Button("Sign out", systemImage: "rectangle.portrait.and.arrow.right", role: .destructive)
        { signout = true }
      } label: {
        Image(systemName: "person.crop.circle").font(.system(size: 16, weight: .medium))
          .foregroundStyle(Palette.ink).frame(width: 44, height: 44)
          .background(Palette.softCloud, in: Circle())
      }.accessibilityLabel("Account")
    }.padding(.horizontal, 24).padding(.top, 8)
  }

  /// The passport at a glance: stamps collected, places they name, and what
  /// they cost, in euros whatever each trip is budgeted in.
  private var passportSummary: some View {
    let trips = store.trips
    let places = Set(trips.map { stampLabel($0).lowercased() }).count
    let spent = trips.reduce(Decimal.zero) { total, trip in
      let spent = budgetSummary(trip, expenses: store.expensesByTrip[trip.id] ?? []).spent
      return total + (convert(amount: spent, from: trip.currency, to: "EUR", rates: store.fx.rates) ?? 0)
    }
    return HStack(alignment: .top, spacing: 18) {
      summaryStat("\(trips.count)", trips.count == 1 ? "STAMP" : "STAMPS")
      Rectangle().fill(Palette.hairline).frame(width: 1, height: 40)
      summaryStat("\(places)", places == 1 ? "PLACE" : "PLACES")
      Rectangle().fill(Palette.hairline).frame(width: 1, height: 40)
      summaryStat(moneyShort(spent), "SPENT")
    }
    .accessibilityElement(children: .ignore)
    .accessibilityLabel(
      "\(trips.count) \(trips.count == 1 ? "trip" : "trips"), \(places) \(places == 1 ? "place" : "places"), \(moneyShort(spent)) spent")
  }

  private func summaryStat(_ value: String, _ label: String) -> some View {
    VStack(alignment: .leading, spacing: 4) {
      Text(value).typeStyle(.headlineSmall).monospacedDigit().lineLimit(1)
      Text(label).font(.system(size: 10, design: .monospaced)).tracking(0.8)
        .foregroundStyle(Palette.ash)
    }
  }

  private var emptyState: some View {
    VStack(spacing: 0) {
      Image(systemName: "safari").font(.system(size: 28, weight: .light))
        .foregroundStyle(Palette.accentInk).frame(width: 64, height: 64)
        .background(Palette.canvas, in: Circle())
      Text("Good trips start here").typeStyle(.titleLarge).padding(.top, 18)
      Text("Add a trip, set a budget, and log each cost as it lands.")
        .typeStyle(.bodyMedium).foregroundStyle(Palette.ash).multilineTextAlignment(.center)
        .padding(.top, 6)
      PrimaryButton(title: "New trip", icon: "plus") { newTrip = true }.padding(.top, 20)
      Button("I have an invite code") { joining = true }
        .typeStyle(.labelMedium).foregroundStyle(Palette.ink).padding(.top, 12)
    }
    .frame(maxWidth: .infinity).padding(32)
    .background(Palette.softCloud, in: RoundedRectangle(cornerRadius: Radius.card))
  }

  /// A trip card led by its passport stamp. The stamp names the place, so the
  /// card names the trip, when it runs, and what it has cost so far.
  private func listingCard(_ trip: Trip) -> some View {
    let spent = budgetSummary(trip, expenses: store.expensesByTrip[trip.id] ?? []).spent
    let travellers = store.members.filter { $0.tripId == trip.id }.count
    // An undated trip's section and stamp already say so; don't say it a third time.
    let dated = trip.startDate != nil || trip.endDate != nil
    let meta = [
      dated ? dateRange(trip.startDate, trip.endDate) : nil,
      travellers > 1 ? "\(travellers) travellers" : nil,
    ].compactMap { $0 }.joined(separator: " · ")
    let shape = RoundedRectangle(cornerRadius: 28, style: .continuous)
    return HStack(spacing: 20) {
      Stamp(trip: trip)
      VStack(alignment: .leading, spacing: 0) {
        // Past and undated trips sit under a section title that already says
        // so; only a countdown earns the pill.
        switch tripPhase(trip) {
        case .active, .upcoming:
          Text(phaseLabel(trip)).typeStyle(.labelSmall).foregroundStyle(Palette.ink)
            .padding(.horizontal, 10).padding(.vertical, 6)
            .background(Palette.softCloud, in: RoundedRectangle(cornerRadius: Radius.card))
            .padding(.bottom, 12)
        case .past, .undated:
          EmptyView()
        }
        Text(trip.name).typeStyle(.titleLarge).lineLimit(1)
        if !meta.isEmpty {
          Text(meta).typeStyle(.bodyMedium).foregroundStyle(Palette.ash).lineLimit(1)
            .padding(.top, 4)
        }
        spendLine(trip, spent: spent).padding(.top, 14)
      }
      .frame(maxWidth: .infinity, alignment: .leading)
    }
    .padding(.leading, 16).padding(.trailing, 20).padding(.vertical, 20)
    .background(Palette.canvas, in: shape)
    // The shadow vanishes on the dark canvas; the hairline keeps the edge.
    .overlay(shape.stroke(Palette.hairline, lineWidth: 1))
    .shadow(color: .black.opacity(0.08), radius: 12, x: 0, y: 6)
    .foregroundStyle(Palette.ink)
    .accessibilityElement(children: .ignore)
    .accessibilityLabel("\(trip.name), \(stampLabel(trip)), \(phaseLabel(trip)), \(meta.isEmpty ? "" : "\(meta), ")\(spendText(trip, spent: spent))")
    .accessibilityHint("Opens trip details")
  }

  /// Spend against budget with a rail when there is both, otherwise whichever
  /// one number there is.
  @ViewBuilder private func spendLine(_ trip: Trip, spent: Decimal) -> some View {
    if trip.budget > 0 && spent > 0 {
      VStack(alignment: .leading, spacing: 8) {
        (Text(moneyShort(spent, trip.currency)).foregroundColor(Palette.ink)
          + Text(" of \(moneyShort(trip.budget, trip.currency))").foregroundColor(Palette.ash))
          .typeStyle(.labelMedium).monospacedDigit().lineLimit(1)
        BudgetMeter(spent: spent, budget: trip.budget)
      }
    } else {
      Text(spendText(trip, spent: spent)).typeStyle(.labelMedium).monospacedDigit()
        .foregroundStyle(spent > 0 ? Palette.ink : Palette.ash).lineLimit(1)
    }
  }

  private func spendText(_ trip: Trip, spent: Decimal) -> String {
    if spent > 0 {
      trip.budget > 0
        ? "\(moneyShort(spent, trip.currency)) of \(moneyShort(trip.budget, trip.currency))"
        : "\(moneyShort(spent, trip.currency)) spent"
    } else if trip.budget > 0 {
      "\(moneyShort(trip.budget, trip.currency)) budget"
    } else {
      "No expenses yet"
    }
  }

  /// Finished trips as a passport page: bare stamps, two across, each with the
  /// trip and its total underneath.
  private func stampPage(_ trips: [Trip]) -> some View {
    LazyVGrid(
      columns: [GridItem(.flexible(), spacing: 16), GridItem(.flexible(), spacing: 16)],
      spacing: 28
    ) {
      ForEach(trips) { trip in
        let spent = budgetSummary(trip, expenses: store.expensesByTrip[trip.id] ?? []).spent
        NavigationLink(value: trip.id) {
          VStack(spacing: 12) {
            Stamp(trip: trip)
            VStack(spacing: 2) {
              Text(trip.name).typeStyle(.titleMedium).lineLimit(1)
              Text(spent > 0 ? moneyShort(spent, trip.currency) : "No expenses")
                .typeStyle(.bodySmall).monospacedDigit().foregroundStyle(Palette.ash)
            }
          }
          .frame(maxWidth: .infinity).padding(.vertical, 16)
          .contentShape(Rectangle())
          .foregroundStyle(Palette.ink)
          .accessibilityElement(children: .ignore)
          .accessibilityLabel("\(trip.name), \(stampLabel(trip)), \(spendText(trip, spent: spent))")
          .accessibilityHint("Opens trip details")
        }.buttonStyle(.plain)
      }
    }
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
      ScrollView {
        VStack(alignment: .leading, spacing: 16) {
          Text("Enter the eight-character code your travel companion sent you.")
            .typeStyle(.bodyMedium).foregroundStyle(Palette.ash)
          WayfareTextField(label: "Invite code", text: $code, error: error)
            .textInputAutocapitalization(.characters).autocorrectionDisabled()
        }.padding(24).frame(maxWidth: 700).frame(maxWidth: .infinity)
      }.canvasScreen()
        .safeAreaInset(edge: .bottom, spacing: 0) {
          ActionBar { PrimaryButton(title: "Join trip", busy: busy, action: join) }
        }
        .navigationTitle("Join a trip").navigationBarTitleDisplayMode(.inline)
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

  private func join() {
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
        if let error { Notice(text: error, isError: true) }
        ForEach(store.expenses.filter { $0.syncState != .synced }) { expense in
          VStack(alignment: .leading, spacing: 10) {
            Text(expense.title).typeStyle(.titleMedium)
            Text("Ledger amount: \(money(expense.amount)) · \(expense.spentOn)")
              .typeStyle(.bodyMedium).foregroundStyle(Palette.ash)
            Text(
              expense.syncState == .failed
                ? "Not saved. Excluded from totals." : "Saved on this device. Waiting to sync."
            ).typeStyle(.bodyMedium)
            if let reason = expense.syncError {
              Text(reason).typeStyle(.bodySmall).foregroundStyle(Palette.ash)
            }
            Button("Try again") { run { try await store.retryExpense(expense) } }
              .typeStyle(.labelMedium).foregroundStyle(Palette.ink)
            if expense.syncState == .failed {
              Button("Discard", role: .destructive) { deleting = expense }
                .typeStyle(.labelMedium).foregroundStyle(Palette.errorRed)
            }
          }.padding(.vertical, 8)
        }
      }.canvasScreen().disabled(busy).navigationTitle("Unsynced expenses")
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
