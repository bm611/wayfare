import type { ReactNode } from "react";
import { cx } from "../lib/cx";

/**
 * The paper primitive everything sits on: a card with a punched tear line
 * separating the body from a hatched stub. Corners are rounded by hand rather
 * than clipped so the notches can hang over the edges.
 */
export function Ticket({
  children,
  stub,
  className,
}: {
  children: ReactNode;
  stub?: ReactNode;
  className?: string;
}) {
  return (
    <div
      className={cx(
        "relative rounded-ticket border border-line bg-card shadow-lift",
        className,
      )}
    >
      {children}

      {stub !== undefined && (
        <div className="relative">
          <Notch side="left" />
          <Notch side="right" />
          <div className="tear mx-3.5" />
          <div className="hatch rounded-b-[calc(var(--radius-ticket)-1px)]">{stub}</div>
        </div>
      )}
    </div>
  );
}

function Notch({ side }: { side: "left" | "right" }) {
  return (
    <span
      aria-hidden
      className={cx(
        "pointer-events-none absolute top-0 size-[19px] -translate-y-1/2 rounded-full border border-line bg-paper",
        side === "left" ? "-left-[10px]" : "-right-[10px]",
      )}
    />
  );
}

/** Deterministic barcode strip. Same trip, same bars, forever. */
export function Barcode({ seed, className }: { seed: number; className?: string }) {
  const bars: number[] = [];
  let s = seed || 1;
  for (let i = 0; i < 34; i++) {
    s = (s * 1103515245 + 12345) >>> 0;
    bars.push(1 + (s % 3));
  }
  return (
    <div aria-hidden className={cx("flex h-6 items-end gap-[2px]", className)}>
      {bars.map((w, i) => (
        <span
          key={i}
          className="h-full rounded-[1px] bg-ink/70"
          style={{ width: `${w}px`, opacity: w === 1 ? 0.45 : 0.8 }}
        />
      ))}
    </div>
  );
}

/** Rotated visa-style postmark carrying the trip's three letter code. */
export function Postmark({ code, caption }: { code: string; caption: string }) {
  return (
    <div className="-rotate-[7deg] select-none rounded-lg border-[1.5px] border-clay/40 px-2.5 py-1.5 text-center ring-1 ring-inset ring-clay/15">
      <div className="tabular text-[15px] font-semibold leading-none tracking-[0.14em] text-clay/85">
        {code}
      </div>
      <div className="my-1 h-px bg-clay/25" />
      <div className="tabular text-[7.5px] leading-none tracking-[0.2em] text-clay/60">
        {caption}
      </div>
    </div>
  );
}
