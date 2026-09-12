import SwiftUI
import WayfareCore

/// The tape: every line in the order it happened, day by day, amounts running
/// out to the right margin. The overview answers how the trip is going; this
/// answers what actually happened.
struct TapeScreen: View {
  @Environment(AppStore.self) private var store
  let tripId: String
  let addOnOpen: Bool
  @Binding var path: [TripRoute]

  @State private var query = ""
  @State private var searching = false
  @State private var payer = ""
  @State private var adding = false
  @State private var editing: Expense?
  @State private var readOnly: Expense?
  @State private var unsynced: Expense?
  @State private var armed = false

  private var trip: Trip? { store.trips.first { $0.id == tripId } }
  private var entries: [Expense] { store.expenses.filter { $0.tripId == tripId } }
  private var lines: [Expense] {
    entries.filter {
      (store.tapeCategory.isEmpty || $0.category.rawValue == store.tapeCategory)
        && (payer.isEmpty || $0.userId == payer)
        && (query.isEmpty
          || ($0.title + " " + ($0.note ?? "")).localizedCaseInsensitiveContains(query))
    }.sorted { $0.spentOn == $1.spentOn ? $0.createdAt > $1.createdAt : $0.spentOn > $1.spentOn }
  }

  /// Category ranking is a property of the whole trip, so a filtered view keeps
  /// the same dot colour a line had before the filter was applied.
  private var ranks: [WayfareCore.Category: Int] {
    let totals = Dictionary(
      grouping: entries.filter { $0.syncState != .failed }, by: \.category
    ).mapValues { rows in rows.reduce(Decimal.zero) { $0 + $1.amount } }
    return Dictionary(
      uniqueKeysWithValues: totals.sorted { $0.value > $1.value }.enumerated().map {
        ($0.element.key, $0.offset)
      })
  }

  var body: some View {
    Group {
      if let trip {
        ScrollView {
          LazyVStack(alignment: .leading, spacing: 0) {
            if searching {
              TextField(
                "", text: $query,
                prompt: Text("Search titles and notes").foregroundStyle(Palette.slate)
              )
              .typeStyle(.bodyMedium).foregroundStyle(Palette.paper)
              .padding(.horizontal, 16).frame(height: 48)
              .background(Palette.card, in: RoundedRectangle(cornerRadius: Radius.control))
              .padding(.horizontal, 16).padding(.bottom, 12)
              .accessibilityLabel("Search the tape")
            }

            Text(
              entries.isEmpty
                ? "Nothing printed yet."
                : "\(lines.count) \(lines.count == 1 ? "line" : "lines"), in the order they happened."
            )
            .typeStyle(.bodyMedium).foregroundStyle(Palette.slate)
            .padding(.horizontal, 24).padding(.bottom, 10)

            filters.padding(.bottom, 4)

            if lines.isEmpty {
              VStack(spacing: 10) {
                MonoLabel(text: "— TAPE IS BLANK —", color: Palette.slate)
                Text(
                  entries.isEmpty
                    ? "Add the first line when it lands." : "No lines match these filters."
                ).typeStyle(.bodyMedium).foregroundStyle(Palette.slate)
              }.frame(maxWidth: .infinity).padding(.vertical, 40)
            }

            ForEach(days, id: \.self) { day in
              dayRule(day, currency: trip.currency).padding(.horizontal, 24)
              ForEach(lines.filter { $0.spentOn == day }) { expense in
                Button { open(expense) } label: {
                  tapeLine(expense, currency: trip.currency)
                }.buttonStyle(.plain).padding(.horizontal, 24)
              }
            }

            if !lines.isEmpty {
              MonoLabel(text: "— END OF TAPE —", color: Palette.slate)
                .frame(maxWidth: .infinity).padding(.top, 24)
            }
            Spacer(minLength: 100)
          }.frame(maxWidth: 700).frame(maxWidth: .infinity)
        }
        .refreshable { await store.refresh() }
        .overlay(alignment: .bottomTrailing) {
          AddLineButton { adding = true }.padding(16)
        }
        .toolbar {
          ToolbarItem(placement: .topBarTrailing) {
            Button {
              searching.toggle()
              if !searching { query = "" }
            } label: {
              Image(systemName: searching ? "xmark" : "magnifyingglass")
                .accessibilityLabel(searching ? "Close search" : "Search the tape")
            }
          }
          ToolbarItem(placement: .topBarTrailing) {
            MonoLabel(
              text: "\(trip.currency) · \(stampDate(Day.today))", color: Palette.slate, tracking: 1)
          }
        }
        .sheet(isPresented: $adding) { AddExpenseSheet(trip: trip, expense: nil) }
        .sheet(item: $editing) { AddExpenseSheet(trip: trip, expense: $0) }
        .sheet(item: $readOnly) { ReadOnlyExpenseSheet(expense: $0, currency: trip.currency) }
        .sheet(item: $unsynced) { UnsyncedExpenseSheet(expense: $0, currency: trip.currency) }
      } else {
        ContentUnavailableView(
          "Trip unavailable", systemImage: "ticket",
          description: Text("It may have been deleted, or you may no longer be a member."))
      }
    }
    .canvasScreen().navigationTitle("The tape").navigationBarTitleDisplayMode(.inline)
    .toolbarBackground(Palette.night, for: .navigationBar)
    .toolbarBackground(.visible, for: .navigationBar)
    .onAppear {
      // Arriving from a "+" elsewhere opens the sheet straight away, but only
      // once: coming back from the sheet must not reopen it.
      guard addOnOpen, !armed else { return }
      armed = true
      adding = true
    }
  }

  private var days: [String] {
    var seen = Set<String>()
    return lines.compactMap { seen.insert($0.spentOn).inserted ? $0.spentOn : nil }
  }

  private func dayRule(_ day: String, currency: String) -> some View {
    let total = lines.filter { $0.spentOn == day }.reduce(Decimal.zero) { $0 + $1.amount }
    return HStack(spacing: 10) {
      MonoLabel(text: tapeDayLabel(day))
      DashedRule().frame(maxWidth: .infinity)
      Text(money(total, currency)).font(.custom(Face.mono, size: 13, relativeTo: .footnote))
        .foregroundStyle(Palette.amber)
    }
    .padding(.top, 20).padding(.bottom, 10)
  }

  /// One printed line: a category dot, the title, the leader, the amount.
  private func tapeLine(_ expense: Expense, currency: String) -> some View {
    VStack(alignment: .leading, spacing: 4) {
      DottedLeaderRow(
        label: expense.title,
        amount: money(expense.amount, currency),
        amountColor: expense.syncState == .failed ? Palette.errorRed : Palette.paper
      ) {
        CategoryDot(color: categoryTint(ranks[expense.category] ?? 4))
      }
      HStack(spacing: 8) {
        MonoLabel(
          text: subtitle(expense), color: Palette.slate, tracking: 1)
        SyncBadge(state: expense.syncState)
      }.padding(.leading, 13)
    }.padding(.vertical, 9)
  }

  private func subtitle(_ expense: Expense) -> String {
    var text = expense.category.label.uppercased()
    if let original = expense.originalCurrency, let amount = expense.originalAmount {
      text += " · \(amount) \(original)"
    }
    return text
  }

  /// Filter pills for category, and the traveller filter when a trip is shared.
  private var filters: some View {
    ScrollView(.horizontal, showsIndicators: false) {
      HStack(spacing: 8) {
        FilterPill(label: "All \(entries.count)", selected: store.tapeCategory.isEmpty) {
          store.tapeCategory = ""
        }
        ForEach(WayfareCore.Category.allCases, id: \.self) { category in
          FilterPill(label: category.label, selected: store.tapeCategory == category.rawValue) {
            store.tapeCategory = category.rawValue
          }
        }
        let members = store.members.filter { $0.tripId == tripId }
        if members.count > 1 {
          Menu {
            Picker("", selection: $payer) {
              Text("Everyone").tag("")
              ForEach(members) { Text(store.name(for: $0.userId)).tag($0.userId) }
            }
          } label: {
            Text(payer.isEmpty ? "Everyone" : store.name(for: payer))
              .typeStyle(.bodySmall).fontWeight(payer.isEmpty ? .regular : .semibold)
              .foregroundStyle(payer.isEmpty ? Palette.steel : Palette.amber)
              .padding(.horizontal, 14).frame(height: 34)
              .background(
                payer.isEmpty ? .clear : Palette.amber.opacity(0.20),
                in: RoundedRectangle(cornerRadius: Radius.chip)
              )
              .overlay {
                if payer.isEmpty {
                  RoundedRectangle(cornerRadius: Radius.chip)
                    .stroke(Palette.outline, lineWidth: 1)
                }
              }
          }
        }
      }.padding(.horizontal, 24)
    }
  }

  private func open(_ expense: Expense) {
    if expense.syncState != .synced {
      unsynced = expense
    } else if expense.userId == store.userId {
      editing = expense
    } else {
      readOnly = expense
    }
  }
}
