import { memo } from "react";
import { cx } from "../lib/cx";

/** A compact budget rail with an optional percentage readout on detail views. */
export const BudgetMeter = memo(function BudgetMeter({
  spent,
  budget,
  track = "cloud",
  showLabel = false,
}: {
  spent: number;
  budget: number;
  /** Hairline when the rail sits on a cloud panel, so it still reads. */
  track?: "cloud" | "hairline";
  showLabel?: boolean;
}) {
  if (budget <= 0) return null;

  const ratio = Math.max(0, spent / budget);
  const percentage = Math.round(ratio * 100);

  return (
    <div
      className="w-full"
      role="meter"
      aria-valuenow={Math.min(percentage, 100)}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-label="Budget used"
      aria-valuetext={`${percentage}% of budget used`}
    >
      {showLabel && (
        <div className="mb-2 flex items-baseline justify-between">
          <span className="type-body-sm text-ash">Budget used</span>
          <span className="tabular type-label text-ash">{percentage}%</span>
        </div>
      )}
      <div className={cx("h-1 overflow-hidden rounded-full", track === "hairline" ? "bg-hairline" : "bg-cloud")}>
        <div
          className={cx("h-full origin-left rounded-full transition-transform duration-500", ratio > 1 ? "bg-error" : "bg-accent-ink")}
          style={{ transform: `scaleX(${Math.min(ratio, 1)})` }}
        />
      </div>
    </div>
  );
});
