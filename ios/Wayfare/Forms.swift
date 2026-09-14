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
  @State private var fieldErrors: [String: String] = [:]
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
      ScrollView {
        VStack(alignment: .leading, spacing: 16) {
          WayfareTextField(label: "Trip name", text: $draft.name, error: fieldErrors["name"])
          WayfareTextField(
            label: "Destination",
            text: Binding(get: { draft.destination ?? "" }, set: { draft.destination = $0 }), error: fieldErrors["destination"])
          OptionalDatePicker(title: "Departure", value: $draft.startDate)
          OptionalDatePicker(title: "Return", value: $draft.endDate, error: fieldErrors["dates"])
          VStack(alignment: .leading, spacing: 6) {
            WayfareTextField(label: "Budget in \(draft.currency)", text: $budget, prompt: "2500", error: fieldErrors["budget"])
              .keyboardType(.decimalPad)
            Text("Leave blank to track spending without a limit.").typeStyle(.bodySmall)
              .foregroundStyle(Palette.ash)
          }
        }.padding(24).frame(maxWidth: 700).frame(maxWidth: .infinity)
      }.scrollDismissesKeyboard(.interactively).canvasScreen().disabled(busy)
        .safeAreaInset(edge: .bottom, spacing: 0) {
          ActionBar(error: error) {
            PrimaryButton(
              title: isNew ? "Start the ledger" : "Save changes", busy: busy, action: save)
          }
        }
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
    fieldErrors = [:]
    error = nil
    var result = draft
    result.name = result.name.trimmingCharacters(in: .whitespacesAndNewlines)
    result.destination = result.destination?.trimmingCharacters(in: .whitespacesAndNewlines)
    if result.destination?.isEmpty == true { result.destination = nil }
    guard (1...80).contains(result.name.count) else {
      fieldErrors["name"] = "Use a trip name of 1–80 characters."
      return
    }
    guard (result.destination?.count ?? 0) <= 120 else {
      fieldErrors["destination"] = "Use at most 120 characters."
      return
    }
    guard let amount = parseAmount(budget.isEmpty ? "0" : budget), amount >= 0,
      amount < 10_000_000_000 else {
      fieldErrors["budget"] = "Enter a budget from zero to 9,999,999,999.99."
      return
    }
    if let start = result.startDate, let end = result.endDate, end < start {
      fieldErrors["dates"] = "Return must be on or after departure."
      return
    }
    result.budget = rounded(amount)
    busy = true
    error = nil
    Task {
      defer { busy = false }
      do {
        let id = try await store.saveTrip(result, isNew: isNew)
        dismiss()
        onSave(id)
      } catch { self.error = error.localizedDescription }
    }
  }
}

struct OptionalDatePicker: View {
  let title: String
  @Binding var value: String?
  var error: String?
  @State private var open = false
  @State private var selected = Date()
  var body: some View {
    LabeledField(label: title, error: error) {
      Button {
        selected = value.flatMap(Day.date) ?? Date()
        open = true
      } label: {
        HStack {
          Text(value.flatMap(shortDate) ?? "Add a date").typeStyle(.bodyLarge)
          Spacer(minLength: 8)
          Image(systemName: "calendar").font(.system(size: 16))
        }.foregroundStyle(value == nil ? Palette.ash : Palette.ink)
          .frame(minHeight: 48).contentShape(Rectangle())
      }.buttonStyle(.plain).accessibilityLabel(title)
        .accessibilityValue(value ?? "No date selected")
    }
    .sheet(isPresented: $open) {
      NavigationStack {
        ScrollView {
          DatePicker(title, selection: $selected, displayedComponents: .date)
            .datePickerStyle(.graphical).padding(16)
        }.canvasScreen().navigationTitle(title).navigationBarTitleDisplayMode(.inline)
          .toolbar {
            ToolbarItem(placement: .cancellationAction) { Button("Cancel") { open = false } }
            ToolbarItem(placement: .confirmationAction) {
              Button("Use date") { value = Day.string(selected); open = false }
            }
            ToolbarItem(placement: .bottomBar) {
              if value != nil { Button("Clear date", role: .destructive) { value = nil; open = false } }
            }
          }
      }.presentationDetents([.large])
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
  @State private var fieldErrors: [String: String] = [:]
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
      ScrollView {
        VStack(alignment: .leading, spacing: 16) {
          if readOnly || unsynced {
            VStack(alignment: .leading, spacing: 8) {
              Text(expense.title).typeStyle(.titleMedium)
              Text(money(expense.amount, trip.currency)).typeStyle(.displaySmall).monospacedDigit()
              Text("\(expense.category.label) · \(expense.spentOn)").typeStyle(.bodyMedium)
                .foregroundStyle(Palette.ash)
              if let note = expense.note { Text(note).typeStyle(.bodyLarge) }
              if let original = expense.originalAmount, let code = expense.originalCurrency {
                Text("Originally \(money(original, code))").typeStyle(.bodyMedium)
                  .foregroundStyle(Palette.ash)
              }
            }
            if readOnly {
              Text(
                "Paid by \(store.name(for: expense.userId)). Only the traveller who added this expense can edit it."
              ).typeStyle(.bodyMedium).foregroundStyle(Palette.ash)
            } else {
              Notice(
                text: liveExpense.syncState == .failed
                  ? "This entry did not reach the server. It is not counted in totals."
                  : "Saved on this device. Waiting to sync; you can edit it once it lands.")
              if let message = liveExpense.syncError { Notice(text: message, isError: true) }
              if liveExpense.syncState == .failed {
                destructiveButton("Discard entry") { deleting = true }
              }
            }
          } else {
            WayfareTextField(label: "What did you spend on?", text: $draft.title, error: fieldErrors["title"])
            WayfareTextField(label: "Amount paid", text: $amount, prompt: "0.00", error: fieldErrors["amount"])
              .keyboardType(.decimalPad)
            MenuField(label: "Paid in", value: currency) {
              Picker("Paid in", selection: $currency) {
                ForEach(Array(Set(store.fx.rates.keys).union([currency])).sorted(), id: \.self) {
                  Text($0).tag($0)
                }
              }
            }
            MenuField(label: "Category", value: draft.category.label) {
              Picker("Category", selection: $draft.category) {
                ForEach(WayfareCore.Category.allCases, id: \.self) {
                  Label($0.label, systemImage: $0.symbol).tag($0)
                }
              }
            }
            LabeledField(label: "Paid on") {
              DatePicker(
                "Paid on",
                selection: Binding(
                  get: { Day.date(draft.spentOn) ?? Date() },
                  set: { draft.spentOn = Day.string($0) }),
                displayedComponents: .date
              ).labelsHidden()
            }
            WayfareTextField(
              label: "Note (optional)",
              text: Binding(get: { draft.note ?? "" }, set: { draft.note = $0 }),
              prompt: "A little context", error: fieldErrors["note"], lines: 3...6)
            if currency != trip.currency {
              VStack(alignment: .leading, spacing: 4) {
                Text("Ledger amount").typeStyle(.bodyMedium).foregroundStyle(Palette.ash)
                Text(converted.map { money($0, trip.currency) } ?? "Enter an amount")
                  .typeStyle(.titleMedium).monospacedDigit()
                Text(
                  !isNew && currency == expense.originalCurrency
                    ? "Using the rate saved with this expense."
                    : store.fx.stale
                      ? "Offline estimate from cached reference rates. This rate will be saved with the expense."
                      : "ECB reference rate · \(store.fx.date)"
                )
                .typeStyle(.bodySmall).foregroundStyle(Palette.ash)
              }
              .padding(14).frame(maxWidth: .infinity, alignment: .leading)
              .background(Palette.softCloud, in: RoundedRectangle(cornerRadius: Radius.control))
            }
            if !isNew { destructiveButton("Delete expense") { deleting = true } }
          }
        }.padding(24).frame(maxWidth: 700).frame(maxWidth: .infinity)
      }.scrollDismissesKeyboard(.interactively).canvasScreen().disabled(busy)
        .safeAreaInset(edge: .bottom, spacing: 0) {
          // Someone else's expense has nothing to act on, so it gets no bar.
          if !readOnly {
            ActionBar(error: error) {
              if unsynced {
                PrimaryButton(title: "Try again", busy: busy) {
                  perform { try await store.retryExpense(liveExpense) }
                }
              } else {
                PrimaryButton(
                  title: isNew ? "Add expense" : "Save changes", busy: busy, action: save)
              }
            }
          }
        }
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

  private func destructiveButton(_ title: String, action: @escaping () -> Void) -> some View {
    Button(role: .destructive, action: action) {
      Text(title).typeStyle(.labelMedium).foregroundStyle(Palette.errorRed)
        .frame(maxWidth: .infinity, minHeight: 44)
    }
  }

  private func save() {
    fieldErrors = [:]
    error = nil
    var result = draft
    result.title = result.title.trimmingCharacters(in: .whitespacesAndNewlines)
    guard (1...120).contains(result.title.count) else {
      fieldErrors["title"] = "Use a title of 1–120 characters."
      return
    }
    guard (result.note?.count ?? 0) <= 500 else {
      fieldErrors["note"] = "Use at most 500 characters."
      return
    }
    guard let original = parseAmount(amount), original > 0, original < 10_000_000_000,
      let ledger = converted, ledger > 0, ledger < 10_000_000_000, let rate = appliedRate else {
      fieldErrors["amount"] = "Enter a positive amount with a supported currency."
      return
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
