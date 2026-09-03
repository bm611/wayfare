import { motion } from "motion/react";
import { CATEGORIES } from "../lib/categories";
import { money, symbolFor } from "../lib/format";
import type { Expense } from "../lib/types";

export function ExpenseRow({
  expense,
  currency,
  payer,
  onSelect,
}: {
  expense: Expense;
  currency: string;
  /** Only passed once a trip has more than one traveller on it. */
  payer?: string | null;
  onSelect: (expense: Expense) => void;
}) {
  const meta = CATEGORIES[expense.category] ?? CATEGORIES.other;
  const Icon = meta.icon;

  const detail = [
    payer,
    expense.original_currency && expense.original_amount
      ? `${symbolFor(expense.original_currency)}${money(expense.original_amount)}`
      : null,
    expense.note?.trim() || meta.label,
  ]
    .filter(Boolean)
    .join(" · ");

  return (
    <motion.li
      layout
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, x: -18, transition: { duration: 0.18 } }}
      transition={{ type: "spring", stiffness: 220, damping: 26 }}
      className="border-b border-dashed border-line last:border-b-0"
    >
      <button
        onClick={() => onSelect(expense)}
        className="press flex w-full items-center gap-3.5 py-3.5 text-left"
      >
        <span
          className="grid size-9 shrink-0 place-items-center rounded-xl"
          style={{ backgroundColor: meta.wash, color: meta.color }}
          aria-hidden
        >
          <Icon size={17} weight="bold" />
        </span>

        <span className="min-w-0 flex-1">
          <span className="block truncate text-[15px] font-medium tracking-[-0.01em] text-ink">
            {expense.title}
          </span>
          <span className="block truncate text-[12.5px] text-ink-faint">{detail}</span>
        </span>

        <span className="tabular shrink-0 text-[15px] font-medium text-ink">
          {symbolFor(currency)}
          {money(expense.amount)}
        </span>
      </button>
    </motion.li>
  );
}
