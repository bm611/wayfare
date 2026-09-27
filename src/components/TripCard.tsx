import { Link } from "react-router-dom";
import { motion } from "motion/react";
import { ArrowUpRight, UsersThree } from "@phosphor-icons/react";
import { dateRange, money, symbolFor, tripPhase } from "../lib/format";
import { Stamp } from "./Stamp";
import type { TripWithSpend } from "../hooks/useTrips";

export function TripCard({ trip, shared = false }: { trip: TripWithSpend; shared?: boolean }) {
  const phase = tripPhase(trip);
  const symbol = symbolFor(trip.currency);
  const place = trip.destination?.trim() || trip.name;
  const dated = trip.start_date || trip.end_date;
  const spent = `${symbol}${money(trip.spent)} spent`;

  return (
    <motion.li
      variants={{
        hidden: { opacity: 0, y: 18 },
        show: { opacity: 1, y: 0, transition: { type: "spring", stiffness: 130, damping: 20 } },
      }}
    >
      <Link
        to={`/trip/${trip.id}`}
        aria-label={`${trip.name}, ${place}, ${phaseLabel(phase)}, ${spent}`}
        className="press group block"
      >
        {/* The stamp names the place, so the card is free to name the trip. */}
        <div className="relative z-10 flex items-center gap-5 rounded-ticket border border-line bg-card py-5 pr-5 pl-4 shadow-lift">
          <Stamp trip={trip} />
          <div className="min-w-0 flex-1">
            <span className="inline-flex items-center gap-1.5 rounded-xl bg-paper px-2.5 py-1.5 text-[11px] font-medium text-ink">
              {phase.kind === "active" && (
                <span className="beacon size-1.5 rounded-full bg-clay" aria-hidden />
              )}
              {phaseLabel(phase)}
            </span>
            <h3 className="mt-3 truncate font-display text-[21px] leading-tight font-semibold tracking-tight text-ink">
              {trip.name}
            </h3>
            <p className="tabular mt-1 truncate text-[14px] text-ink-soft">
              {dated ? dateRange(trip.start_date, trip.end_date) : place}
            </p>
          </div>
        </div>

        {/* The numbers ride in a drawer tucked under the card. */}
        <div className="mx-3.5 -mt-3 flex items-center gap-3 rounded-b-[20px] border border-line bg-line-soft px-5 pt-6 pb-3.5 text-[15px] text-ink-soft">
          <p className="tabular flex min-w-0 flex-1 items-center gap-1.5 truncate">
            <span className="truncate">
              <span className="font-bold text-clay-deep">
                {symbol}
                {money(trip.spent)}
              </span>{" "}
              spent
            </span>
            {shared && <UsersThree size={14} weight="bold" aria-label="Shared" className="shrink-0" />}
          </p>
          <ArrowUpRight size={18} weight="bold" className="text-ink" aria-hidden />
        </div>
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
