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
        // Photographs read as distinct objects only if the canvas between them
        // is wide enough to be read as canvas.
        LazyVStack(alignment: .leading, spacing: 24) {
          topNav
          VStack(alignment: .leading, spacing: 6) {
            Text("Your trips").typeStyle(.displaySmall)
            Text(
              store.trips.isEmpty
                ? "Your first journey is waiting."
                : "\(store.trips.count) \(store.trips.count == 1 ? "trip" : "trips"). All your plans, in one place."
            ).typeStyle(.bodyLarge).foregroundStyle(Palette.ash)
          }.padding(.horizontal, 24)
          ViewThatFits(in: .horizontal) {
            HStack(spacing: 12) { tripActions }
            VStack(spacing: 12) { tripActions }
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
              ForEach(trips) { trip in
                NavigationLink(value: trip.id) { listingCard(trip) }
                  .buttonStyle(.plain).padding(.horizontal, 24)
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

  /// Top nav: the Rausch wordmark and circular controls.
  private var topNav: some View {
    HStack(spacing: 8) {
      HStack(spacing: 8) {
        Image(systemName: "airplane.departure").font(.system(size: 20, weight: .medium))
        Text("wayfare").typeStyle(.headlineSmall)
      }.foregroundStyle(Palette.rausch)
      Spacer(minLength: 0)
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

  @ViewBuilder private var tripActions: some View {
    PrimaryButton(title: "New trip", icon: "plus") { newTrip = true }
    SecondaryButton(title: "Join friend") { joining = true }
  }

  private var emptyState: some View {
    VStack(spacing: 0) {
      Image(systemName: "safari").font(.system(size: 28, weight: .light))
        .foregroundStyle(Palette.rausch).frame(width: 64, height: 64)
        .background(Palette.canvas, in: Circle())
      Text("Good trips start here").typeStyle(.titleLarge).padding(.top, 18)
      Text("Add a trip, set a budget, and log each cost as it lands.")
        .typeStyle(.bodyMedium).foregroundStyle(Palette.ash).multilineTextAlignment(.center)
        .padding(.top, 6)
      Button("I have an invite code") { joining = true }
        .typeStyle(.labelMedium).foregroundStyle(Palette.ink).padding(.top, 12)
    }
    .frame(maxWidth: .infinity).padding(32)
    .background(Palette.softCloud, in: RoundedRectangle(cornerRadius: Radius.card))
  }

  /// A photo-led destination card with a native, content-sized overlay.
  private func listingCard(_ trip: Trip) -> some View {
    let expenses = store.expenses.filter { $0.tripId == trip.id }
    let summary = budgetSummary(trip, expenses: expenses)
    let destination = trip.destination?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    let isPrint = trip.coverPath?.hasSuffix("-print.jpg") == true
    // The cover stays untouched apart from the countdown; the numbers ride in a
    // drawer tucked under it. Only covers without lettering need the place
    // named in words.
    return VStack(spacing: 0) {
      TripArtwork(trip: trip, url: store.coverURL(trip.coverPath), aspect: 3 / 2, showStatus: false)
        .accessibilityHidden(true)
        .overlay(alignment: .topLeading) { PhaseBadge(trip: trip).padding(12) }
        .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
        .padding(5)
        .background(Palette.canvas, in: RoundedRectangle(cornerRadius: 31, style: .continuous))
        .shadow(color: .black.opacity(0.08), radius: 12, x: 0, y: 6)
        .zIndex(1)
      HStack(spacing: 12) {
        Group {
          if trip.coverStatus == "pending" {
            Text("Creating your cover…").foregroundColor(Palette.ash)
          } else {
            Text(isPrint ? "" : "\(destination.isEmpty ? trip.name : destination) · ").foregroundColor(Palette.ash)
              + Text(money(summary.spent, trip.currency)).fontWeight(.bold).foregroundColor(Palette.rauschDeep)
              + Text(" spent").foregroundColor(Palette.ash)
          }
        }
        .font(.subheadline).monospacedDigit().lineLimit(1)
        Spacer(minLength: 0)
        Image(systemName: "arrow.up.right").font(.subheadline.weight(.semibold))
          .foregroundStyle(Palette.ink)
      }
      .padding(.horizontal, 18).padding(.top, 26).padding(.bottom, 14)
      .background(Palette.softCloud, in: UnevenRoundedRectangle(bottomLeadingRadius: 20, bottomTrailingRadius: 20, style: .continuous))
      .overlay(UnevenRoundedRectangle(bottomLeadingRadius: 20, bottomTrailingRadius: 20, style: .continuous)
        .stroke(Palette.hairline, lineWidth: 1))
      .padding(.top, -12).padding(.horizontal, 14)
    }
    .foregroundStyle(Palette.ink)
    .accessibilityElement(children: .ignore)
    .accessibilityLabel("\(destination.isEmpty ? trip.name : destination), \(phaseLabel(trip)), \(money(summary.spent, trip.currency)) spent")
    .accessibilityHint("Opens trip details")
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
