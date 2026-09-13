import { useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { AnimatePresence, motion } from "motion/react";
import { ArrowLeft, FunnelSimple, MagnifyingGlass, PencilSimple, Plus, Trash, UsersThree } from "@phosphor-icons/react";
import { Barcode, Postmark, Ticket } from "../components/Ticket";
import { BudgetMeter } from "../components/BudgetMeter";
import { CountUp } from "../components/CountUp";
import { CategorySplit } from "../components/CategorySplit";
import { ExpenseRow } from "../components/ExpenseRow";
import { ExpenseSheet } from "../components/ExpenseSheet";
import { Sheet } from "../components/Sheet";
import { Button } from "../components/Button";
import { IconButton, IconGroup, IconGroupDivider } from "../components/Brand";
import { ShareSheet } from "../components/ShareSheet";
import { TripSheet } from "../components/TripSheet";
import { Field, Select } from "../components/Field";
import { CATEGORY_LIST } from "../lib/categories";
import {
  EmptyState,
  ErrorNote,
  ExpenseRowSkeleton,
  RouteArt,
  Shimmer,
} from "../components/States";
import { useExpenses } from "../hooks/useExpenses";
import { useMembers } from "../hooks/useMembers";
import { useTrip } from "../hooks/useTrips";
import { useAuth } from "../hooks/useAuth";
import {
  dateRange,
  dayLabel,
  money,
  parseDay,
  symbolFor,
  tripCode,
  tripPhase,
} from "../lib/format";
import { cx, hash } from "../lib/cx";
import type { Expense } from "../lib/types";

const MONTHS = ["JAN","FEB","MAR","APR","MAY","JUN","JUL","AUG","SEP","OCT","NOV","DEC"];

/** Below this many entries, search/filter controls are more clutter than help. */
const FILTER_THRESHOLD = 6;

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
  // Below the threshold the controls are hidden, so their state is inert —
  // guarding here too means a trip that shrinks under it (an expense deleted)
  // can't end up silently filtering with no visible way to clear it.
  const filtersEnabled = expenses.length >= FILTER_THRESHOLD;
  const filteredExpenses = useMemo(() => !filtersEnabled ? expenses : expenses.filter((expense) =>
    (!categoryFilter || expense.category === categoryFilter) &&
    (!payerFilter || expense.user_id === payerFilter) &&
    `${expense.title} ${expense.note ?? ""}`.toLowerCase().includes(search.trim().toLowerCase()),
  ), [expenses, filtersEnabled, categoryFilter, payerFilter, search]);
  const days = useMemo(() => groupByDay(filteredExpenses), [filteredExpenses]);
  const hasFilters = filtersEnabled && !!(search || categoryFilter || payerFilter);

  if (tripError) {
    return (
      <main className="mx-auto max-w-[560px] px-5 pt-6">
        <TopBar onBack={() => navigate("/")} />
        <div className="mt-8">
          <ErrorNote message={tripError} />
          <Link
            to="/"
            className="press mt-4 inline-block text-[14px] font-medium text-clay underline underline-offset-4"
          >
            Back to all trips
          </Link>
        </div>
      </main>
    );
  }

  if (tripLoading || expensesLoading || !trip) {
    return (
      <main className="mx-auto max-w-[560px] px-5 pt-6">
        <TopBar onBack={() => navigate("/")} />
        <Shimmer className="mt-6 h-[232px] rounded-ticket" />
        <div className="mt-8 flex flex-col">
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
  const over = trip.budget > 0 && remaining < 0;
  const elapsed = phase.kind === "active" ? phase.day : phase.kind === "past" ? tripLength(trip) : 0;
  const perDay = elapsed > 0 ? spent / elapsed : null;
  const daysLeft = phase.kind === "active" && trip.end_date ? phase.total - phase.day + 1 : null;
  const availablePerDay = trip.budget > 0 && daysLeft ? Math.max(0, remaining) / daysLeft : null;
  const showRemaining = phase.kind === "active" && trip.budget > 0;

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
    <main className="grain mx-auto max-w-[560px] px-5 pb-32 pt-6">
      <TopBar
        onBack={() => navigate("/")}
        onAdd={openNew}
        onShare={() => setShareOpen(true)}
        onEdit={isOwner ? () => setTripSheetOpen(true) : undefined}
        onDelete={isOwner ? () => setConfirmOpen(true) : undefined}
      />

      <motion.div
        initial={{ opacity: 0, y: 14 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ type: "spring", stiffness: 130, damping: 20 }}
        className="mt-6"
      >
        <Ticket
          stub={
            <div className="flex items-stretch divide-x divide-line">
              <Stat
                label={over ? "Over budget" : "Remaining"}
                // With no budget there is nothing to be left of.
                value={
                  trip.budget > 0
                    ? `${symbol}${money(Math.abs(remaining), { cents: false })}`
                    : "—"
                }
                tone={over ? "warn" : "normal"}
              />
              <Stat label="Avg. spent/day" value={perDay !== null ? `${symbol}${money(perDay, { cents: false })}` : "—"} />
              <Stat label="Entries" value={String(expenses.length)} />
            </div>
          }
        >
          <div className="flex flex-col gap-5 p-5">
            <div className="flex items-start justify-between gap-4">
              <div className="min-w-0 flex-1">
                <p className="text-xs font-medium text-ink-soft">
                  {phaseLabel(phase)}
                </p>
                <h1 className="mt-1.5 font-display text-[27px] font-semibold leading-tight tracking-tight text-ink">
                  {trip.name}
                </h1>
                <p className="mt-0.5 truncate text-[13px] text-ink-soft">
                  {trip.destination ? `${trip.destination} · ` : ""}
                  {dateRange(trip.start_date, trip.end_date)}
                </p>
              </div>
              <Postmark
                code={tripCode(trip)}
                caption={
                  trip.start_date
                    ? `${MONTHS[parseDay(trip.start_date).getMonth()]} ${parseDay(trip.start_date).getFullYear()}`
                    : "OPEN"
                }
              />
            </div>

            <div>
              <p className="mb-2 text-sm font-medium text-ink-soft">{showRemaining ? over ? "Over budget" : "Budget remaining" : "Total spent"}</p>
              <p className="font-display text-[40px] font-semibold leading-none tracking-tight text-ink">
                <span className="text-ink-faint">{symbol.trim()}</span>
                <CountUp value={showRemaining ? Math.abs(remaining) : spent} />
              </p>
              <p className="mt-2 text-[13.5px] text-ink-soft">
                {trip.budget > 0 ? (
                  <>
                    {showRemaining ? `${symbol}${money(spent)} spent of ` : "of "}
                    <span className="tabular font-medium text-ink">
                      {symbol}
                      {money(trip.budget, { cents: false })}
                    </span>{" "}
                    budgeted
                    {over && <span className="text-clay-deep"> · over by {symbol}{money(-remaining)}</span>}
                  </>
                ) : (
                  "No budget set — just keeping count."
                )}
              </p>
              {availablePerDay !== null && (
                <div className="mt-4 border-t border-line pt-4">
                  <p className="text-base font-medium text-ink"><span className="tabular">{symbol}{money(availablePerDay)}</span> available/day remaining</p>
                  <p className="mt-1 text-xs leading-relaxed text-ink-soft">Across {daysLeft} {daysLeft === 1 ? "day" : "days"}, including today. Average spent/day includes pre-trip bookings.</p>
                </div>
              )}
              {isShared && <p className="mt-3 text-sm leading-relaxed text-ink-soft">Group budget · includes everyone’s expenses. This ledger tracks spending, not who owes whom.</p>}
            </div>

            <BudgetMeter spent={spent} budget={trip.budget} showLabel />
            <Barcode seed={hash(trip.id)} className="opacity-45" />
          </div>
        </Ticket>
      </motion.div>

      {expenses.length > 0 && (
        <details className="mt-8 rounded-2xl border border-line p-4">
          <summary className="cursor-pointer py-2 text-sm font-medium">Spending by category</summary>
          <div className="mt-3"><CategorySplit expenses={expenses} currency={trip.currency} onSelect={(category) => {
            setCategoryFilter(category);
            setFiltersOpen(true);
            document.getElementById("ledger")?.scrollIntoView({ block: "start" });
          }} /></div>
        </details>
      )}

      <section id="ledger" className="mt-8 scroll-mt-6">
        <div className="flex min-h-11 items-center justify-between gap-3">
          <h2 className="text-base font-semibold text-ink">
            The ledger
          </h2>
          {filtersEnabled && (
            <div className="flex items-center gap-1">
              <button
                type="button"
                aria-label={searchOpen ? "Hide search" : "Show search"}
                aria-controls="ledger-search"
                aria-expanded={searchOpen}
                onClick={() => setSearchOpen((open) => !open)}
                className={cx(
                  "press grid size-11 place-items-center rounded-xl text-ink-soft hover:bg-paper-deep hover:text-ink",
                  (searchOpen || search) && "bg-clay-wash text-clay-deep",
                )}
              >
                <MagnifyingGlass size={19} weight={search ? "bold" : "regular"} />
              </button>
              <button
                type="button"
                aria-label={filtersOpen ? "Hide filters" : "Show filters"}
                aria-controls="ledger-filters"
                aria-expanded={filtersOpen}
                onClick={() => setFiltersOpen((open) => !open)}
                className={cx(
                  "press grid size-11 place-items-center rounded-xl text-ink-soft hover:bg-paper-deep hover:text-ink",
                  (filtersOpen || categoryFilter || payerFilter) && "bg-clay-wash text-clay-deep",
                )}
              >
                <FunnelSimple size={19} weight={categoryFilter || payerFilter ? "fill" : "regular"} />
              </button>
            </div>
          )}
        </div>
        {filtersEnabled && (
          <div className={cx((searchOpen || filtersOpen || hasFilters) && "mt-3", "space-y-3")}>
            {searchOpen && (
              <div id="ledger-search">
                <Field label="Search expenses" type="search" placeholder="Search titles and notes" value={search} onChange={(e) => setSearch(e.target.value)} />
              </div>
            )}
            {filtersOpen && (
              <div id="ledger-filters" className={cx("grid gap-3", isShared && "grid-cols-2")}>
                <Select label="Category" value={categoryFilter} onChange={(e) => setCategoryFilter(e.target.value)}>
                  <option value="">All categories</option>
                  {CATEGORY_LIST.map((category) => <option key={category.key} value={category.key}>{category.label}</option>)}
                </Select>
                {isShared && <Select label="Paid by" value={payerFilter} onChange={(e) => setPayerFilter(e.target.value)}>
                  <option value="">Everyone</option>
                  {membership.members.map((member) => <option key={member.user_id} value={member.user_id}>{member.is_you ? "You" : member.display_name ?? "Traveller"}</option>)}
                </Select>}
              </div>
            )}
            {hasFilters && <div className="flex items-center justify-between text-sm text-ink-soft">
              <p role="status">{filteredExpenses.length} matching {filteredExpenses.length === 1 ? "expense" : "expenses"}</p>
              <button className="press min-h-11 text-clay underline underline-offset-4" onClick={() => { setSearch(""); setCategoryFilter(""); setPayerFilter(""); }}>Clear filters</button>
            </div>}
            {hasFilters && filteredExpenses.length === 0 && <p className="py-4 text-sm text-ink-soft">No expenses match. Try another search or clear your filters.</p>}
          </div>
        )}

        {expensesError && (
          <div className="mt-4">
            <ErrorNote message={expensesError} onRetry={() => void reload()} />
          </div>
        )}

        {expensesLoading ? (
          <div className="mt-2 flex flex-col">
            <ExpenseRowSkeleton />
            <ExpenseRowSkeleton />
            <ExpenseRowSkeleton />
          </div>
        ) : expenses.length === 0 && !expensesError ? (
          <EmptyState
            art={<RouteArt />}
            title="Nothing logged yet"
            body="Add the first cost — a flight, the deposit on the room, the airport coffee. It all counts."
            action={
              <Button variant="accent" onClick={openNew}>
                Add an expense
              </Button>
            }
          />
        ) : (
          <div className="mt-3 flex flex-col gap-7">
            {days.map(({ date, items, total }) => (
              <div key={date}>
                <div className="flex items-baseline justify-between border-b border-line pb-2">
                  <span className="text-[12.5px] font-medium tracking-[-0.01em] text-ink-soft">
                    {dayLabel(date)}
                  </span>
                  <span className="tabular text-[12.5px] text-ink-faint">
                    {symbol}
                    {money(total)}
                  </span>
                </div>
                <ul>
                  <AnimatePresence initial={false}>
                    {items.map((expense) => (
                      <ExpenseRow
                        key={expense.id}
                        expense={expense}
                        currency={trip.currency}
                        payer={isShared ? payerNames.get(expense.user_id) ?? "Someone" : null}
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
        eyebrow="Careful"
        title={`Delete ${trip.name}?`}
      >
        <p className="text-[14px] leading-relaxed text-ink-soft">
          This removes the trip and all{" "}
          <span className="tabular font-medium text-ink">{expenses.length}</span> entries on it.
          There's no undo.
        </p>
        <div className="mt-6 flex gap-3">
          <Button variant="quiet" full disabled={deleting} onClick={() => setConfirmOpen(false)}>
            Keep it
          </Button>
          <Button variant="accent" full loading={deleting} onClick={() => void confirmDelete()}>
            Delete trip
          </Button>
        </div>
      </Sheet>
      <div className="fixed inset-x-0 bottom-0 z-20 mx-auto max-w-[560px] border-t border-line bg-paper/95 px-5 pt-3 safe-b backdrop-blur-sm">
        <Button variant="accent" full onClick={openNew}><Plus size={18} weight="bold" />Add expense</Button>
      </div>
    </main>
  );
}

function TopBar({
  onBack,
  onAdd,
  onShare,
  onEdit,
  onDelete,
}: {
  onBack: () => void;
  onAdd?: () => void;
  onShare?: () => void;
  onEdit?: () => void;
  onDelete?: () => void;
}) {
  return (
    <header className="flex items-center gap-2.5">
      <IconButton label="Back to all trips" onClick={onBack}>
        <ArrowLeft size={16} weight="bold" />
      </IconButton>

      <div className="flex-1" />

      {onAdd && (
        <div className="hidden sm:block"><Button size="sm" variant="accent" onClick={onAdd}>
          <Plus size={15} weight="bold" />
          Add expense
        </Button></div>
      )}
      {(onShare || onEdit || onDelete) && (
        <IconGroup>
          {onShare && (
            <IconButton flush label="Share this trip" onClick={onShare}>
              <UsersThree size={16} weight="bold" />
            </IconButton>
          )}
          {onShare && (onEdit || onDelete) && <IconGroupDivider />}
          {onEdit && (
            <IconButton flush label="Edit trip details" onClick={onEdit}>
              <PencilSimple size={16} weight="bold" />
            </IconButton>
          )}
          {onEdit && onDelete && <IconGroupDivider />}
          {onDelete && (
            <IconButton flush label="Delete this trip" onClick={onDelete} tone="danger">
              <Trash size={16} weight="bold" />
            </IconButton>
          )}
        </IconGroup>
      )}
    </header>
  );
}

/** "Rosalind Mbeki" -> "Rosalind"; the ledger only has room for one word. */
function firstName(name: string | null) {
  return name?.trim().split(/\s+/)[0] ?? "Someone";
}

function Stat({
  label,
  value,
  tone = "normal",
}: {
  label: string;
  value: string;
  tone?: "normal" | "warn";
}) {
  return (
    <div className="min-w-0 flex-1 px-3 py-3.5">
      <p className="text-xs font-medium text-ink-soft">{label}</p>
      <p
        className={`tabular mt-1 text-[15px] font-medium ${
          tone === "warn" ? "text-clay-deep" : "text-ink"
        }`}
      >
        {value}
      </p>
    </div>
  );
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
    .map(([date, items]) => ({
      date,
      items,
      total: items.reduce((sum, e) => sum + e.amount, 0),
    }));
}

function tripLength(trip: { start_date: string | null; end_date: string | null }) {
  if (!trip.start_date || !trip.end_date) return 0;
  const start = parseDay(trip.start_date).getTime();
  const end = parseDay(trip.end_date).getTime();
  return Math.round((end - start) / 86_400_000) + 1;
}

function phaseLabel(phase: ReturnType<typeof tripPhase>) {
  switch (phase.kind) {
    case "active":
      return `Day ${phase.day} of ${phase.total} · in motion`;
    case "upcoming":
      return phase.days === 1 ? "Departs tomorrow" : `Departs in ${phase.days} days`;
    case "past":
      return "Wrapped";
    default:
      return "Open ended";
  }
}
