import SwiftUI
import WayfareCore

/// Applies one keypad press to the amount. Kept pure and separate so the rules —
/// one decimal point, at most two decimals, no runaway leading zeroes — are
/// readable in one place rather than scattered through the key handlers.
/// Mirrors `applyKey` in the Android `AddExpenseSheet.kt`.
func applyKey(_ current: String, _ key: String) -> String {
  if key == "⌫" { return String(current.dropLast()) }
  if key == "." {
    if current.contains(".") { return current }
    return current.isEmpty ? "0." : current + "."
  }
  if current.contains("."), current.split(separator: ".", maxSplits: 1).last?.count ?? 0 >= 2 {
    return current
  }
  if current == "0" { return key }
  if current.count >= 12 { return current }
  return current + key
}

/// Adding a line. The amount is typed on a keypad rather than a keyboard,
/// because an amount is the one field that is always a number and always the
/// first thing you know — the sheet opens straight onto it.
///
/// The design's payer control ("You paid / Sam paid / Split it") is deliberately
/// absent: this is one person's tape, and there is nobody to split with.
struct AddExpenseSheet: View {
  @Environment(AppStore.self) private var store
  @Environment(\.dismiss) private var dismiss
  let trip: Trip
  let expense: Expense?

  @State private var amount = ""
  @State private var title = ""
  @State private var paidIn = ""
  @State private var category: WayfareCore.Category = .food
  @State private var spentOn = Day.today
  @State private var note = ""
  @State private var showNote = false
  @State private var pickingDate = false
  @State private var busy = false
  @State private var error: String?
  @State private var confirmDelete = false
  @State private var loaded = false

  /// Shared with the trip form so the keypad accepts exactly what the ledger does.
  private var parsed: Decimal? { parseAmount(amount).flatMap { $0 > 0 ? $0 : nil } }
  private var rate: Decimal? {
    // Changing a note, category or date must not revalue a historical cost, so
    // an edit that leaves the currency alone keeps the rate saved with the line.
    if let expense, paidIn == (expense.originalCurrency ?? trip.currency) {
      return expense.fxRate ?? (paidIn == trip.currency ? 1 : nil)
    }
    return rateBetween(from: paidIn, to: trip.currency, rates: store.fx.rates)
  }
  private var converted: Decimal? {
    guard let parsed, let rate else { return nil }
    return rounded(parsed * rate)
  }
  private var currencies: [String] {
    Array(Set(store.fx.rates.keys).union([paidIn, trip.currency])).sorted()
  }

  var body: some View {
    VStack(spacing: 0) {
      HStack {
        Text(expense == nil ? "Add a line" : "Fix the line").typeStyle(.headlineSmall)
        Spacer()
        CircleIconButton(symbol: "xmark", label: "Close", raised: true) { dismiss() }
      }.padding(.top, 12)

      if let error { Notice(text: error, isError: true).padding(.top, 14) }

      // The figure, set the way a till prints it: big, amber, monospaced.
      VStack(spacing: 10) {
        Text(symbolFor(paidIn) + (amount.isEmpty ? "0" : amount))
          .font(.custom(Face.monoMedium, size: 54, relativeTo: .largeTitle)).tracking(-2)
          .foregroundStyle(amount.isEmpty ? Palette.amber.opacity(0.45) : Palette.amber)
          .lineLimit(1).minimumScaleFactor(0.5)
          .accessibilityLabel("Amount \(amount.isEmpty ? "zero" : amount) \(paidIn)")
        // The title sits where a receipt puts the merchant: under the figure.
        TextField(
          "", text: $title,
          prompt: Text("What was it for?").foregroundStyle(Palette.slate)
        )
        .typeStyle(.titleMedium).foregroundStyle(Palette.paper)
        .multilineTextAlignment(.center)
        if paidIn != trip.currency, let converted {
          Text(
            "≈ \(money(converted, trip.currency)) · 1 \(paidIn) = \(rate.map { "\(rounded($0, scale: 4))" } ?? "—") \(trip.currency)"
              + (store.fx.stale ? " · offline rate" : "")
          )
          .typeStyle(.bodySmall).foregroundStyle(Palette.slate).multilineTextAlignment(.center)
        }
      }.padding(.top, 18)

      // Category first, because it is the one chip you always set.
      ScrollView(.horizontal, showsIndicators: false) {
        HStack(spacing: 8) {
          ForEach(WayfareCore.Category.allCases, id: \.self) { item in
            FilterPill(label: item.label, selected: category == item) { category = item }
          }
        }
      }.padding(.top, 14)

      // Then the three facts the design leaves out but the ledger needs.
      HStack(spacing: 8) {
        FilterPill(label: dateLabel, selected: spentOn != Day.today) { pickingDate = true }
        Menu {
          Picker("", selection: $paidIn) {
            ForEach(currencies, id: \.self) { Text($0).tag($0) }
          }
        } label: {
          Text(paidIn).typeStyle(.bodySmall).foregroundStyle(Palette.steel)
            .frame(maxWidth: .infinity).frame(height: 34)
            .overlay(
              RoundedRectangle(cornerRadius: Radius.chip).stroke(Palette.outline, lineWidth: 1))
        }
        FilterPill(label: note.isEmpty ? "Note" : "Note ✓", selected: showNote) {
          showNote.toggle()
        }
      }.padding(.top, 10)

      if showNote {
        TextField(
          "", text: $note,
          prompt: Text("Anything worth remembering").foregroundStyle(Palette.slate),
          axis: .vertical
        )
        .typeStyle(.bodyMedium).foregroundStyle(Palette.paper).lineLimit(1...3)
        .padding(.horizontal, 14).padding(.vertical, 12)
        .overlay(RoundedRectangle(cornerRadius: Radius.control).stroke(Palette.outline, lineWidth: 1))
        .padding(.top, 10)
      }

      keypad.padding(.top, 14)

      PrimaryButton(
        title: expense == nil ? "Print it to the tape" : "Save the line", busy: busy, action: save
      ).padding(.top, 14)

      if expense != nil {
        Button("Delete this line", role: .destructive) { confirmDelete = true }
          .typeStyle(.bodyMedium).fontWeight(.semibold).foregroundStyle(Palette.errorRed)
          .padding(.top, 10)
      }
      Spacer(minLength: 12)
    }
    .padding(.horizontal, 20)
    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
    .background(Palette.card)
    .presentationDetents([.large])
    .presentationCornerRadius(Radius.sheet)
    .presentationBackground(Palette.card)
    .interactiveDismissDisabled(busy)
    .sheet(isPresented: $pickingDate) { datePicker }
    .confirmationDialog(
      "Delete this line?", isPresented: $confirmDelete, titleVisibility: .visible
    ) {
      Button("Delete line", role: .destructive) {
        guard let expense else { return }
        run {
          try await store.deleteExpense(expense)
          dismiss()
        }
      }
    } message: {
      Text("This cannot be undone.")
    }
    .onAppear(perform: load)
  }

  private var dateLabel: String {
    switch spentOn {
    case Day.today: "Today"
    case Day.string(Date().addingTimeInterval(-86400)): "Yesterday"
    default: shortDate(spentOn) ?? spentOn
    }
  }

  private var keypad: some View {
    VStack(spacing: 10) {
      ForEach(Array(keys.chunked(into: 3).enumerated()), id: \.offset) { _, row in
        HStack(spacing: 10) {
          ForEach(row, id: \.self) { key in
            Button { amount = applyKey(amount, key) } label: {
              Text(key).font(.custom(Face.mono, size: 22, relativeTo: .title2))
                .foregroundStyle(Palette.paper)
                .frame(maxWidth: .infinity).frame(height: 52)
                .background(Palette.cardRaised, in: RoundedRectangle(cornerRadius: Radius.key))
            }.buttonStyle(.plain)
          }
        }
      }
    }
  }

  private var keys: [String] { ["1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "⌫"] }

  private var datePicker: some View {
    NavigationStack {
      DatePicker(
        "Date",
        selection: Binding(
          get: { Day.date(spentOn) ?? Date() },
          set: { spentOn = Day.string($0) }),
        displayedComponents: .date
      )
      .datePickerStyle(.graphical).tint(Palette.amber).padding()
      .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
      .canvasScreen().navigationTitle("When was it?").navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .confirmationAction) {
          Button("Done") { pickingDate = false }
        }
      }
    }.presentationDetents([.medium])
  }

  private func load() {
    guard !loaded else { return }
    loaded = true
    let remembered = UserDefaults.standard.string(forKey: Self.paidInKey(trip: trip, store: store))
    paidIn = expense?.originalCurrency ?? (expense == nil ? remembered : nil) ?? trip.currency
    if let expense {
      let shown = expense.originalAmount ?? expense.amount
      amount = "\(shown)"
      title = expense.title
      category = expense.category
      spentOn = expense.spentOn
      note = expense.note ?? ""
      showNote = !(expense.note ?? "").isEmpty
    }
  }

  private static func paidInKey(trip: Trip, store: AppStore) -> String {
    "paidIn.\(store.userId ?? "").\(trip.id)"
  }

  private func save() {
    error =
      if parsed == nil {
        "Enter an amount above zero."
      } else if title.trimmingCharacters(in: .whitespaces).isEmpty {
        "What was it for?"
      } else if paidIn != trip.currency && rate == nil {
        "No rate is available for \(paidIn) to \(trip.currency)."
      } else {
        nil
      }
    guard error == nil, let parsed else { return }

    var draft = expense ?? Expense(tripId: trip.id, userId: store.userId ?? "")
    draft.title = title.trimmingCharacters(in: .whitespaces)
    draft.amount = rounded(converted ?? parsed)
    draft.originalAmount = paidIn == trip.currency ? nil : rounded(parsed)
    draft.originalCurrency = paidIn == trip.currency ? nil : paidIn
    draft.fxRate = paidIn == trip.currency ? nil : rate
    draft.category = category
    draft.spentOn = spentOn
    draft.note = note.trimmingCharacters(in: .whitespaces).isEmpty ? nil : note
    draft.syncState = .synced
    if expense == nil { draft.createdAt = ISO8601DateFormatter().string(from: Date()) }

    let isNew = expense == nil
    run {
      try await store.saveExpense(draft, isNew: isNew)
      UserDefaults.standard.set(paidIn, forKey: Self.paidInKey(trip: trip, store: store))
      dismiss()
    }
  }

  private func run(_ action: @escaping () async throws -> Void) {
    busy = true
    Task {
      defer { busy = false }
      do { try await action() } catch { self.error = error.localizedDescription }
    }
  }
}

/// A line someone else added: readable, never editable.
struct ReadOnlyExpenseSheet: View {
  @Environment(AppStore.self) private var store
  @Environment(\.dismiss) private var dismiss
  let expense: Expense
  let currency: String
  var body: some View {
    NavigationStack {
      ScrollView {
        VStack(alignment: .leading, spacing: 12) {
          FigureWithQualifier(figure: money(expense.amount, currency), size: 38)
          Text(
            "Printed by \(store.name(for: expense.userId)). Only the traveller who added a line can change it."
          ).typeStyle(.bodyMedium).foregroundStyle(Palette.slate)
          MonoLabel(
            text: "\(expense.category.label.uppercased()) · \(tapeDayLabel(expense.spentOn))",
            color: Palette.slate)
          if let original = expense.originalCurrency, let amount = expense.originalAmount {
            Text("Originally \(amount) \(original)").typeStyle(.bodyMedium)
              .foregroundStyle(Palette.slate)
          }
          if let note = expense.note { Text(note).typeStyle(.bodyMedium) }
        }.frame(maxWidth: .infinity, alignment: .leading).padding(20)
      }
      .canvasScreen().navigationTitle(expense.title).navigationBarTitleDisplayMode(.inline)
      .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Close") { dismiss() } } }
    }.presentationDetents([.medium])
  }
}

/// A line that never reached the server: kept, explained, retried or discarded.
struct UnsyncedExpenseSheet: View {
  @Environment(AppStore.self) private var store
  @Environment(\.dismiss) private var dismiss
  let expense: Expense
  let currency: String
  @State private var busy = false
  @State private var error: String?
  var body: some View {
    let failed = expense.syncState == .failed
    NavigationStack {
      ScrollView {
        VStack(alignment: .leading, spacing: 14) {
          DottedLeaderRow(label: expense.title, amount: money(expense.amount, currency))
          Text(
            failed
              ? "It is kept here so nothing is lost, but it is not counted in the trip total and other travellers cannot see it yet."
              : "It is saved on this phone and will sync as soon as there is a connection. You can edit it once it lands."
          ).typeStyle(.bodyMedium).foregroundStyle(Palette.slate)
          if let reason = expense.syncError { Notice(text: reason, isError: failed) }
          if let error { Notice(text: error, isError: true) }
          PrimaryButton(title: "Try again", busy: busy) {
            run {
              try await store.retryExpense(expense)
              dismiss()
            }
          }
          if failed {
            Button("Discard this line", role: .destructive) {
              run {
                try await store.discardExpense(expense)
                dismiss()
              }
            }
            .typeStyle(.bodyMedium).fontWeight(.semibold).foregroundStyle(Palette.errorRed)
            .frame(maxWidth: .infinity)
          }
        }.padding(20)
      }
      .canvasScreen().disabled(busy)
      .navigationTitle(failed ? "This line did not print" : "Still printing")
      .navigationBarTitleDisplayMode(.inline)
      .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Close") { dismiss() } } }
    }.presentationDetents([.medium])
  }
  private func run(_ action: @escaping () async throws -> Void) {
    busy = true
    Task {
      defer { busy = false }
      do { try await action() } catch { self.error = error.localizedDescription }
    }
  }
}

extension Array {
  func chunked(into size: Int) -> [[Element]] {
    stride(from: 0, to: count, by: size).map { Array(self[$0..<Swift.min($0 + size, count)]) }
  }
}
