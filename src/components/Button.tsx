import type { ButtonHTMLAttributes, ReactNode } from "react";
import { cx } from "../lib/cx";

type Variant = "solid" | "accent" | "quiet" | "danger";

const VARIANTS: Record<Variant, string> = {
  solid:
    "bg-ink text-paper border-ink shadow-[inset_0_1px_0_rgb(255_255_255/0.12)] hover:bg-ink/92",
  accent:
    "bg-clay text-[#fff8f4] border-clay-deep shadow-[inset_0_1px_0_rgb(255_255_255/0.18)] hover:bg-clay-deep",
  quiet: "bg-transparent text-ink-soft border-line hover:bg-paper-deep hover:text-ink",
  danger: "bg-transparent text-clay-deep border-clay/35 hover:bg-clay-wash",
};

export function Button({
  variant = "solid",
  loading = false,
  full = false,
  children,
  className,
  disabled,
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: Variant;
  loading?: boolean;
  full?: boolean;
  children: ReactNode;
}) {
  return (
    <button
      {...rest}
      disabled={disabled || loading}
      className={cx(
        "press inline-flex h-12 items-center justify-center gap-2 rounded-2xl border px-5",
        "text-[15px] font-medium tracking-[-0.01em]",
        "disabled:pointer-events-none disabled:opacity-45",
        VARIANTS[variant],
        full && "w-full",
        className,
      )}
    >
      {loading ? <Spinner /> : children}
    </button>
  );
}

function Spinner() {
  return (
    <span className="flex items-center gap-1" aria-label="Working">
      {[0, 1, 2].map((i) => (
        <span
          key={i}
          className="beacon size-1.5 rounded-full bg-current"
          style={{ animationDelay: `${i * 160}ms` }}
        />
      ))}
    </span>
  );
}
