import { useState } from "react";
import { Link } from "react-router-dom";
import { motion } from "motion/react";
import { CaretRight, UsersThree } from "@phosphor-icons/react";
import { Barcode, Ticket } from "./Ticket";
import { BudgetMeter } from "./BudgetMeter";
import { money, symbolFor, tripPhase } from "../lib/format";
import { cx, hash } from "../lib/cx";
import { coverUrl } from "../lib/covers";
import type { TripWithSpend } from "../hooks/useTrips";

export function TripCard({ trip, shared = false }: { trip: TripWithSpend; shared?: boolean }) {
  const phase = tripPhase(trip);
  const symbol = symbolFor(trip.currency);
  const remaining = trip.budget - trip.spent;
  const over = trip.budget > 0 && remaining < 0;
  const pct = trip.budget > 0 ? Math.round((trip.spent / trip.budget) * 100) : null;

  const cover = trip.cover_status === "ready" ? coverUrl(trip.cover_path) : null;
  const [paintedSrc, setPaintedSrc] = useState<string | null>(null);
  const developing = trip.cover_status === "pending";
  // Print covers carry the place name in the artwork, so the card's own title
  // would only repeat it.
  const isPrint = cover !== null && trip.cover_path?.endsWith("-print.jpg") === true;
  const place = trip.destination?.trim() || trip.name;

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
          {(cover || developing) && (
            // Same 8:5 frame the cover is generated at, so the whole print —
            // lettering included — shows without cropping. Clipped one pixel
            // inside the ticket's radius so it stops exactly at the border.
            <div
              className={cx(
                "relative aspect-[8/5] overflow-hidden rounded-t-[calc(var(--radius-ticket)-1px)] bg-paper",
                developing && "developing",
              )}
            >
              {cover && (
                <img
                  src={cover}
                  alt={isPrint ? place : ""}
                  decoding="async"
                  onLoad={() => setPaintedSrc(cover)}
                  className={cx(
                    "size-full object-cover transition-opacity duration-700 ease-out",
                    paintedSrc === cover ? "opacity-100" : "opacity-0",
                  )}
                />
              )}
            </div>
          )}

          <div className="flex flex-col gap-4 p-5">
            <div className="min-w-0">
              <p className="tabular flex items-center gap-1.5 text-[10px] uppercase tracking-[0.22em] text-ink-faint">
                {developing
                  ? "Creating your cover…"
                  : trip.entries === 0
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
              {!isPrint && (
                <h3 className="mt-1.5 truncate font-display text-[26px] font-semibold leading-tight tracking-tight text-ink">
                  {place}
                </h3>
              )}
            </div>

            <BudgetMeter spent={trip.spent} budget={trip.budget} />

            <div className="flex items-baseline justify-between gap-3">
              <p className="tabular text-[15px] font-medium text-ink">
                {symbol}
                {money(trip.spent)}
                <span className="ml-1.5 text-[13px] font-normal text-ink-faint">
                  {trip.budget > 0
                    ? `of ${symbol}${money(trip.budget, { cents: false })}`
                    : "no budget set"}
                </span>
              </p>
              {pct !== null && (
                <p
                  className={cx(
                    "tabular text-[13px] font-medium",
                    over ? "text-clay-deep" : "text-ink-soft",
                  )}
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
