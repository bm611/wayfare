import type { ButtonHTMLAttributes, ReactNode } from "react";
import { cx } from "../lib/cx";

type Variant = "solid" | "accent" | "quiet" | "danger" | "google";
type Size = "md" | "sm";

/* Mirrors the native controls: the accent fill for the one primary action on a
   surface, white behind a hairline for everything else. `solid` is kept as an
   alias of the accent so there is still only one primary look. */
const VARIANTS: Record<Variant, string> = {
  solid: "bg-accent text-on-accent border-transparent hover:brightness-[0.97]",
  accent: "bg-accent text-on-accent border-transparent hover:brightness-[0.97]",
  quiet: "bg-canvas text-ink border-hairline hover:bg-cloud",
  danger: "bg-canvas text-error border-hairline hover:bg-cloud",
  /* Google requires its mark on a neutral surface. */
  google: "bg-canvas text-ink border-hairline hover:bg-cloud",
};

/* Heights live here rather than in a caller's className: same-property utilities
   can't be overridden from outside. */
const SIZES: Record<Size, string> = {
  md: "min-h-12 gap-2 px-5",
  sm: "min-h-11 gap-1.5 px-3.5",
};

export function Button({
  variant = "accent",
  size = "md",
  loading = false,
  full = false,
  pill = false,
  children,
  className,
  disabled,
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: Variant;
  size?: Size;
  loading?: boolean;
  full?: boolean;
  /** The rounder panel radius, for buttons that float over content. */
  pill?: boolean;
  children: ReactNode;
}) {
  return (
    <button
      {...rest}
      disabled={disabled || loading}
      className={cx(
        "press inline-flex items-center justify-center border type-body-lg",
        pill ? "rounded-panel" : "rounded-control",
        "disabled:pointer-events-none",
        SIZES[size],
        // Busy keeps its colour; only a truly unavailable action greys out.
        disabled && !loading ? "border-transparent bg-cloud text-stone" : VARIANTS[variant],
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
