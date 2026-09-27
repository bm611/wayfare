import { Link } from "react-router-dom";
import { motion } from "motion/react";
import { ArrowRight, Plus } from "@phosphor-icons/react";
import { dateRange, money, phaseLabel, symbolFor, tripPhase } from "../lib/format";
import { BudgetMeter } from "./BudgetMeter";
import { Stamp } from "./Stamp";
import type { TripWithSpend } from "../hooks/useTrips";

const rise = {
  hidden: { opacity: 0, y: 18 },
  show: { opacity: 1, y: 0, transition: { type: "spring" as const, stiffness: 130, damping: 20 } },
};

/**
 * A trip card led by its passport stamp. The stamp names the place, so the
 * card names the trip, when it runs, and what it has cost so far.
 */
export function TripCard({ trip, travellers = 1 }: { trip: TripWithSpend; travellers?: number }) {
  const phase = tripPhase(trip);
  const counting = phase.kind === "active" || phase.kind === "upcoming";
  // An undated trip's section and stamp already say so; don't say it a third time.
  const meta = [
    trip.start_date || trip.end_date ? dateRange(trip.start_date, trip.end_date) : null,
    travellers > 1 ? `${travellers} travellers` : null,
  ]
    .filter(Boolean)
    .join(" · ");
  const spend = spendText(trip);

  return (
    <motion.li variants={rise}>
      <Link
        to={`/trip/${trip.id}`}
        aria-label={`${trip.name}, ${phaseLabel(phase)}, ${meta ? `${meta}, ` : ""}${spend}`}
        className="press flex items-center gap-5 rounded-listing border border-hairline bg-canvas py-5 pr-5 pl-4 shadow-lift"
      >
        <Stamp trip={trip} />
        <div className="min-w-0 flex-1">
          {/* Past and undated trips sit under a section title that already
              says so; only a countdown earns the pill. */}
          {counting && (
            <span className="mb-3 inline-block rounded-card bg-cloud px-2.5 py-1.5 type-label-sm text-ink">
              {phaseLabel(phase)}
            </span>
          )}
          <h3 className="truncate type-title-lg text-ink">{trip.name}</h3>
          {meta && <p className="tabular mt-1 truncate type-body text-ash">{meta}</p>}
          {/* Spend against budget with a rail when there is both, otherwise
              whichever one number there is. */}
          <div className="mt-3.5">
            {trip.budget > 0 && trip.spent > 0 ? (
              <>
                <p className="tabular mb-2 truncate type-label text-ink">
                  {short(trip, trip.spent)} <span className="text-ash">of {short(trip, trip.budget)}</span>
                </p>
                <BudgetMeter spent={trip.spent} budget={trip.budget} />
              </>
            ) : (
              <p className={`tabular truncate type-label ${trip.spent > 0 ? "text-ink" : "text-ash"}`}>{spend}</p>
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
        aria-label={`${trip.name}, ${spendText(trip)}`}
        className="press flex flex-col items-center gap-3 rounded-panel py-4"
      >
        <Stamp trip={trip} />
        <span className="flex max-w-full flex-col items-center gap-0.5">
          <span className="max-w-full truncate type-title text-ink">{trip.name}</span>
          <span className="tabular type-body-sm text-ash">
            {trip.spent > 0 ? short(trip, trip.spent) : "No expenses"}
          </span>
        </span>
      </Link>
    </motion.li>
  );
}

/**
 * The way into a new trip, drawn as the empty slot the next stamp will fill: a
 * dashed, tilted stamp outline holding the plus, on a wash of the accent.
 */
export function NewTripButton({ onClick }: { onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label="New trip"
      className="press flex w-full items-center gap-4 rounded-panel border-[1.5px] border-dashed border-accent-ink/30 bg-accent-wash px-4 py-3.5 text-left"
    >
      <span
        aria-hidden
        className="grid size-[52px] shrink-0 -rotate-3 place-items-center rounded-[12px] border-[1.5px] border-dashed border-accent-ink/50 text-accent-ink"
      >
        <Plus size={20} weight="bold" />
      </span>
      <span className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span className="type-title text-ink">New trip</span>
        <span className="type-body text-ash">Start your next stamp</span>
      </span>
      <ArrowRight size={18} weight="bold" className="shrink-0 text-accent-ink" aria-hidden />
    </button>
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
