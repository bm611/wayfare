import type { ReactNode } from "react";
import { WarningCircle } from "@phosphor-icons/react";
import { Ticket } from "./Ticket";
import { cx } from "../lib/cx";

export function Shimmer({ className }: { className?: string }) {
  return (
    <div
      className={cx(
        "relative overflow-hidden rounded-md bg-line-soft",
        "after:absolute after:inset-0 after:-translate-x-full after:animate-[shimmer_1.6s_infinite]",
        "after:bg-gradient-to-r after:from-transparent after:via-card/70 after:to-transparent",
        className,
      )}
    />
  );
}

/** Matches the real ticket's silhouette so the list never jumps on load. */
export function TripCardSkeleton() {
  return (
    <Ticket
      stub={
        <div className="flex items-center justify-between px-5 py-3.5">
          <Shimmer className="h-5 w-28" />
          <Shimmer className="h-4 w-12" />
        </div>
      }
    >
      <div className="flex flex-col gap-4 p-5">
        <div className="flex items-start justify-between">
          <Shimmer className="h-3 w-24" />
          <Shimmer className="size-11 rounded-lg" />
        </div>
        <div className="flex flex-col gap-2">
          <Shimmer className="h-7 w-40" />
          <Shimmer className="h-3.5 w-32" />
        </div>
        <Shimmer className="h-2.5 w-full rounded-sm" />
        <Shimmer className="h-4 w-36" />
      </div>
    </Ticket>
  );
}

export function ExpenseRowSkeleton() {
  return (
    <div className="flex items-center gap-3 py-3.5">
      <Shimmer className="size-9 rounded-xl" />
      <div className="flex flex-1 flex-col gap-1.5">
        <Shimmer className="h-3.5 w-32" />
        <Shimmer className="h-3 w-20" />
      </div>
      <Shimmer className="h-4 w-16" />
    </div>
  );
}

export function EmptyState({
  art,
  title,
  body,
  action,
}: {
  art: ReactNode;
  title: string;
  body: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center px-6 py-14 text-center">
      <div className="mb-6 text-ink-faint">{art}</div>
      <h3 className="font-display text-[21px] font-semibold tracking-tight text-ink">{title}</h3>
      <p className="mt-2 max-w-[38ch] text-[14px] leading-relaxed text-ink-soft">{body}</p>
      {action && <div className="mt-6">{action}</div>}
    </div>
  );
}

export function ErrorNote({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div
      role="alert"
      className="flex items-start gap-3 rounded-2xl border border-clay/30 bg-clay-wash/60 px-4 py-3.5"
    >
      <WarningCircle size={18} weight="bold" className="mt-px shrink-0 text-clay-deep" />
      <div className="flex-1">
        <p className="text-[13.5px] leading-relaxed text-clay-deep">{message}</p>
        {onRetry && (
          <button
            onClick={onRetry}
            className="press mt-1.5 text-[13px] font-medium text-clay-deep underline underline-offset-4"
          >
            Try again
          </button>
        )}
      </div>
    </div>
  );
}

/** Dotted-route illustration used on empty screens. No stock art, no emoji. */
export function RouteArt() {
  return (
    <svg width="112" height="56" viewBox="0 0 112 56" fill="none" aria-hidden>
      <path
        d="M6 44C24 44 30 12 54 12s32 32 52 32"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeDasharray="4 5"
        strokeLinecap="round"
      />
      <circle cx="6" cy="44" r="3.5" fill="currentColor" />
      <path d="M106 44l-7-4v8l7-4z" fill="var(--color-clay)" />
      <circle cx="54" cy="12" r="5" stroke="var(--color-clay)" strokeWidth="1.5" />
    </svg>
  );
}
