import { AirplaneTakeoff } from "@phosphor-icons/react";
import { cx } from "../lib/cx";

/** The accent wordmark from the native top bars. */
export function Brand() {
  return (
    <span className="inline-flex items-center gap-2 text-accent-ink">
      <AirplaneTakeoff size={22} weight="bold" aria-hidden />
      <span className="type-headline">wayfare</span>
    </span>
  );
}

/**
 * The circular icon button that recurs throughout the system: back, share,
 * options. Always 50%, never any other geometry. An `active` toggle turns
 * white with an ink ring, the same switch to ink a focused field makes.
 */
export function IconButton({
  label,
  onClick,
  active,
  children,
  ...rest
}: {
  label: string;
  onClick?: () => void;
  active?: boolean;
  children: React.ReactNode;
} & Omit<React.ButtonHTMLAttributes<HTMLButtonElement>, "onClick" | "children">) {
  return (
    <button
      type="button"
      {...rest}
      onClick={onClick}
      aria-label={label}
      title={label}
      className={cx(
        "press grid size-11 shrink-0 place-items-center rounded-full text-ink",
        active ? "bg-canvas ring-[1.5px] ring-ink ring-inset" : "bg-cloud hover:bg-hairline/60",
      )}
    >
      {children}
    </button>
  );
}
