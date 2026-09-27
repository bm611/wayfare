import { useState } from "react";
import { animate, motion, useMotionValue } from "motion/react";
import { Trash } from "@phosphor-icons/react";
import { CATEGORIES } from "../lib/categories";
import { money, symbolFor } from "../lib/format";
import type { Expense } from "../lib/types";

/** How far a row must slide before letting go reveals the delete action. */
const REVEAL_WIDTH = 76;
const SPRING = { type: "spring", stiffness: 420, damping: 40 } as const;

export function ExpenseRow({
  expense,
  currency,
  canDelete = false,
  onSelect,
  onDelete,
}: {
  expense: Expense;
  currency: string;
  /** Only the person who logged an entry may swipe it away. */
  canDelete?: boolean;
  onSelect: (expense: Expense) => void;
  onDelete?: (id: string) => Promise<unknown>;
}) {
  const meta = CATEGORIES[expense.category] ?? CATEGORIES.other;
  const Icon = meta.icon;
  const x = useMotionValue(0);
  const [revealed, setRevealed] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [failed, setFailed] = useState(false);

  // What it cost in the currency it was paid in, and any note. The category
  // already shows as the glyph, so it is not repeated in words.
  const detail = [
    expense.original_currency && expense.original_amount
      ? `${symbolFor(expense.original_currency)}${money(expense.original_amount)}`
      : null,
    expense.note?.trim(),
  ]
    .filter(Boolean)
    .join(" · ");

  function close() {
    animate(x, 0, SPRING);
    setRevealed(false);
  }

  function handleDragEnd(_: unknown, info: { offset: { x: number } }) {
    const open = info.offset.x < -REVEAL_WIDTH / 2;
    animate(x, open ? -REVEAL_WIDTH : 0, SPRING);
    setRevealed(open);
  }

  async function handleDelete(e: React.MouseEvent) {
    e.stopPropagation();
    if (!onDelete || deleting) return;
    setDeleting(true);
    setFailed(false);
    try {
      await onDelete(expense.id);
      // Row is gone once the parent's list updates; nothing left to reset here.
    } catch {
      setDeleting(false);
      setFailed(true);
      close();
    }
  }

  return (
    <motion.li
      layout
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, x: -18, transition: { duration: 0.18 } }}
      transition={{ type: "spring", stiffness: 220, damping: 26 }}
      className="relative overflow-hidden"
    >
      {canDelete && (
        <button
          type="button"
          onClick={handleDelete}
          disabled={deleting}
          aria-label={`Delete ${expense.title}`}
          className="press absolute inset-y-0 right-0 flex w-[76px] items-center justify-center rounded-l-card bg-error text-canvas disabled:opacity-70"
        >
          <Trash size={18} weight="bold" />
        </button>
      )}

      <motion.button
        style={{ x }}
        drag={canDelete ? "x" : false}
        dragConstraints={{ left: -REVEAL_WIDTH, right: 0 }}
        dragElastic={0.06}
        onDragEnd={handleDragEnd}
        onClick={() => (revealed ? close() : onSelect(expense))}
        className="press relative z-10 flex w-full items-center gap-3 bg-canvas py-1.5 text-left"
      >
        {/* A circular glyph where an avatar would sit. */}
        <span className="grid size-10 shrink-0 place-items-center rounded-full bg-cloud text-ink" aria-hidden>
          <Icon size={18} />
        </span>

        <span className="min-w-0 flex-1">
          <span className="block truncate type-title text-ink">{expense.title}</span>
          {(failed || detail) && (
            <span className={`block truncate type-body ${failed ? "text-error" : "text-ash"}`}>
              {failed ? "Couldn't delete — try again" : detail}
            </span>
          )}
        </span>

        <span className="tabular shrink-0 type-title text-ink">
          {symbolFor(currency)}
          {money(expense.amount)}
        </span>
      </motion.button>
    </motion.li>
  );
}
