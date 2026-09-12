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
          LazyVStack(alignment: .leading, spacing: 24) {
            if let notice = store.notice { Notice(text: notice).padding(.horizontal, 24) }
            hero(trip)
            bookingPanel(trip).padding(.horizontal, 24)
            breakdown(trip).padding(.horizontal, 24)
            VStack(alignment: .leading, spacing: 12) {
              Text("Ledger").typeStyle(.headlineSmall)
              searchField
              filters
            }.padding(.horizontal, 24)
            if filtered.isEmpty {
              ledgerEmptyState
            }
            ForEach(
              Array(Dictionary(grouping: filtered, by: \.spentOn).keys.sorted().reversed()),
              id: \.self
            ) { day in
              Text(shortDate(day) ?? day).typeStyle(.labelMedium).foregroundStyle(Palette.ash)
                .padding(.horizontal, 24).padding(.top, 4)
              ForEach(filtered.filter { $0.spentOn == day }) { entry in
                Button {
                  editing = entry
                } label: {
                  expenseRow(entry, currency: trip.currency)
                }.buttonStyle(.plain).padding(.horizontal, 24)
              }
            }
            Spacer(minLength: 8)
          }.frame(maxWidth: 700).frame(maxWidth: .infinity)
        }.refreshable { await store.refresh() }
          .overlay(alignment: .bottomTrailing) {
            PrimaryButton(title: "Add expense", icon: "plus") {
              editing = Expense(
                tripId: tripId, userId: store.userId ?? "", category: .food)
            }
            .frame(width: 170)
            .shadow(color: .black.opacity(0.14), radius: 8, y: 4)
            .padding(.trailing, 24).padding(.bottom, 16)
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
    }.canvasScreen().navigationTitle(trip?.name ?? "Trip").navigationBarTitleDisplayMode(.inline)
      .toolbarBackground(Palette.canvas, for: .navigationBar)
      .toolbarBackground(.visible, for: .navigationBar)
  }

  /// The hero: one 16:9 photograph at 20pt radius, then the listing title and
  /// its facts stacked underneath. Text never sits on the photograph.
  private func hero(_ trip: Trip) -> some View {
    VStack(alignment: .leading, spacing: 0) {
      TripArtwork(trip: trip, url: store.coverURL(trip.coverPath), aspect: 16 / 9)
        .clipShape(RoundedRectangle(cornerRadius: Radius.panel))
      VStack(alignment: .leading, spacing: 4) {
        Text(trip.name).typeStyle(.titleLarge).fixedSize(horizontal: false, vertical: true)
        TripMeta(trip: trip).padding(.top, 4)
        Text(phaseLabel(trip)).typeStyle(.labelMedium).foregroundStyle(Palette.ink)
      }.padding(.top, 16)
    }.padding(.horizontal, 24)
  }

  /// The search pill: full 32pt radius, hairline border, one soft shadow.
  private var searchField: some View {
    HStack(spacing: 10) {
      Image(systemName: "magnifyingglass").font(.system(size: 15, weight: .medium))
        .foregroundStyle(Palette.ink)
      TextField("Search titles and notes", text: $query).typeStyle(.bodyMedium)
        .accessibilityLabel("Search expenses")
    }
    .padding(.horizontal, 16).frame(height: 48)
    .background(Palette.canvas, in: RoundedRectangle(cornerRadius: Radius.pill))
    .overlay(RoundedRectangle(cornerRadius: Radius.pill).stroke(Palette.hairline, lineWidth: 1))
    .shadow(color: .black.opacity(0.04), radius: 3, x: 0, y: 2)
  }

  /// Outlined pills: the system's secondary control, never a filled one.
  private var filters: some View {
    HStack(spacing: 8) {
      filterMenu(
        label: WayfareCore.Category(rawValue: category)?.label ?? "All categories",
        selection: $category,
        options: [("All categories", "")]
          + WayfareCore.Category.allCases.map { ($0.label, $0.rawValue) })
      let members = store.members.filter { $0.tripId == tripId }
      if members.count > 1 {
        filterMenu(
          label: payer.isEmpty ? "All travellers" : store.name(for: payer),
          selection: $payer,
          options: [("All travellers", "")] + members.map { (store.name(for: $0.userId), $0.userId) }
        )
      }
    }
  }

  private func filterMenu(label: String, selection: Binding<String>, options: [(String, String)])
    -> some View
  {
    Menu {
      Picker("", selection: selection) {
        ForEach(options, id: \.1) { Text($0.0).tag($0.1) }
      }
    } label: {
      HStack(spacing: 6) {
        Text(label).typeStyle(.labelLarge).lineLimit(1)
        Image(systemName: "chevron.down").font(.system(size: 12, weight: .medium))
      }
      .foregroundStyle(Palette.ink).frame(maxWidth: .infinity).frame(height: 48)
      .background(Palette.canvas, in: RoundedRectangle(cornerRadius: Radius.panel))
      .overlay(RoundedRectangle(cornerRadius: Radius.panel).stroke(Palette.hairline, lineWidth: 1))
    }
  }

  /// The booking panel, inline on a phone: white card, hairline border, the
  /// layered lift, and the figure set large at the top the way a price is.
  private func bookingPanel(_ trip: Trip) -> some View {
    let summary = budgetSummary(trip, expenses: entries)
    let showRemaining: Bool
    if case .active = tripPhase(trip) {
      showRemaining = trip.budget > 0
    } else {
      showRemaining = false
    }
    let over = showRemaining && summary.remaining < 0
    return VStack(alignment: .leading, spacing: 0) {
      HStack(alignment: .lastTextBaseline, spacing: 0) {
        Text(money(showRemaining ? abs(summary.remaining) : summary.spent, trip.currency))
          .typeStyle(.displaySmall).monospacedDigit()
          .foregroundStyle(over ? Palette.errorRed : Palette.ink)
        Text(showRemaining ? (over ? " over" : " left") : " spent")
          .typeStyle(.bodyLarge).foregroundStyle(Palette.ash)
      }
      Text(
        trip.budget > 0
          ? "\(money(summary.spent, trip.currency)) spent of \(money(trip.budget, trip.currency))"
          : "No budget set — just keeping count."
      ).typeStyle(.bodyMedium).foregroundStyle(Palette.ash).padding(.top, 4)
      if trip.budget > 0 {
        BudgetMeter(spent: summary.spent, budget: trip.budget).padding(.top, 16)
      }
      if let available = summary.availablePerDay {
        Text("\(money(available, trip.currency)) available per day").typeStyle(.titleMedium)
          .padding(.top, 16)
        Text("Across \(summary.daysLeft ?? 0) days, including today.")
          .typeStyle(.bodyMedium).foregroundStyle(Palette.ash).padding(.top, 2)
      }
      // The rules strip: hairline above, facts in a row, nothing shouting.
      HairlineDivider().padding(.top, 20)
      HStack(alignment: .top, spacing: 12) {
        panelFact(
          summary.remaining < 0 ? "Over budget" : "Remaining",
          trip.budget > 0 ? money(abs(summary.remaining), trip.currency) : "—")
        panelFact("Daily average", summary.perDay.map { money($0, trip.currency) } ?? "—")
        panelFact("Entries", "\(entries.filter { $0.syncState != .failed }.count)")
      }.padding(.top, 16)
      if entries.contains(where: { $0.syncState == .pending }) {
        Text("Includes entries waiting to sync.").typeStyle(.bodySmall)
          .foregroundStyle(Palette.ash).padding(.top, 16)
      }
      Text("Group spending, not who owes whom.").typeStyle(.bodySmall)
        .foregroundStyle(Palette.ash).padding(.top, 4)
    }
    .padding(24)
    .background(Palette.canvas, in: RoundedRectangle(cornerRadius: Radius.card))
    .overlay(RoundedRectangle(cornerRadius: Radius.card).stroke(Palette.hairline, lineWidth: 1))
    .panelElevation()
  }

  private func panelFact(_ label: String, _ value: String) -> some View {
    VStack(alignment: .leading, spacing: 4) {
      Text(label).typeStyle(.bodySmall).foregroundStyle(Palette.ash)
      Text(value).typeStyle(.labelMedium).monospacedDigit()
    }.frame(maxWidth: .infinity, alignment: .leading)
  }

  /// The amenity grid: a 24pt outline glyph, a 16pt label, and a hairline
  /// between every row. Category glyphs stay monochrome — one accent only.
  private func breakdown(_ trip: Trip) -> some View {
    let groups = WayfareCore.Category.allCases.map { group in
      (
        group,
        entries.filter { $0.category == group && $0.syncState != .failed }
          .reduce(Decimal.zero) { $0 + $1.amount }
      )
    }.filter { $0.1 > 0 }.sorted { $0.1 > $1.1 }
    let total = groups.reduce(Decimal.zero) { $0 + $1.1 }
    return VStack(alignment: .leading, spacing: 8) {
      if !groups.isEmpty {
        Text("Spending by category").typeStyle(.headlineSmall)
        ForEach(Array(groups.enumerated()), id: \.element.0) { index, entry in
          if index > 0 { HairlineDivider() }
          Button {
            category = entry.0.rawValue
          } label: {
            HStack(spacing: 16) {
              Image(systemName: entry.0.symbol).font(.system(size: 18))
                .foregroundStyle(Palette.ink).frame(width: 24)
              Text(entry.0.label).typeStyle(.bodyLarge)
              Spacer(minLength: 8)
              Text(percentText(entry.1, of: total)).typeStyle(.bodyMedium)
                .foregroundStyle(Palette.ash)
              Text(money(entry.1, trip.currency)).typeStyle(.labelMedium).monospacedDigit()
            }.frame(minHeight: 44).padding(.vertical, 8)
          }.buttonStyle(.plain).foregroundStyle(Palette.ink)
        }
      }
    }
  }

  private func percentText(_ amount: Decimal, of total: Decimal) -> String {
    guard total > 0 else { return "0%" }
    let ratio = (amount as NSDecimalNumber).doubleValue / (total as NSDecimalNumber).doubleValue
    return "\(Int((ratio * 100).rounded()))%"
  }

  private var ledgerEmptyState: some View {
    VStack(spacing: 14) {
      Image(systemName: "wallet.pass").font(.system(size: 24))
        .foregroundStyle(Palette.ash).frame(width: 56, height: 56)
        .background(Palette.softCloud, in: Circle())
      Text(
        entries.isEmpty
          ? "No expenses yet. Add the first one when it lands."
          : "No entries match these filters."
      ).typeStyle(.bodyMedium).foregroundStyle(Palette.ash).multilineTextAlignment(.center)
    }.frame(maxWidth: .infinity).padding(.vertical, 36).padding(.horizontal, 24)
  }

  /// The review-card row: a circular glyph where an avatar would sit, the title
  /// in 16/600, its payer in 14/500 ash, and no border of its own.
  private func expenseRow(_ expense: Expense, currency: String) -> some View {
    HStack(spacing: 12) {
      Image(systemName: expense.category.symbol).font(.system(size: 16))
        .foregroundStyle(Palette.ink).frame(width: 40, height: 40)
        .background(Palette.softCloud, in: Circle())
      VStack(alignment: .leading, spacing: 2) {
        Text(expense.title).typeStyle(.titleMedium).lineLimit(1)
        Text(store.name(for: expense.userId)).typeStyle(.bodyMedium)
          .foregroundStyle(Palette.ash)
        if expense.syncState != .synced {
          Text(expense.syncState == .failed ? "Not saved · tap to resolve" : "Waiting to sync")
            .typeStyle(.bodySmall)
            .foregroundStyle(expense.syncState == .failed ? Palette.errorRed : Palette.ash)
        }
      }
      Spacer(minLength: 8)
      Text(money(expense.amount, currency)).typeStyle(.titleMedium).monospacedDigit()
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
            Text(code).typeStyle(.displaySmall).tracking(4).textSelection(.enabled)
            ShareLink(
              item: "Join \(trip.name) on Wayfare: \(webURL)/join/\(code)\nInvite code: \(code)"
            ) {
              Label("Share invite", systemImage: "square.and.arrow.up").typeStyle(.labelMedium)
            }
          }
        }
        Section("On this trip") {
          ForEach(store.members.filter { $0.tripId == trip.id }) { member in
            HStack(spacing: 12) {
              Text(store.name(for: member.userId).prefix(1).uppercased())
                .typeStyle(.titleMedium).frame(width: 40, height: 40)
                .background(Palette.softCloud, in: Circle())
              VStack(alignment: .leading, spacing: 2) {
                Text(store.name(for: member.userId)).typeStyle(.titleMedium)
                Text(member.role == "owner" ? "Organiser" : "Member").typeStyle(.bodyMedium)
                  .foregroundStyle(Palette.ash)
              }
              Spacer()
              if member.role != "owner",
                trip.userId == store.userId || member.userId == store.userId
              {
                Button(member.userId == store.userId ? "Leave" : "Remove", role: .destructive) {
                  removing = member
                }.typeStyle(.labelMedium).foregroundStyle(Palette.errorRed)
              }
            }
          }
        }
        if let error { Notice(text: error, isError: true) }
      }.canvasScreen().disabled(busy).navigationTitle("Travel companions")
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
