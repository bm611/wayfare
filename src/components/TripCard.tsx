import { useState } from "react";
import { Link } from "react-router-dom";
import { motion } from "motion/react";
import { ArrowUpRight, UsersThree } from "@phosphor-icons/react";
import { money, symbolFor } from "../lib/format";
import { cx } from "../lib/cx";
import { coverUrl } from "../lib/covers";
import type { TripWithSpend } from "../hooks/useTrips";

export function TripCard({ trip, shared = false }: { trip: TripWithSpend; shared?: boolean }) {
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
        aria-label={`${place}, ${spent}`}
        className="press group block"
      >
        {/* The whole card is the cover, in the 8:5 frame it is generated at. */}
        <div className="relative z-10 rounded-ticket border border-line bg-card p-1 shadow-lift">
          <div
            className={cx(
              "relative aspect-[8/5] overflow-hidden rounded-[calc(var(--radius-ticket)-5px)] bg-paper",
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
          </div>
        </div>

        {/* The numbers ride in a drawer tucked under the cover rather than on
            it. Only covers without lettering need the place named in words. */}
        <div className="mx-4 -mt-3 flex items-center gap-2 rounded-b-2xl border border-line bg-line-soft px-4 pt-5 pb-2.5 text-[13px] text-ink-soft">
          <p className="tabular flex min-w-0 flex-1 items-center gap-1.5 truncate">
            {developing ? "Creating your cover…" : isPrint ? spent : `${place} · ${spent}`}
            {shared && <UsersThree size={13} weight="bold" aria-label="Shared" className="shrink-0" />}
          </p>
          <ArrowUpRight size={14} weight="bold" aria-hidden />
        </div>
      </Link>
    </motion.li>
  );
}
