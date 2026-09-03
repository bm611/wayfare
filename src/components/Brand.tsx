import { PaperPlaneTilt } from "@phosphor-icons/react";

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

/** Small circular icon button used across the top bars. */
export function IconButton({
  label,
  onClick,
  tone = "neutral",
  children,
}: {
  label: string;
  onClick: () => void;
  tone?: "neutral" | "danger";
  children: React.ReactNode;
}) {
  return (
    <button
      onClick={onClick}
      aria-label={label}
      title={label}
      className={`press grid size-9 shrink-0 place-items-center rounded-full border border-line text-ink-soft ${
        tone === "danger"
          ? "hover:border-clay/40 hover:bg-clay-wash hover:text-clay-deep"
          : "hover:bg-paper-deep hover:text-ink"
      }`}
    >
      {children}
    </button>
  );
}
