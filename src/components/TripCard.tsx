import { useState } from "react";
import { Link } from "react-router-dom";
import { motion } from "motion/react";
import { ArrowUpRight, UsersThree } from "@phosphor-icons/react";
import { money, symbolFor, tripPhase } from "../lib/format";
import { cx } from "../lib/cx";
import { coverUrl } from "../lib/covers";
import type { TripWithSpend } from "../hooks/useTrips";

export function TripCard({ trip, shared = false }: { trip: TripWithSpend; shared?: boolean }) {
  const phase = tripPhase(trip);
  const symbol = symbolFor(trip.currency);
  const cover = trip.cover_status === "ready" ? coverUrl(trip.cover_path) : null;
  const [paintedSrc, setPaintedSrc] = useState<string | null>(null);
  const developing = trip.cover_status === "pending";
  // Print covers carry the place name in the artwork, so the card's own title
  // would only repeat it.
  const isPrint = cover !== null && trip.cover_path?.endsWith("-print.jpg") === true;
  const place = trip.destination?.trim() || trip.name;
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
        aria-label={`${place}, ${phaseLabel(phase)}, ${spent}`}
        className="press group block"
      >
        {/* The whole card is the cover, in the 3:2 frame it is generated at. */}
        <div className="relative z-10 rounded-ticket border border-line bg-card p-1 shadow-lift">
          <div
            className={cx(
              "relative aspect-[3/2] overflow-hidden rounded-[calc(var(--radius-ticket)-5px)] bg-paper",
              developing && "developing",
            )}
          >
            {cover && (
              <img
                src={cover}
                alt=""
                decoding="async"
                onLoad={() => setPaintedSrc(cover)}
                className={cx(
                  "size-full object-cover transition-opacity duration-700 ease-out",
                  paintedSrc === cover ? "opacity-100" : "opacity-0",
                )}
              />
            )}
            <span className="absolute top-3 left-3 flex items-center gap-1.5 rounded-xl bg-card px-2.5 py-1.5 text-[11px] font-medium text-ink shadow-sm">
              {phase.kind === "active" && (
                <span className="beacon size-1.5 rounded-full bg-clay" aria-hidden />
              )}
              {phaseLabel(phase)}
            </span>
          </div>
        </div>

        {/* The numbers ride in a drawer tucked under the cover rather than on
            it. Only covers without lettering need the place named in words. */}
        <div className="mx-3.5 -mt-3 flex items-center gap-3 rounded-b-[20px] border border-line bg-line-soft px-5 pt-6 pb-3.5 text-[15px] text-ink-soft">
          <p className="tabular flex min-w-0 flex-1 items-center gap-1.5 truncate">
            {developing ? (
              "Creating your cover…"
            ) : (
              <span className="truncate">
                {!isPrint && `${place} · `}
                <span className="font-bold text-clay-deep">
                  {symbol}
                  {money(trip.spent)}
                </span>{" "}
                spent
              </span>
            )}
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
