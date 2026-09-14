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

  return (
    <motion.li
      variants={{
        hidden: { opacity: 0, y: 18 },
        show: { opacity: 1, y: 0, transition: { type: "spring", stiffness: 130, damping: 20 } },
      }}
    >
      <Link
        to={`/trip/${trip.id}`}
        aria-label={`${place}, ${symbol}${money(trip.spent)} spent`}
        className="press block rounded-ticket border border-line bg-card p-1 shadow-lift"
      >
        {/* The whole card is the cover, in the 8:5 frame it is generated at.
            Controls rest on the empty paper the print leaves along its bottom
            edge, either side of the lettered title. */}
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

          <div className="absolute inset-x-0 bottom-0 flex flex-col gap-0.5 px-4 pb-3 text-ink">
            {!isPrint && (
              <h3 className="truncate font-display text-xl font-semibold leading-tight tracking-tight">
                {place}
              </h3>
            )}
            {developing && <p className="text-xs text-ink-soft">Creating your cover…</p>}
            <div className="flex items-center justify-between gap-3">
              <p className="tabular flex items-center gap-1.5 text-[15px] font-semibold">
                {symbol}
                {money(trip.spent)} spent
                {shared && <UsersThree size={14} weight="bold" aria-label="Shared" />}
              </p>
              <ArrowUpRight size={20} weight="bold" aria-hidden />
            </div>
          </div>
        </div>
      </Link>
    </motion.li>
  );
}
