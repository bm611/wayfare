import { useState } from "react";
import { Link } from "react-router-dom";
import { motion } from "motion/react";
import { CaretRight, UsersThree } from "@phosphor-icons/react";
import { Barcode, Postmark, Ticket } from "./Ticket";
import { BudgetMeter } from "./BudgetMeter";
import { dateRange, money, symbolFor, tripCode, tripPhase, parseDay } from "../lib/format";
import { cx, hash } from "../lib/cx";
import { coverUrl } from "../lib/covers";
import type { TripWithSpend } from "../hooks/useTrips";

const MONTHS = ["JAN","FEB","MAR","APR","MAY","JUN","JUL","AUG","SEP","OCT","NOV","DEC"];

export function TripCard({ trip, shared = false }: { trip: TripWithSpend; shared?: boolean }) {
  const phase = tripPhase(trip);
  const symbol = symbolFor(trip.currency);
  const remaining = trip.budget - trip.spent;
  const over = trip.budget > 0 && remaining < 0;
  const pct = trip.budget > 0 ? Math.round((trip.spent / trip.budget) * 100) : null;

  const cover = trip.cover_status === "ready" ? coverUrl(trip.cover_path) : null;
  const [paintedSrc, setPaintedSrc] = useState<string | null>(null);
  // Type only flips to paper once the photograph is actually on screen —
  // otherwise the card spends a beat with white text on a cream background.
  const onImage = cover !== null && paintedSrc === cover;
  const developing = trip.cover_status === "pending";

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
          {/* Clipped one pixel inside the ticket's own radius so the photograph
              stops exactly at the border rather than poking through the corner. */}
          <div
            className={cx(
              "relative overflow-hidden rounded-t-[calc(var(--radius-ticket)-1px)]",
              developing && "developing",
            )}
          >
            {cover && (
              <div aria-hidden className="absolute inset-0">
                <img
                  src={cover}
                  alt=""
                  decoding="async"
                  onLoad={() => setPaintedSrc(cover)}
                  // The card crops a wide band out of a 16:10 frame. Centred,
                  // that band is mostly empty sky; pulling it down lands it on
                  // the skyline the prompt puts just below the horizon.
                  // Saturation compensates for the scrim washing the colour out.
                  className={cx(
                    "size-full object-cover object-[center_68%] saturate-125 transition-opacity duration-700 ease-out",
                    onImage ? "opacity-100" : "opacity-0",
                  )}
                />
                {/* Two scrims. The flat one is deliberately light — enough to
                    hold the eyebrow and title, not enough to grey the place out.
                    The gradient does the heavy lifting at the bottom, where the
                    numbers sit and a hazy skyline would otherwise swallow them. */}
                <div
                  className={cx(
                    "absolute inset-0 bg-ink/25 transition-opacity duration-700",
                    onImage ? "opacity-100" : "opacity-0",
                  )}
                />
                <div
                  className={cx(
                    "absolute inset-0 bg-gradient-to-t from-ink/90 via-ink/42 to-ink/18 transition-opacity duration-700",
                    onImage ? "opacity-100" : "opacity-0",
                  )}
                />
              </div>
            )}

            <div className="relative flex flex-col gap-4 p-5">
              <div className="flex items-start justify-between gap-4">
                <div className="min-w-0 flex-1">
                  <p
                    className={cx(
                      "tabular flex items-center gap-1.5 text-[10px] uppercase tracking-[0.22em] transition-colors duration-500",
                      onImage ? "text-paper/70" : "text-ink-faint",
                    )}
                  >
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
                  <h3
                    className={cx(
                      "mt-1.5 truncate font-display text-[26px] font-semibold leading-tight tracking-tight transition-colors duration-500",
                      onImage
                        ? "text-paper drop-shadow-[0_1px_12px_rgb(26_23_20_/_0.55)]"
                        : "text-ink",
                    )}
                  >
                    {trip.name}
                  </h3>
                  <p
                    className={cx(
                      "mt-0.5 truncate text-[13px] transition-colors duration-500",
                      onImage ? "text-paper/80" : "text-ink-soft",
                    )}
                  >
                    {trip.destination ? `${trip.destination} · ` : ""}
                    {dateRange(trip.start_date, trip.end_date)}
                  </p>
                </div>
                <Postmark code={tripCode(trip)} caption={stampCaption} onImage={onImage} />
              </div>

              <BudgetMeter spent={trip.spent} budget={trip.budget} onImage={onImage} />

              <div className="flex items-baseline justify-between gap-3">
                <p
                  className={cx(
                    "tabular text-[15px] font-medium transition-colors duration-500",
                    onImage ? "text-paper" : "text-ink",
                  )}
                >
                  {symbol}
                  {money(trip.spent)}
                  <span
                    className={cx(
                      "ml-1.5 text-[13px] font-normal transition-colors duration-500",
                      onImage ? "text-paper/60" : "text-ink-faint",
                    )}
                  >
                    {trip.budget > 0
                      ? `of ${symbol}${money(trip.budget, { cents: false })}`
                      : "no budget set"}
                  </span>
                </p>
                {pct !== null && (
                  <p
                    className={cx(
                      "tabular text-[13px] font-medium transition-colors duration-500",
                      over
                        ? onImage
                          ? "text-clay-light"
                          : "text-clay-deep"
                        : onImage
                          ? "text-paper/80"
                          : "text-ink-soft",
                    )}
                  >
                    {over ? `${symbol}${money(Math.abs(remaining), { cents: false })} over` : `${pct}%`}
                  </p>
                )}
              </div>
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
