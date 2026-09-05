import type { ButtonHTMLAttributes, ReactNode } from "react";
import { cx } from "../lib/cx";

type Variant = "solid" | "accent" | "quiet" | "danger";
type Size = "md" | "sm";

const VARIANTS: Record<Variant, string> = {
  solid:
    "bg-ink text-paper border-ink shadow-[inset_0_1px_0_rgb(255_255_255/0.12)] hover:bg-ink/92",
  accent:
    "bg-clay text-[#fff8f4] border-clay-deep shadow-[inset_0_1px_0_rgb(255_255_255/0.18)] hover:bg-clay-deep",
  quiet: "bg-transparent text-ink-soft border-line hover:bg-paper-deep hover:text-ink",
  danger: "bg-transparent text-clay-deep border-clay/35 hover:bg-clay-wash",
};

/* Heights live here rather than in a caller's className: same-property utilities
   can't be overridden from outside, so `h-9` would lose to the base `h-12`. */
const SIZES: Record<Size, string> = {
  md: "h-12 gap-2 px-5 text-[15px]",
  sm: "h-11 gap-1.5 px-3 text-[14px]",
};

export function Button({
  variant = "solid",
  size = "md",
  loading = false,
  full = false,
  children,
  className,
  disabled,
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: Variant;
  size?: Size;
  loading?: boolean;
  full?: boolean;
  children: ReactNode;
}) {
  return (
    <button
      {...rest}
      disabled={disabled || loading}
      className={cx(
        "press inline-flex items-center justify-center rounded-2xl border",
        "font-medium tracking-[-0.01em]",
        "disabled:pointer-events-none disabled:opacity-45",
        SIZES[size],
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
