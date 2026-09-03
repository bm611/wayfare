import { useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { AnimatePresence, motion } from "motion/react";
import { ArrowLeft, Plus, Trash, UsersThree } from "@phosphor-icons/react";
import { Barcode, Postmark, Ticket } from "../components/Ticket";
import { BudgetMeter } from "../components/BudgetMeter";
import { CountUp } from "../components/CountUp";
import { CategorySplit } from "../components/CategorySplit";
import { ExpenseRow } from "../components/ExpenseRow";
import { ExpenseSheet } from "../components/ExpenseSheet";
import { Sheet } from "../components/Sheet";
import { Button } from "../components/Button";
import { IconButton } from "../components/Brand";
import { ShareSheet } from "../components/ShareSheet";
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
import { hash } from "../lib/cx";
import type { Expense } from "../lib/types";

const MONTHS = ["JAN","FEB","MAR","APR","MAY","JUN","JUL","AUG","SEP","OCT","NOV","DEC"];

export function TripDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const { trip, loading: tripLoading, error: tripError, removeTrip } = useTrip(id);
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
  const [deleting, setDeleting] = useState(false);

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
  const days = useMemo(() => groupByDay(expenses), [expenses]);

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

  if (tripLoading || !trip) {
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
                label="Left"
                // With no budget there is nothing to be left of.
                value={
                  trip.budget > 0
                    ? `${over ? "−" : ""}${symbol}${money(Math.abs(remaining), { cents: false })}`
                    : "—"
                }
                tone={over ? "warn" : "normal"}
              />
              <Stat label="Per day" value={perDay !== null ? `${symbol}${money(perDay, { cents: false })}` : "—"} />
              <Stat label="Entries" value={String(expenses.length)} />
            </div>
          }
        >
          <div className="flex flex-col gap-5 p-5">
            <div className="flex items-start justify-between gap-4">
              <div className="min-w-0 flex-1">
                <p className="tabular text-[10px] uppercase tracking-[0.22em] text-ink-faint">
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
              <p className="font-display text-[40px] font-semibold leading-none tracking-tight text-ink">
                <span className="text-ink-faint">{symbol.trim()}</span>
                <CountUp value={spent} />
              </p>
              <p className="mt-2 text-[13.5px] text-ink-soft">
                {trip.budget > 0 ? (
                  <>
                    of{" "}
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
            </div>

            <BudgetMeter spent={spent} budget={trip.budget} />
            <Barcode seed={hash(trip.id)} className="opacity-45" />
          </div>
        </Ticket>
      </motion.div>

      {expenses.length > 0 && (
        <div className="mt-10">
          <CategorySplit expenses={expenses} currency={trip.currency} />
        </div>
      )}

      <section className="mt-10">
        <h2 className="tabular text-[10.5px] uppercase tracking-[0.22em] text-ink-faint">
          The ledger
        </h2>

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

      <Sheet
        open={confirmOpen}
        onClose={() => setConfirmOpen(false)}
        eyebrow="Careful"
        title={`Delete ${trip.name}?`}
      >
        <p className="text-[14px] leading-relaxed text-ink-soft">
          This removes the trip and all{" "}
          <span className="tabular font-medium text-ink">{expenses.length}</span> entries on it.
          There's no undo.
        </p>
        <div className="mt-6 flex gap-3">
          <Button variant="quiet" full onClick={() => setConfirmOpen(false)}>
            Keep it
          </Button>
          <Button variant="accent" full loading={deleting} onClick={() => void confirmDelete()}>
            Delete trip
          </Button>
        </div>
      </Sheet>
    </main>
  );
}

function TopBar({
  onBack,
  onAdd,
  onShare,
  onDelete,
}: {
  onBack: () => void;
  onAdd?: () => void;
  onShare?: () => void;
  onDelete?: () => void;
}) {
  return (
    <header className="flex items-center gap-2">
      <IconButton label="Back to all trips" onClick={onBack}>
        <ArrowLeft size={16} weight="bold" />
      </IconButton>

      <div className="flex-1" />

      {onAdd && (
        <Button variant="accent" onClick={onAdd} className="h-9 gap-1.5 px-3.5 text-[14px]">
          <Plus size={15} weight="bold" />
          Add expense
        </Button>
      )}
      {onShare && (
        <IconButton label="Share this trip" onClick={onShare}>
          <UsersThree size={16} weight="bold" />
        </IconButton>
      )}
      {onDelete && (
        <IconButton label="Delete this trip" onClick={onDelete} tone="danger">
          <Trash size={16} weight="bold" />
        </IconButton>
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
    <div className="flex-1 px-4 py-3.5">
      <p className="tabular text-[9.5px] uppercase tracking-[0.18em] text-ink-faint">{label}</p>
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
  return Math.floor((end - start) / 86_400_000) + 1;
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
