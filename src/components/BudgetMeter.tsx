import { memo } from "react";
import { cx } from "../lib/cx";

const SEGMENTS = 30;

/**
 * A boarding-strip style gauge. Segments reveal via a CSS delay cascade so a
 * long list of trips costs nothing at runtime.
 */
export const BudgetMeter = memo(function BudgetMeter({
  spent,
  budget,
  size = "md",
  onImage = false,
}: {
  spent: number;
  budget: number;
  size?: "sm" | "md";
  /** Over a destination shot the clay fill loses its contrast, so it goes pale. */
  onImage?: boolean;
}) {
  const hasBudget = budget > 0;
  const ratio = hasBudget ? spent / budget : 0;
  const filled = hasBudget ? Math.min(SEGMENTS, Math.round(Math.min(ratio, 1) * SEGMENTS)) : 0;
  const over = hasBudget && ratio > 1;

  return (
    <div
      className={cx("flex w-full gap-[3px]", size === "sm" ? "h-2" : "h-2.5")}
      role="meter"
      aria-valuenow={Math.round(ratio * 100)}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-label="Budget used"
    >
      {Array.from({ length: SEGMENTS }, (_, i) => {
        const isFilled = i < filled;
        return (
          <span
            key={i}
            className={cx(
              "seg flex-1 rounded-[1.5px] transition-colors duration-500",
              isFilled
                ? over
                  ? onImage
                    ? "bg-clay-light"
                    : "bg-clay-deep"
                  : onImage
                    ? "bg-paper/90"
                    : "bg-clay"
                : onImage
                  ? "bg-paper/15"
                  : "bg-line-soft",
            )}
            style={{ "--i": i } as React.CSSProperties}
          />
        );
      })}
    </div>
  );
});
