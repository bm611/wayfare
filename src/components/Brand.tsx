import { PaperPlaneTilt } from "@phosphor-icons/react";
import { cx } from "../lib/cx";

export function Brand({ size = "md" }: { size?: "md" | "lg" }) {
  return (
    <span className="inline-flex items-center gap-2">
      <span className="grid size-7 shrink-0 place-items-center rounded-lg bg-ink text-paper">
        <PaperPlaneTilt size={14} weight="fill" />
      </span>
      <span
        className={`font-display font-semibold tracking-tight text-ink ${
          size === "lg" ? "text-[20px]" : "text-[17px]"
        }`}
      >
        Wayfare
      </span>
    </span>
  );
}

/**
 * Icon button for the top bars. Standalone it carries its own card surface so it
 * reads as a sibling of the primary button; inside an IconGroup the shell is the
 * group's, so `flush` drops the border and shrinks it to fit the padding.
 */
export function IconButton({
  label,
  onClick,
  tone = "neutral",
  flush = false,
  children,
}: {
  label: string;
  onClick: () => void;
  tone?: "neutral" | "danger";
  flush?: boolean;
  children: React.ReactNode;
}) {
  return (
    <button
      onClick={onClick}
      aria-label={label}
      title={label}
      className={cx(
        "press grid shrink-0 place-items-center text-ink-soft",
        flush
          ? "size-[30px] rounded-xl"
          : "size-9 rounded-2xl border border-line bg-card shadow-[inset_0_1px_0_rgb(255_255_255/0.7)]",
        tone === "danger"
          ? "hover:bg-clay-wash hover:text-clay-deep"
          : "hover:bg-paper-deep hover:text-ink",
      )}
    >
      {children}
    </button>
  );
}

/** Binds the secondary top-bar actions into one segmented pill. */
export function IconGroup({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex h-9 shrink-0 items-center gap-px rounded-2xl border border-line bg-card p-[3px] shadow-[inset_0_1px_0_rgb(255_255_255/0.7)]">
      {children}
    </div>
  );
}

/** Hairline between two buttons sharing an IconGroup. */
export function IconGroupDivider() {
  return <span aria-hidden className="mx-px h-4 w-px shrink-0 bg-line" />;
}
