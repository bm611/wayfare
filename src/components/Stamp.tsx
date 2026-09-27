import type { CSSProperties } from "react";
import { cx } from "../lib/cx";
import { stampArt, stampMonth, stampStyle } from "../lib/stamp";
import type { Trip } from "../lib/types";

/**
 * A trip's passport stamp: a tinted card with a dashed inner rule, the drawing,
 * the place and the month, all pressed in the tint's own ink. Until the
 * drawing lands it is the same stamp, empty, with its rule breathing while the
 * server draws.
 */
export function Stamp({
  trip,
  className,
}: {
  trip: Pick<Trip, "id" | "name" | "destination" | "start_date" | "end_date" | "cover_art" | "cover_status">;
  className?: string;
}) {
  const { tint, tilt, arched } = stampStyle(trip.id);
  const art = trip.cover_status === "ready" ? stampArt(trip.cover_art) : null;
  // Until the model names it, the place as typed, less any region after a comma.
  const label = art?.label || (trip.destination?.split(",")[0].trim() || trip.name).toLocaleUpperCase();
  // An undated stamp still gets its date line, so it never looks unfinished.
  const date = stampMonth(trip.start_date ?? trip.end_date) ?? "DATES OPEN";

  return (
    <div
      aria-hidden
      className={cx(
        "stamp",
        arched && "stamp-arched",
        trip.cover_status === "pending" && "stamp-developing",
        className,
      )}
      style={{ "--tilt": `${tilt}deg`, "--tint": `var(--stamp-${tint})`, "--ink": `var(--stamp-${tint}-ink)` } as CSSProperties}
    >
      <svg
        className="stamp-mark"
        viewBox="0 0 64 64"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.75"
        strokeLinecap="round"
        strokeLinejoin="round"
        focusable="false"
      >
        {/* Keyed on the drawing so a stamp that arrives while the card is on
            screen inks in, rather than popping. */}
        <g key={art?.paths.join("|") ?? "blank"} className={art ? "stamp-ink" : undefined}>
          {art?.paths.map((d, i) => <path key={i} d={d} />)}
        </g>
      </svg>
      <span className="stamp-label">{label}</span>
      <span className="stamp-date">{date}</span>
    </div>
  );
}
