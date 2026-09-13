import { memo } from "react";
import { cx } from "../lib/cx";

export const BudgetMeter = memo(function BudgetMeter({
  spent,
  budget,
  onImage = false,
  showLabel = false,
}: {
  spent: number;
  budget: number;
  /** Over a destination shot the clay fill loses its contrast, so it goes pale. */
  onImage?: boolean;
  showLabel?: boolean;
}) {
  if (budget <= 0) return null;

  const ratio = Math.max(0, spent / budget);
  const cappedRatio = Math.min(ratio, 1);
  const percentage = Math.round(ratio * 100);
  const over = ratio > 1;

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
        <div className={cx("mb-2 flex items-baseline justify-between text-xs font-medium", onImage ? "text-paper/80" : "text-ink-soft")}>
          <span>Budget used</span>
          <span className="tabular">{percentage}%</span>
        </div>
      )}
      <div className={cx("h-2 overflow-hidden rounded-full", onImage ? "bg-paper/20" : "bg-line-soft")}>
        <div
          className={cx(
            "h-full origin-left rounded-full transition-transform duration-500",
            over ? (onImage ? "bg-clay-light" : "bg-clay-deep") : (onImage ? "bg-paper/90" : "bg-clay"),
          )}
          style={{ transform: `scaleX(${cappedRatio})` }}
        />
      </div>
    </div>
  );
});
