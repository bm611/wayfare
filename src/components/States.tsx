import type { ReactNode } from "react";
import { cx } from "../lib/cx";

function Bone({ className }: { className?: string }) {
  return <div className={cx("bg-hairline", className)} />;
}

/** The shape of a stamp card: the stamp, then the trip's name and dates. */
export function TripCardSkeleton() {
  return (
    <div
      role="status"
      aria-label="Loading trips"
      className="flex items-center gap-5 rounded-[35px] bg-cloud p-[18px]"
    >
      <Bone className="h-32 w-[104px] shrink-0 rounded-[18px]" />
      <div className="flex flex-1 flex-col gap-3">
        <Bone className="h-5 w-20 rounded-[10px]" />
        <Bone className="h-6 w-full max-w-[180px] rounded-chip" />
        <Bone className="h-4 w-[110px] rounded-chip" />
      </div>
    </div>
  );
}

export function ExpenseRowSkeleton() {
  return (
    <div className="flex items-center gap-3 py-1.5">
      <Bone className="size-10 rounded-full" />
      <div className="flex flex-1 flex-col gap-1.5">
        <Bone className="h-4 w-32 rounded-chip" />
      </div>
      <Bone className="h-4 w-16 rounded-chip" />
    </div>
  );
}

/** A glyph in a circle, a title and a line of guidance, on a cloud panel. */
export function EmptyState({
  icon,
  title,
  body,
  action,
  plain = false,
}: {
  icon: ReactNode;
  title?: string;
  body: string;
  action?: ReactNode;
  /** No panel: for empty states inside a section that already has one. */
  plain?: boolean;
}) {
  return (
    <div
      className={cx(
        "flex flex-col items-center text-center",
        plain ? "px-6 py-9" : "rounded-card bg-cloud p-8",
      )}
    >
      <div
        className={cx(
          "grid place-items-center rounded-full",
          plain ? "size-14 bg-cloud text-ash" : "size-16 bg-canvas text-accent-ink",
        )}
        aria-hidden
      >
        {icon}
      </div>
      {title && <h3 className="mt-[18px] type-title-lg text-ink">{title}</h3>}
      <p className={cx("max-w-[38ch] type-body text-ash", title ? "mt-1.5" : "mt-3.5")}>{body}</p>
      {action && <div className="mt-3">{action}</div>}
    </div>
  );
}

/** An error notice: canvas behind an error-red hairline, error-red text. */
export function ErrorNote({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div role="alert" className="rounded-control border border-error bg-canvas p-3.5">
      <p className="type-body text-error">{message}</p>
      {onRetry && (
        <button
          onClick={onRetry}
          className="press mt-1.5 min-h-11 type-label text-ink underline underline-offset-4"
        >
          Try again
        </button>
      )}
    </div>
  );
}
