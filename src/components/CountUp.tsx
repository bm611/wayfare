import { memo, useEffect } from "react";
import { animate, motion, useMotionValue, useTransform } from "motion/react";
import { money } from "../lib/format";

/**
 * Isolated so the number can tick without re-rendering anything around it.
 */
export const CountUp = memo(function CountUp({
  value,
  cents = true,
}: {
  value: number;
  cents?: boolean;
}) {
  const raw = useMotionValue(0);
  const text = useTransform(raw, (v) => money(v, { cents }));

  useEffect(() => {
    const controls = animate(raw, value, {
      duration: 0.85,
      ease: [0.16, 1, 0.3, 1],
    });
    return () => controls.stop();
  }, [value, raw]);

  return <motion.span className="tabular">{text}</motion.span>;
});
