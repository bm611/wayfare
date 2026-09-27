import { useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { AnimatePresence, motion } from "motion/react";
import {
  ArrowLeft,
  CalendarBlank,
  DotsThree,
  FunnelSimple,
  MagnifyingGlass,
  MapPin,
  PencilSimple,
  Plus,
  Trash,
  UsersThree,
  Wallet,
} from "@phosphor-icons/react";
import { BudgetMeter } from "../components/BudgetMeter";
import { CategorySplit } from "../components/CategorySplit";
import { ExpenseRow } from "../components/ExpenseRow";
import { ExpenseSheet } from "../components/ExpenseSheet";
import { Sheet } from "../components/Sheet";
import { Button } from "../components/Button";
import { IconButton } from "../components/Brand";
import { Menu } from "../components/Menu";
import { ShareSheet } from "../components/ShareSheet";
import { TripSheet } from "../components/TripSheet";
import { CATEGORY_LIST } from "../lib/categories";
import { EmptyState, ErrorNote, ExpenseRowSkeleton } from "../components/States";
import { useExpenses } from "../hooks/useExpenses";
import { useMembers } from "../hooks/useMembers";
import { useTrip } from "../hooks/useTrips";
import { useAuth } from "../hooks/useAuth";
import { dateRange, money, phaseLabel, shortDate, symbolFor, tripPhase } from "../lib/format";
import { cx } from "../lib/cx";
import type { Expense } from "../lib/types";


export function TripDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const { trip, loading: tripLoading, error: tripError, updateTrip, removeTrip } = useTrip(id);
  const {
    expenses,
    loading: expensesLoading,
    error: expensesError,
    reload,
    addExpense,
    updateExpense,
    deleteExpense,
  } = useExpenses(id);

  const [sheetOpen, setSheetOpen] = useState(false);
  const [editing, setEditing] = useState<Expense | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [shareOpen, setShareOpen] = useState(false);
  const [tripSheetOpen, setTripSheetOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [search, setSearch] = useState("");
  const [categoryFilter, setCategoryFilter] = useState("");
  const [payerFilter, setPayerFilter] = useState("");
  const [searchOpen, setSearchOpen] = useState(false);
  const [filtersOpen, setFiltersOpen] = useState(false);

  const membership = useMembers(id);
  const isOwner = !!trip && trip.user_id === user?.id;
  const isShared = membership.members.length > 1;
  const payerNames = useMemo(
    () => new Map(membership.members.map((m) => [m.user_id, m.is_you ? "You" : firstName(m.display_name)])),
    [membership.members],
  );

  async function leave() {
    try {
      await membership.leaveTrip();
      navigate("/", { replace: true });
    } catch {
      // The sheet keeps showing; the member list is still accurate.
    }
  }

  const spent = useMemo(() => expenses.reduce((sum, e) => sum + e.amount, 0), [expenses]);
  const filteredExpenses = useMemo(() => expenses.filter((expense) =>
    (!categoryFilter || expense.category === categoryFilter) &&
    (!payerFilter || expense.user_id === payerFilter) &&
    `${expense.title} ${expense.note ?? ""}`.toLowerCase().includes(search.trim().toLowerCase()),
  ), [expenses, categoryFilter, payerFilter, search]);
  const days = useMemo(() => groupByDay(filteredExpenses), [filteredExpenses]);
  const hasFilters = !!(search || categoryFilter || payerFilter);
  // A control stays on screen while it is narrowing the ledger, and hiding it
  // clears it, so a search or filter can never keep working out of sight.
  const showSearch = searchOpen || !!search;
  const showFilters = filtersOpen || !!categoryFilter || !!payerFilter;

  if (tripError) {
    return (
      <main className="mx-auto max-w-[700px] px-6 pt-4">
        <TopBar onBack={() => navigate("/")} />
        <div className="mt-8">
          <ErrorNote message={tripError} />
          <Link to="/" className="press mt-4 inline-block type-label text-ink underline underline-offset-4">
            Back to all trips
          </Link>
        </div>
      </main>
    );
  }

  if (tripLoading || expensesLoading || !trip) {
    return (
      <main className="mx-auto max-w-[700px] px-6 pt-4">
        <TopBar onBack={() => navigate("/")} />
        <div className="mt-6 h-[180px] rounded-card bg-cloud" />
        <div className="mt-8 flex flex-col gap-2">
          <ExpenseRowSkeleton />
          <ExpenseRowSkeleton />
          <ExpenseRowSkeleton />
        </div>
      </main>
    );
  }

  const symbol = symbolFor(trip.currency);
  const phase = tripPhase(trip);
  const remaining = trip.budget - spent;
  const hasBudget = trip.budget > 0;
  const finished = phase.kind === "past";
  const over = hasBudget && remaining < 0;
  // A finished trip has nothing left to spend, so it shows what it cost.
  const showBalance = hasBudget && !finished;
  const daysLeft = phase.kind === "active" && trip.end_date ? phase.total - phase.day + 1 : null;
  const availablePerDay = hasBudget && daysLeft ? Math.max(0, remaining) / daysLeft : null;
  const destination = trip.destination?.trim() ?? "";
  const cash = (value: number) => `${symbol}${money(value)}`;

  function openNew() {
    setEditing(null);
    setSheetOpen(true);
  }

  function openEdit(expense: Expense) {
    setEditing(expense);
    setSheetOpen(true);
  }

  async function confirmDelete() {
    setDeleting(true);
    try {
      await removeTrip();
      navigate("/", { replace: true });
    } catch {
      setDeleting(false);
    }
  }

  return (
    <main className="mx-auto max-w-[700px] px-6 pb-32 pt-4">
      <TopBar
        onBack={() => navigate("/")}
        onShare={() => setShareOpen(true)}
        onEdit={isOwner ? () => setTripSheetOpen(true) : undefined}
        onDelete={isOwner ? () => setConfirmOpen(true) : undefined}
      />

      <motion.div
        initial={{ opacity: 0, y: 14 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ type: "spring", stiffness: 130, damping: 20 }}
        className="mt-4 flex flex-col gap-6"
      >
        {/* Destination leads; a distinct trip name adds context without repeating it. */}
        <header className="flex flex-col gap-2">
          <h1 className="flex items-baseline gap-2 type-headline text-ink">
            {destination && <MapPin size={18} className="shrink-0 translate-y-0.5 text-ash" aria-hidden />}
            {destination || trip.name}
          </h1>
          {destination && destination.toLowerCase() !== trip.name.trim().toLowerCase() && (
            <p className="type-body text-ash">{trip.name}</p>
          )}
          <p className="flex flex-wrap items-center gap-x-3 gap-y-1.5 type-body text-ash">
            <span className="inline-flex items-center gap-1.5">
              <CalendarBlank size={16} aria-hidden />
              {dateRange(trip.start_date, trip.end_date)}
            </span>
            {phase.kind !== "undated" && <span className="type-label">{phaseLabel(phase)}</span>}
          </p>
        </header>

        {/* The booking panel: the one number that matters, then how it got there. */}
        <section className="flex flex-col gap-3 rounded-card bg-cloud p-5">
          <p className="type-body text-ash">
            {showBalance ? (over ? "Over budget" : "Left to spend") : "Total spent"}
          </p>
          <p className={cx("tabular type-display", over ? "text-error" : "text-ink")}>
            {cash(showBalance ? Math.abs(remaining) : spent)}
          </p>
          {hasBudget ? (
            <>
              <p className="tabular type-body text-ash">
                {finished
                  ? `${cash(Math.abs(remaining))} ${over ? "over" : "under"} budget`
                  : `${cash(spent)} spent of ${cash(trip.budget)}`}
              </p>
              <BudgetMeter spent={spent} budget={trip.budget} track="hairline" />
            </>
          ) : isOwner ? (
            <button onClick={() => setTripSheetOpen(true)} className="press min-h-11 self-start type-label text-ink">
              Set a budget
            </button>
          ) : null}
          {!over && availablePerDay !== null && daysLeft !== null && (
            <p className="tabular type-body text-ash">
              {cash(availablePerDay)} available per day · {daysLeft} {daysLeft === 1 ? "day" : "days"} left, including today
            </p>
          )}
          {isShared && (
            <p className="type-body-sm text-ash">
              Group budget · includes everyone’s expenses. This tracks spending, not who owes whom.
            </p>
          )}
        </section>

        {expenses.length > 0 && (
          <section className="flex flex-col gap-3">
            <h2 className="type-headline text-ink">Spending by category</h2>
            <CategorySplit
              expenses={expenses}
              currency={trip.currency}
              onSelect={(category) => {
                setCategoryFilter(category);
                setFiltersOpen(true);
                document.getElementById("ledger")?.scrollIntoView({ block: "start" });
              }}
            />
          </section>
        )}

        <section id="ledger" className="flex scroll-mt-6 flex-col gap-3">
          <div className="flex items-center gap-1">
            <h2 className="flex-1 type-headline text-ink">Expenses</h2>
            <IconButton
              label={showSearch ? "Hide search" : "Show search"}
              aria-controls="ledger-search"
              aria-expanded={showSearch}
              active={showSearch}
              onClick={() => {
                if (showSearch) {
                  setSearchOpen(false);
                  setSearch("");
                } else setSearchOpen(true);
              }}
            >
              <MagnifyingGlass size={18} />
            </IconButton>
            <IconButton
              label={showFilters ? "Hide filters" : "Show filters"}
              aria-controls="ledger-filters"
              aria-expanded={showFilters}
              active={showFilters}
              onClick={() => {
                if (showFilters) {
                  setFiltersOpen(false);
                  setCategoryFilter("");
                  setPayerFilter("");
                } else setFiltersOpen(true);
              }}
            >
              <FunnelSimple size={18} />
            </IconButton>
          </div>

          <AnimatePresence initial={false}>
            {showSearch && (
              <Reveal key="search">
                {/* The search pill: full radius, hairline border, one soft shadow. */}
                <label
                  id="ledger-search"
                  className="flex min-h-12 items-center gap-2.5 rounded-pill border border-hairline bg-canvas px-4 shadow-[0_2px_3px_rgb(0_0_0/0.04)] focus-within:border-ink"
                >
                  <MagnifyingGlass size={16} className="shrink-0 text-ink" aria-hidden />
                  <input
                    type="search"
                    aria-label="Search expenses"
                    placeholder="Search titles and notes"
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                    className="min-w-0 flex-1 bg-transparent type-body text-ink placeholder:text-ash focus:outline-none"
                  />
                </label>
              </Reveal>
            )}
            {showFilters && (
              <Reveal key="filters">
                <div id="ledger-filters" className={cx("grid gap-2", isShared && "grid-cols-2")}>
                  <PillSelect label="Category" value={categoryFilter} onChange={setCategoryFilter}>
                    <option value="">All categories</option>
                    {CATEGORY_LIST.map((category) => (
                      <option key={category.key} value={category.key}>{category.label}</option>
                    ))}
                  </PillSelect>
                  {isShared && (
                    <PillSelect label="Paid by" value={payerFilter} onChange={setPayerFilter}>
                      <option value="">All travellers</option>
                      {membership.members.map((member) => (
                        <option key={member.user_id} value={member.user_id}>
                          {member.is_you ? "You" : member.display_name ?? "Traveller"}
                        </option>
                      ))}
                    </PillSelect>
                  )}
                </div>
              </Reveal>
            )}
          </AnimatePresence>

          {hasFilters && (
            <div className="flex items-center justify-between type-body text-ash">
              <p role="status">
                {filteredExpenses.length} matching {filteredExpenses.length === 1 ? "expense" : "expenses"}
              </p>
              <button
                className="press min-h-11 type-label text-ink underline underline-offset-4"
                onClick={() => {
                  setSearch("");
                  setCategoryFilter("");
                  setPayerFilter("");
                }}
              >
                Clear filters
              </button>
            </div>
          )}

          {expensesError && <ErrorNote message={expensesError} onRetry={() => void reload()} />}

          {filteredExpenses.length === 0 && !expensesError ? (
            <EmptyState
              plain
              icon={<Wallet size={24} />}
              body={expenses.length === 0 ? "No expenses yet. Add the first one when it lands." : "No entries match these filters."}
            />
          ) : (
            <div className="flex flex-col gap-3">
              {days.map(({ date, items }) => (
                <div key={date} className="flex flex-col gap-1">
                  <p className="pt-1 type-label text-ash">{shortDate(date)}</p>
                  <ul className="flex flex-col">
                    <AnimatePresence initial={false}>
                      {items.map((expense) => (
                        <ExpenseRow
                          key={expense.id}
                          expense={expense}
                          currency={trip.currency}
                          canDelete={expense.user_id === user?.id}
                          onDelete={deleteExpense}
                          onSelect={openEdit}
                        />
                      ))}
                    </AnimatePresence>
                  </ul>
                </div>
              ))}
            </div>
          )}
        </section>
      </motion.div>

      <ExpenseSheet
        open={sheetOpen}
        onClose={() => setSheetOpen(false)}
        currency={trip.currency}
        tripId={trip.id}
        readOnly={!!editing && editing.user_id !== user?.id}
        payer={editing ? payerNames.get(editing.user_id) : undefined}
        editing={editing}
        onCreate={addExpense}
        onUpdate={updateExpense}
        onDelete={deleteExpense}
      />

      <ShareSheet
        open={shareOpen}
        onClose={() => setShareOpen(false)}
        tripName={trip.name}
        shareCode={trip.share_code}
        isOwner={isOwner}
        membership={membership}
        onLeave={() => void leave()}
      />

      <TripSheet
        open={tripSheetOpen}
        onClose={() => setTripSheetOpen(false)}
        onSave={updateTrip}
        trip={trip}
      />

      <Sheet
        open={confirmOpen}
        onClose={() => setConfirmOpen(false)}
        busy={deleting}
        title={`Delete ${trip.name}?`}
      >
        <p className="type-body text-ash">
          This removes the trip and all{" "}
          <span className="tabular text-ink">{expenses.length}</span> expenses on it for every traveller.
          It cannot be undone.
        </p>
        <div className="mt-6 flex gap-3">
          <Button variant="quiet" full disabled={deleting} onClick={() => setConfirmOpen(false)}>
            Keep it
          </Button>
          <Button variant="danger" full loading={deleting} onClick={() => void confirmDelete()}>
            Delete trip
          </Button>
        </div>
      </Sheet>

      {/* Floats bottom-right, as on the phones; the page pads its foot so the
          last amount is never hidden under it. */}
      <div className="pointer-events-none fixed inset-x-0 bottom-0 z-20 mx-auto flex max-w-[700px] justify-end px-6 safe-b">
        <Button
          variant="accent"
          onClick={openNew}
          className="pointer-events-auto min-w-[170px] shadow-float"
        >
          <Plus size={18} weight="bold" />
          Add expense
        </Button>
      </div>
    </main>
  );
}

/** A tap-revealed panel: fades and slides in on an ease-out, leaves quicker. */
function Reveal({ children }: { children: React.ReactNode }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: -8 }}
      animate={{ opacity: 1, y: 0, transition: { duration: 0.18, ease: [0.23, 1, 0.32, 1] } }}
      exit={{ opacity: 0, transition: { duration: 0.14 } }}
    >
      {children}
    </motion.div>
  );
}

/** Outlined pill: the system's secondary control, never a filled one. */
function PillSelect({
  label,
  value,
  onChange,
  children,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  children: React.ReactNode;
}) {
  return (
    <select
      aria-label={label}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className="min-h-12 w-full appearance-none rounded-panel border border-hairline bg-canvas bg-[length:12px] bg-[right_1rem_center] bg-no-repeat px-4 pr-9 text-center type-body-lg text-ink focus:border-ink focus:outline-none"
      style={{
        backgroundImage:
          "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 12 12' fill='none' stroke='%236a6a6a' stroke-width='1.5' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M2.5 4.5 6 8l3.5-3.5'/%3E%3C/svg%3E\")",
      }}
    >
      {children}
    </select>
  );
}

function TopBar({
  onBack,
  onShare,
  onEdit,
  onDelete,
}: {
  onBack: () => void;
  onShare?: () => void;
  onEdit?: () => void;
  onDelete?: () => void;
}) {
  const items = [
    onShare && { label: "Travellers & sharing", icon: <UsersThree size={18} />, onSelect: onShare },
    onEdit && { label: "Edit trip", icon: <PencilSimple size={18} />, onSelect: onEdit },
    onDelete && { label: "Delete trip", icon: <Trash size={18} />, onSelect: onDelete, destructive: true },
  ].filter((item) => !!item);
  return (
    <header className="flex items-center gap-2">
      <IconButton label="Back to all trips" onClick={onBack}>
        <ArrowLeft size={18} />
      </IconButton>
      <div className="flex-1" />
      {items.length > 0 && <Menu label="Trip options" icon={<DotsThree size={22} weight="bold" />} items={items} />}
    </header>
  );
}

/** "Rosalind Mbeki" -> "Rosalind"; a read-only entry names its payer by one word. */
function firstName(name: string | null) {
  return name?.trim().split(/\s+/)[0] ?? "Someone";
}

function groupByDay(expenses: Expense[]) {
  const map = new Map<string, Expense[]>();
  for (const expense of expenses) {
    const list = map.get(expense.spent_on) ?? [];
    list.push(expense);
    map.set(expense.spent_on, list);
  }
  return [...map.entries()]
    .sort((a, b) => b[0].localeCompare(a[0]))
    .map(([date, items]) => ({ date, items }));
}
