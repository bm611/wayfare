import { Link } from "react-router-dom";
import { motion } from "motion/react";
import { CaretRight, UsersThree } from "@phosphor-icons/react";
import { Barcode, Postmark, Ticket } from "./Ticket";
import { BudgetMeter } from "./BudgetMeter";
import { dateRange, money, symbolFor, tripCode, tripPhase, parseDay } from "../lib/format";
import { hash } from "../lib/cx";
import type { TripWithSpend } from "../hooks/useTrips";

const MONTHS = ["JAN","FEB","MAR","APR","MAY","JUN","JUL","AUG","SEP","OCT","NOV","DEC"];

export function TripCard({ trip, shared = false }: { trip: TripWithSpend; shared?: boolean }) {
  const phase = tripPhase(trip);
  const symbol = symbolFor(trip.currency);
  const remaining = trip.budget - trip.spent;
  const over = trip.budget > 0 && remaining < 0;
  const pct = trip.budget > 0 ? Math.round((trip.spent / trip.budget) * 100) : null;

  const stampCaption = trip.start_date
    ? `${MONTHS[parseDay(trip.start_date).getMonth()]} ${parseDay(trip.start_date).getFullYear()}`
    : "OPEN";

  return (
    <motion.li
      variants={{
        hidden: { opacity: 0, y: 18 },
        show: { opacity: 1, y: 0, transition: { type: "spring", stiffness: 130, damping: 20 } },
      }}
    >
      <Link to={`/trip/${trip.id}`} className="press block rounded-ticket">
        <Ticket
          stub={
            <div className="flex items-center gap-3 px-5 py-3.5">
              <Barcode seed={hash(trip.id)} className="flex-1" />
              <div className="flex items-center gap-2">
                {phase.kind === "active" && (
                  <span className="beacon size-1.5 rounded-full bg-clay" aria-hidden />
                )}
                <span className="tabular text-[11px] uppercase tracking-[0.14em] text-ink-soft">
                  {phaseLabel(phase)}
                </span>
                <CaretRight size={13} weight="bold" className="text-ink-faint" />
              </div>
            </div>
          }
        >
          <div className="flex flex-col gap-4 p-5">
            <div className="flex items-start justify-between gap-4">
              <div className="min-w-0 flex-1">
                <p className="tabular flex items-center gap-1.5 text-[10px] uppercase tracking-[0.22em] text-ink-faint">
                  {trip.entries === 0
                    ? "No entries yet"
                    : `${trip.entries} ${trip.entries === 1 ? "entry" : "entries"}`}
                  {shared && (
                    <>
                      <span aria-hidden>·</span>
                      <UsersThree size={12} weight="bold" />
                      <span>Shared</span>
                    </>
                  )}
                </p>
                <h3 className="mt-1.5 truncate font-display text-[26px] font-semibold leading-tight tracking-tight text-ink">
                  {trip.name}
                </h3>
                <p className="mt-0.5 truncate text-[13px] text-ink-soft">
                  {trip.destination ? `${trip.destination} · ` : ""}
                  {dateRange(trip.start_date, trip.end_date)}
                </p>
              </div>
              <Postmark code={tripCode(trip)} caption={stampCaption} />
            </div>

            <BudgetMeter spent={trip.spent} budget={trip.budget} />

            <div className="flex items-baseline justify-between gap-3">
              <p className="tabular text-[15px] font-medium text-ink">
                {symbol}
                {money(trip.spent)}
                <span className="ml-1.5 text-[13px] font-normal text-ink-faint">
                  {trip.budget > 0 ? `of ${symbol}${money(trip.budget, { cents: false })}` : "no budget set"}
                </span>
              </p>
              {pct !== null && (
                <p
                  className={`tabular text-[13px] font-medium ${
                    over ? "text-clay-deep" : "text-ink-soft"
                  }`}
                >
                  {over ? `${symbol}${money(Math.abs(remaining), { cents: false })} over` : `${pct}%`}
                </p>
              )}
            </div>
          </div>
        </Ticket>
      </Link>
    </motion.li>
  );
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
