import SwiftUI
import WayfareCore

struct TripForm: View {
  @Environment(AppStore.self) private var store
  @Environment(\.dismiss) private var dismiss
  let trip: Trip
  let isNew: Bool
  let onSave: (String) -> Void
  @State private var draft: Trip
  @State private var budget: String
  @State private var busy = false
  @State private var error: String?
  @State private var discard = false

  init(trip: Trip, isNew: Bool, onSave: @escaping (String) -> Void) {
    self.trip = trip
    self.isNew = isNew
    self.onSave = onSave
    _draft = State(initialValue: trip)
    _budget = State(initialValue: trip.budget == 0 ? "" : "\(trip.budget)")
  }
  private var dirty: Bool { draft != trip || budget != (trip.budget == 0 ? "" : "\(trip.budget)") }

  var body: some View {
    NavigationStack {
      Form {
        Section("The next chapter") {
          TextField("Trip name", text: $draft.name)
          TextField(
            "Destination",
            text: Binding(get: { draft.destination ?? "" }, set: { draft.destination = $0 }))
        }
        Section("When") {
          OptionalDatePicker(title: "Departure", value: $draft.startDate)
          OptionalDatePicker(title: "Return", value: $draft.endDate)
        }
        Section {
          TextField("Budget in \(draft.currency)", text: $budget).keyboardType(.decimalPad)
        } footer: {
          Text("Leave blank to track spending without a limit.")
        }
        if let error { Notice(text: error, isError: true) }
        PrimaryButton(title: isNew ? "Start the ledger" : "Save changes", busy: busy, action: save)
      }.canvasScreen().disabled(busy)
        .navigationTitle(isNew ? "Where are you headed?" : "Update your trip")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
          ToolbarItem(placement: .cancellationAction) {
            Button("Cancel") { if dirty { discard = true } else { dismiss() } }.disabled(busy)
          }
        }
        .confirmationDialog(
          "Discard your changes?", isPresented: $discard, titleVisibility: .visible
        ) {
          Button("Discard changes", role: .destructive) { dismiss() }
        }
    }.interactiveDismissDisabled(dirty || busy)
  }

  private func save() {
    guard !busy else { return }
    do {
      var result = draft
      result.name = result.name.trimmingCharacters(in: .whitespacesAndNewlines)
      result.destination = result.destination?.trimmingCharacters(in: .whitespacesAndNewlines)
      if result.destination?.isEmpty == true { result.destination = nil }
      guard (1...80).contains(result.name.count), (result.destination?.count ?? 0) <= 120 else {
        throw FormError(
          "Use a trip name of 1–80 characters and a destination of at most 120 characters.")
      }
      guard let amount = parseAmount(budget.isEmpty ? "0" : budget), amount >= 0,
        amount < 10_000_000_000
      else {
        throw FormError("Enter a valid budget of zero or more.")
      }
      if let start = result.startDate, let end = result.endDate, end < start {
        throw FormError("The return date is before departure.")
      }
      result.budget = rounded(amount)
      busy = true
      error = nil
      Task {
        defer { busy = false }
        do {
          let id = try await store.saveTrip(result, isNew: isNew)
          // Cover generation is optional and cannot turn a saved trip into a failed form.
          if isNew && result.destination != nil {
            Task {
              do { try await store.requestCover(tripId: id) } catch {
                store.notice =
                  "Trip saved. The cover could not be generated; retry from trip options."
              }
            }
          }
          dismiss()
          onSave(id)
        } catch { self.error = error.localizedDescription }
      }
    } catch { self.error = error.localizedDescription }
  }
}

struct OptionalDatePicker: View {
  let title: String
  @Binding var value: String?
  var body: some View {
    Toggle(
      "Set \(title.lowercased()) date",
      isOn: Binding(get: { value != nil }, set: { value = $0 ? Day.today : nil }))
    if value != nil {
      DatePicker(
        title,
        selection: Binding(
          get: { value.flatMap(Day.date) ?? Date() }, set: { value = Day.string($0) }),
        displayedComponents: .date)
    }
  }
}

struct ExpenseForm: View {
  @Environment(AppStore.self) private var store
  @Environment(\.dismiss) private var dismiss
  let trip: Trip
  let expense: Expense
  let isNew: Bool
  @State private var draft: Expense
  @State private var amount: String
  @State private var currency: String
  @State private var busy = false
  @State private var error: String?
  @State private var discard = false
  @State private var deleting = false

  init(trip: Trip, expense: Expense, isNew: Bool) {
    self.trip = trip
    self.expense = expense
    self.isNew = isNew
    _draft = State(initialValue: expense)
    _amount = State(initialValue: isNew ? "" : "\(expense.originalAmount ?? expense.amount)")
    let remembered = UserDefaults.standard.string(forKey: "paidIn.\(expense.userId).\(trip.id)")
    _currency = State(
      initialValue: expense.originalCurrency ?? (isNew ? remembered : nil) ?? trip.currency)
  }
  private var liveExpense: Expense { store.expenses.first { $0.id == expense.id } ?? expense }
  private var readOnly: Bool { !isNew && expense.userId != store.userId }
  private var unsynced: Bool { !isNew && liveExpense.syncState != .synced }
  private var dirty: Bool {
    draft != expense || amount != (isNew ? "" : "\(expense.originalAmount ?? expense.amount)")
      || (!isNew && currency != (expense.originalCurrency ?? trip.currency))
  }
  private var appliedRate: Decimal? {
    // Changing a note/category/date must not revalue a historical cost.
    if !isNew, currency == (expense.originalCurrency ?? trip.currency) {
      return expense.fxRate ?? (currency == trip.currency ? 1 : nil)
    }
    return rateBetween(from: currency, to: trip.currency, rates: store.fx.rates)
  }
  private var converted: Decimal? {
    guard let value = parseAmount(amount), let rate = appliedRate else { return nil }
    if !isNew, value == (expense.originalAmount ?? expense.amount),
      currency == (expense.originalCurrency ?? trip.currency)
    {
      return expense.amount
    }
    return rounded(value * rate)
  }

  var body: some View {
    NavigationStack {
      Form {
        if readOnly || unsynced {
          Section {
            Text(expense.title).typeStyle(.titleMedium)
            Text(money(expense.amount, trip.currency)).typeStyle(.displaySmall)
            Text("\(expense.category.label) · \(expense.spentOn)")
            if let note = expense.note { Text(note) }
            if let original = expense.originalAmount, let code = expense.originalCurrency {
              Text("Originally \(money(original, code))")
            }
          }
          if readOnly {
            Text(
              "Paid by \(store.name(for: expense.userId)). Only the traveller who added this expense can edit it."
            )
          } else {
            Notice(
              text: liveExpense.syncState == .failed
                ? "This entry did not reach the server. It is not counted in totals."
                : "Saved on this device. Waiting to sync; you can edit it once it lands.")
            if let message = liveExpense.syncError { Notice(text: message, isError: true) }
            Button("Try again") { perform { try await store.retryExpense(liveExpense) } }
            if liveExpense.syncState == .failed {
              Button("Discard entry", role: .destructive) { deleting = true }
            }
          }
        } else {
          Section("The expense") {
            TextField("What did you spend on?", text: $draft.title)
            TextField("Amount paid", text: $amount).keyboardType(.decimalPad)
            Picker("Paid in", selection: $currency) {
              ForEach(Array(Set(store.fx.rates.keys).union([currency])).sorted(), id: \.self) {
                Text($0).tag($0)
              }
            }
            Picker("Category", selection: $draft.category) {
              ForEach(WayfareCore.Category.allCases, id: \.self) {
                Label($0.label, systemImage: $0.symbol).tag($0)
              }
            }
            DatePicker(
              "Paid on",
              selection: Binding(
                get: { Day.date(draft.spentOn) ?? Date() }, set: { draft.spentOn = Day.string($0) }),
              displayedComponents: .date)
          }
          Section("Note (optional)") {
            TextField(
              "A little context",
              text: Binding(get: { draft.note ?? "" }, set: { draft.note = $0 }), axis: .vertical
            ).lineLimit(3...6)
          }
          if currency != trip.currency {
            Section("Ledger amount") {
              Text(converted.map { money($0, trip.currency) } ?? "Enter an amount")
              Text(
                !isNew && currency == expense.originalCurrency
                  ? "Using the rate saved with this expense."
                  : store.fx.stale
                    ? "Offline estimate from cached reference rates. This rate will be saved with the expense."
                    : "ECB reference rate · \(store.fx.date)"
              )
              .typeStyle(.bodySmall).foregroundStyle(Palette.ash)
            }
          }
          PrimaryButton(title: isNew ? "Add expense" : "Save changes", busy: busy, action: save)
          if !isNew { Button("Delete expense", role: .destructive) { deleting = true } }
        }
        if let error { Notice(text: error, isError: true) }
      }.canvasScreen().disabled(busy)
        .navigationTitle(readOnly ? "Expense details" : isNew ? "Add expense" : "Update expense")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
          ToolbarItem(placement: .cancellationAction) {
            Button(readOnly || unsynced ? "Close" : "Cancel") {
              if dirty && !readOnly && !unsynced { discard = true } else { dismiss() }
            }.disabled(busy)
          }
        }
        .confirmationDialog(
          "Discard your changes?", isPresented: $discard, titleVisibility: .visible
        ) { Button("Discard changes", role: .destructive) { dismiss() } }
        .confirmationDialog(
          "Delete this expense?", isPresented: $deleting, titleVisibility: .visible
        ) {
          Button("Delete expense", role: .destructive) {
            perform {
              if liveExpense.syncState == .failed {
                try await store.discardExpense(liveExpense)
              } else {
                try await store.deleteExpense(liveExpense)
              }
              dismiss()
            }
          }
        }
    }.interactiveDismissDisabled(busy || (dirty && !readOnly && !unsynced)).task {
      await store.refreshFX()
    }
  }

  private func save() {
    do {
      var result = draft
      result.title = result.title.trimmingCharacters(in: .whitespacesAndNewlines)
      guard (1...120).contains(result.title.count), (result.note?.count ?? 0) <= 500 else {
        throw FormError("Use a title of 1–120 characters and a note of at most 500 characters.")
      }
      guard let original = parseAmount(amount), original > 0, original < 10_000_000_000,
        let ledger = converted, ledger > 0, ledger < 10_000_000_000, let rate = appliedRate
      else {
        throw FormError("Enter a positive amount with a supported currency.")
      }
      result.amount = ledger
      result.originalAmount = rounded(original)
      result.originalCurrency = currency
      result.fxRate = rate
      result.syncState = .synced
      if isNew { result.createdAt = ISO8601DateFormatter().string(from: Date()) }
      perform {
        try await store.saveExpense(result, isNew: isNew)
        UserDefaults.standard.set(currency, forKey: "paidIn.\(result.userId).\(trip.id)")
        dismiss()
      }
    } catch { self.error = error.localizedDescription }
  }

  private func perform(_ action: @escaping () async throws -> Void) {
    guard !busy else { return }
    busy = true
    error = nil
    Task {
      defer { busy = false }
      do { try await action() } catch { self.error = error.localizedDescription }
    }
  }
}

// No grouping separators: accept either decimal keyboard separator, never partial parses.
func parseAmount(_ text: String) -> Decimal? {
  let value = text.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(
    of: ",", with: ".")
  guard value.range(of: "^[0-9]+([.][0-9]{1,2})?$", options: .regularExpression) != nil else {
    return nil
  }
  return Decimal(string: value, locale: Locale(identifier: "en_US_POSIX"))
}
