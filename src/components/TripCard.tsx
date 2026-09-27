import { Link } from "react-router-dom";
import { motion } from "motion/react";
import { UsersThree } from "@phosphor-icons/react";
import { dateRange, money, symbolFor, tripPhase } from "../lib/format";
import { BudgetMeter } from "./BudgetMeter";
import { Stamp } from "./Stamp";
import type { TripWithSpend } from "../hooks/useTrips";

const rise = {
  hidden: { opacity: 0, y: 18 },
  show: { opacity: 1, y: 0, transition: { type: "spring" as const, stiffness: 130, damping: 20 } },
};

export function TripCard({ trip, shared = false }: { trip: TripWithSpend; shared?: boolean }) {
  const phase = tripPhase(trip);
  const counting = phase.kind === "active" || phase.kind === "upcoming";
  const place = trip.destination?.trim() || trip.name;
  // An undated trip's section and stamp already say so; don't say it a third time.
  const dated = trip.start_date || trip.end_date;
  const spend = spendText(trip);

  return (
    <motion.li variants={rise}>
      <Link
        to={`/trip/${trip.id}`}
        aria-label={`${trip.name}, ${place}, ${phaseLabel(phase)}, ${spend}`}
        className="press group flex items-center gap-5 rounded-ticket border border-line bg-card py-5 pr-5 pl-4 shadow-lift"
      >
        {/* The stamp names the place, so the card is free to name the trip. */}
        <Stamp trip={trip} />
        <div className="min-w-0 flex-1">
          {/* Past and undated trips sit under a section title that already
              says so; only a countdown earns the pill. */}
          {counting && (
            <span className="mb-3 inline-flex items-center gap-1.5 rounded-xl bg-paper px-2.5 py-1.5 text-[11px] font-medium text-ink">
              {phase.kind === "active" && (
                <span className="beacon size-1.5 rounded-full bg-clay" aria-hidden />
              )}
              {phaseLabel(phase)}
            </span>
          )}
          <h3 className="truncate font-display text-[21px] leading-tight font-semibold tracking-tight text-ink">
            {trip.name}
          </h3>
          {(dated || shared) && (
            <p className="tabular mt-1 flex items-center gap-1.5 truncate text-[14px] text-ink-soft">
              {dated && <span className="truncate">{dateRange(trip.start_date, trip.end_date)}</span>}
              {shared && <UsersThree size={14} weight="bold" aria-label="Shared" className="shrink-0" />}
            </p>
          )}
          {/* Spend against budget with a rail when there is both, otherwise
              whichever one number there is. */}
          <div className="mt-3.5">
            {trip.budget > 0 && trip.spent > 0 ? (
              <>
                <p className="tabular mb-2 truncate text-[14px] font-semibold text-ink">
                  {short(trip, trip.spent)} <span className="font-medium text-ink-soft">of {short(trip, trip.budget)}</span>
                </p>
                <BudgetMeter spent={trip.spent} budget={trip.budget} />
              </>
            ) : (
              <p className={`tabular truncate text-[14px] font-semibold ${trip.spent > 0 ? "text-ink" : "text-ink-soft"}`}>
                {spend}
              </p>
            )}
          </div>
        </div>
      </Link>
    </motion.li>
  );
}

/** A finished trip as a bare stamp on the passport page, its total underneath. */
export function StampTile({ trip }: { trip: TripWithSpend }) {
  return (
    <motion.li variants={rise}>
      <Link
        to={`/trip/${trip.id}`}
        aria-label={`${trip.name}, ${trip.destination?.trim() || trip.name}, ${spendText(trip)}`}
        className="press flex flex-col items-center gap-3 rounded-ticket py-4"
      >
        <Stamp trip={trip} />
        <span className="flex max-w-full flex-col items-center gap-0.5">
          <span className="max-w-full truncate font-display text-[16px] font-semibold tracking-tight text-ink">
            {trip.name}
          </span>
          <span className="tabular text-[13px] text-ink-soft">
            {trip.spent > 0 ? short(trip, trip.spent) : "No expenses"}
          </span>
        </span>
      </Link>
    </motion.li>
  );
}

/** Whole units for glanceable totals; cents belong on the trip itself. */
function short(trip: TripWithSpend, value: number) {
  return `${symbolFor(trip.currency)}${money(value, { cents: false })}`;
}

function spendText(trip: TripWithSpend) {
  if (trip.spent > 0) {
    return trip.budget > 0 ? `${short(trip, trip.spent)} of ${short(trip, trip.budget)}` : `${short(trip, trip.spent)} spent`;
  }
  return trip.budget > 0 ? `${short(trip, trip.budget)} budget` : "No expenses yet";
}

function phaseLabel(phase: ReturnType<typeof tripPhase>) {
  switch (phase.kind) {
    case "active":
      return `Day ${phase.day} of ${phase.total}`;
    case "upcoming":
      return phase.days === 1 ? "Tomorrow" : `In ${phase.days} days`;
    case "past":
      return "Wrapped";
    default:
      return "Open ended";
  }
}
