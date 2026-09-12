import SwiftUI
import WayfareCore

struct TripDetailScreen: View {
  @Environment(AppStore.self) private var store
  @Environment(\.dismiss) private var dismiss
  let tripId: String
  @State private var query = ""
  @State private var category = ""
  @State private var payer = ""
  @State private var editing: Expense?
  @State private var editTrip = false
  @State private var sharing = false
  @State private var deleting = false
  @State private var busy = false
  private var trip: Trip? { store.trips.first { $0.id == tripId } }
  private var entries: [Expense] { store.expenses.filter { $0.tripId == tripId } }
  private var filtered: [Expense] {
    entries.filter {
      (category.isEmpty || $0.category.rawValue == category)
        && (payer.isEmpty || $0.userId == payer)
        && (query.isEmpty
          || ($0.title + " " + ($0.note ?? "")).localizedCaseInsensitiveContains(query))
    }.sorted { $0.spentOn == $1.spentOn ? $0.createdAt > $1.createdAt : $0.spentOn > $1.spentOn }
  }

  var body: some View {
    Group {
      if let trip {
        ScrollView {
          LazyVStack(alignment: .leading, spacing: 20) {
            if let notice = store.notice { Notice(text: notice) }
            TripArtwork(trip: trip, url: store.coverURL(trip.coverPath)).clipShape(
              RoundedRectangle(cornerRadius: 22))
            Text(phaseLabel(trip).uppercased()).font(.caption.bold()).foregroundStyle(Palette.clay)
            Text(trip.name).font(.largeTitle.bold())
            Text(
              [trip.destination, trip.startDate, trip.endDate].compactMap { $0 }.joined(
                separator: " · ")
            )
            .font(.subheadline).foregroundStyle(Palette.soft)
            budget(trip)
            breakdown(trip)
            Text("Ledger").font(.title2.bold())
            TextField("Search titles and notes", text: $query).padding(14)
              .background(Palette.card, in: Capsule()).accessibilityLabel("Search expenses")
            filters
            if filtered.isEmpty {
              ContentUnavailableView(
                "No expenses", systemImage: "wallet.pass",
                description: Text(
                  entries.isEmpty
                    ? "Add the first cost when it lands." : "No entries match these filters."))
            }
            ForEach(
              Array(Dictionary(grouping: filtered, by: \.spentOn).keys.sorted().reversed()),
              id: \.self
            ) { day in
              Text(day).font(.caption.bold()).foregroundStyle(Palette.soft).padding(.top, 10)
              ForEach(filtered.filter { $0.spentOn == day }) { entry in
                Button {
                  editing = entry
                } label: {
                  expenseRow(entry, currency: trip.currency)
                }.buttonStyle(.plain)
                Divider()
              }
            }
          }.padding(20).frame(maxWidth: 700).frame(maxWidth: .infinity)
        }.refreshable { await store.refresh() }
          .safeAreaInset(edge: .bottom) {
            PrimaryButton(title: "Add expense") {
              editing = Expense(tripId: tripId, userId: store.userId ?? "", category: .food)
            }
            .padding(.horizontal, 20).padding(.vertical, 10).background(Palette.paper)
          }
          .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
              Menu {
                Button("Travellers & sharing", systemImage: "person.2") { sharing = true }
                if trip.userId == store.userId {
                  Button("Edit trip", systemImage: "pencil") { editTrip = true }
                  Button("Generate destination cover", systemImage: "photo") {
                    perform { try await store.requestCover(tripId: tripId) }
                  }
                  Button("Delete trip", systemImage: "trash", role: .destructive) {
                    deleting = true
                  }
                }
              } label: {
                Image(systemName: "ellipsis.circle").accessibilityLabel("Trip options")
              }.disabled(busy)
            }
          }
          .sheet(item: $editing) { expense in
            ExpenseForm(
              trip: trip, expense: expense, isNew: !entries.contains { $0.id == expense.id })
          }
          .sheet(isPresented: $editTrip) { TripForm(trip: trip, isNew: false) { _ in } }
          .sheet(isPresented: $sharing) { SharingScreen(trip: trip) }
          .confirmationDialog(
            "Delete \(trip.name)?", isPresented: $deleting, titleVisibility: .visible
          ) {
            Button("Delete trip and all expenses", role: .destructive) {
              perform {
                try await store.deleteTrip(trip)
                dismiss()
              }
            }
          } message: {
            Text("This removes the trip for every traveller and cannot be undone.")
          }
      } else {
        ContentUnavailableView(
          "Trip unavailable", systemImage: "ticket",
          description: Text("It may have been deleted, or you may no longer be a member."))
      }
    }.paperScreen().navigationTitle(trip?.name ?? "Trip").navigationBarTitleDisplayMode(.inline)
  }

  private var filters: some View {
    HStack {
      Picker("Category", selection: $category) {
        Text("All categories").tag("")
        ForEach(WayfareCore.Category.allCases, id: \.self) { Text($0.label).tag($0.rawValue) }
      }
      Picker("Traveller", selection: $payer) {
        Text("All travellers").tag("")
        ForEach(store.members.filter { $0.tripId == tripId }) {
          Text(store.name(for: $0.userId)).tag($0.userId)
        }
      }
    }.pickerStyle(.menu)
  }

  private func budget(_ trip: Trip) -> some View {
    let summary = budgetSummary(trip, expenses: entries)
    let showRemaining: Bool
    if case .active = tripPhase(trip) {
      showRemaining = trip.budget > 0
    } else {
      showRemaining = false
    }
    return VStack(alignment: .leading, spacing: 14) {
      Text(
        showRemaining ? (summary.remaining < 0 ? "Over budget" : "Budget remaining") : "Total spent"
      ).foregroundStyle(Palette.soft)
      Text(money(showRemaining ? abs(summary.remaining) : summary.spent, trip.currency))
        .font(.largeTitle.bold()).monospacedDigit()
      if trip.budget > 0 {
        Text(
          "\(money(summary.remaining, trip.currency)) remaining of \(money(trip.budget, trip.currency))"
        ).font(.subheadline)
        ProgressView(
          value: min(1, max(0, NSDecimalNumber(decimal: summary.spent / trip.budget).doubleValue)))
      }
      if let available = summary.availablePerDay {
        Text("\(money(available, trip.currency)) available/day remaining").font(.headline)
        Text("Across \(summary.daysLeft ?? 0) days, including today.").font(.caption)
          .foregroundStyle(Palette.soft)
      }
      Divider()
      HStack {
        Text("Daily average").foregroundStyle(Palette.soft)
        Spacer()
        Text(summary.perDay.map { money($0, trip.currency) } ?? "—").monospacedDigit()
      }.font(.subheadline)
      if entries.contains(where: { $0.syncState == .pending }) {
        Text("Includes entries waiting to sync.").font(.caption).foregroundStyle(Palette.clay)
      }
      Text("Group spending, not who owes whom.").font(.caption).foregroundStyle(Palette.soft)
    }.padding(20).background(Palette.card, in: RoundedRectangle(cornerRadius: 22))
  }

  private func breakdown(_ trip: Trip) -> some View {
    VStack(alignment: .leading, spacing: 12) {
      Text("Spending by category").font(.headline)
      ForEach(WayfareCore.Category.allCases, id: \.self) { group in
        let amount = entries.filter { $0.category == group && $0.syncState != .failed }.reduce(
          Decimal.zero
        ) { $0 + $1.amount }
        if amount > 0 {
          Button {
            category = group.rawValue
          } label: {
            HStack {
              Label(group.label, systemImage: group.symbol)
              Spacer()
              Text(money(amount, trip.currency)).monospacedDigit()
            }
          }.foregroundStyle(Palette.ink)
        }
      }
    }.padding(.vertical, 8)
  }

  private func expenseRow(_ expense: Expense, currency: String) -> some View {
    HStack(spacing: 12) {
      Image(systemName: expense.category.symbol).frame(width: 44, height: 44)
        .foregroundStyle(Palette.clay).background(
          Palette.clay.opacity(0.08), in: RoundedRectangle(cornerRadius: 14))
      VStack(alignment: .leading, spacing: 4) {
        Text(expense.title).fontWeight(.semibold)
        Text(store.name(for: expense.userId)).font(.caption).foregroundStyle(Palette.soft)
        if expense.syncState != .synced {
          Text(expense.syncState == .failed ? "Not saved · tap to resolve" : "Waiting to sync")
            .font(.caption).foregroundStyle(Palette.clay)
        }
      }
      Spacer()
      Text(money(expense.amount, currency)).fontWeight(.semibold).monospacedDigit()
    }.padding(.vertical, 6)
  }

  private func perform(_ action: @escaping () async throws -> Void) {
    busy = true
    Task {
      defer { busy = false }
      do { try await action() } catch { store.notice = error.localizedDescription }
    }
  }
}

struct SharingScreen: View {
  @Environment(AppStore.self) private var store
  @Environment(\.dismiss) private var dismiss
  let trip: Trip
  @State private var removing: Member?
  @State private var busy = false
  @State private var error: String?
  var body: some View {
    NavigationStack {
      List {
        if let code = trip.shareCode {
          Section("Invite code") {
            Text(code).font(.largeTitle.monospaced().bold()).textSelection(.enabled)
            ShareLink(
              item: "Join \(trip.name) on Wayfare: \(webURL)/join/\(code)\nInvite code: \(code)"
            ) {
              Label("Share invite", systemImage: "square.and.arrow.up")
            }
          }
        }
        Section("On this trip") {
          ForEach(store.members.filter { $0.tripId == trip.id }) { member in
            HStack {
              VStack(alignment: .leading) {
                Text(store.name(for: member.userId))
                Text(member.role == "owner" ? "Organiser" : "Member").font(.caption)
                  .foregroundStyle(Palette.soft)
              }
              Spacer()
              if member.role != "owner",
                trip.userId == store.userId || member.userId == store.userId
              {
                Button(member.userId == store.userId ? "Leave" : "Remove", role: .destructive) {
                  removing = member
                }
              }
            }
          }
        }
        if let error { Notice(text: error) }
      }.paperScreen().disabled(busy).navigationTitle("Travel companions")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        .confirmationDialog(
          "Remove this traveller?",
          isPresented: Binding(get: { removing != nil }, set: { if !$0 { removing = nil } }),
          titleVisibility: .visible
        ) {
          if let member = removing {
            Button(
              member.userId == store.userId ? "Leave trip" : "Remove traveller", role: .destructive
            ) {
              busy = true
              Task {
                defer { busy = false }
                do {
                  try await store.removeMember(tripId: trip.id, userId: member.userId)
                  await store.refresh()
                  if member.userId == store.userId { dismiss() }
                } catch { self.error = error.localizedDescription }
              }
            }
          }
        } message: {
          Text("They will lose access to this shared ledger.")
        }
    }
  }
  private var webURL: String {
    Bundle.main.object(forInfoDictionaryKey: "WEB_URL") as? String
      ?? "https://getwayfare.netlify.app"
  }
}
