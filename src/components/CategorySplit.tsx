import { CATEGORY_LIST } from "../lib/categories";
import { money, symbolFor } from "../lib/format";
import type { CategoryKey, Expense } from "../lib/types";

/** No cards here — hierarchy comes from a single rule and generous spacing. */
export function CategorySplit({
  expenses,
  currency,
  onSelect,
}: {
  expenses: Expense[];
  currency: string;
  onSelect: (category: CategoryKey) => void;
}) {
  const total = expenses.reduce((sum, e) => sum + e.amount, 0);
  if (total === 0) return null;

  const rows = CATEGORY_LIST.map((meta) => ({
    meta,
    amount: expenses
      .filter((e) => e.category === meta.key)
      .reduce((sum, e) => sum + e.amount, 0),
  }))
    .filter((row) => row.amount > 0)
    .sort((a, b) => b.amount - a.amount);

  const symbol = symbolFor(currency);

  return (
    <section className="flex flex-col gap-4">
      <p className="text-xs text-ink-soft">Choose a category to see its expenses.</p>

      <div className="flex h-3 gap-[3px]">
        {rows.map(({ meta, amount }) => (
          <span
            key={meta.key}
            className="rounded-[2px] transition-[flex-grow] duration-500"
            style={{ flexGrow: amount, flexBasis: 0, backgroundColor: meta.color }}
            title={`${meta.label} · ${symbol}${money(amount)}`}
          />
        ))}
      </div>

      <ul className="divide-y divide-line-soft">
        {rows.map(({ meta, amount }) => (
          <li key={meta.key}>
            <button type="button" onClick={() => onSelect(meta.key)} className="press flex min-h-11 w-full items-center gap-3 py-2.5 text-left hover:bg-paper">
            <span
              className="size-2 shrink-0 rounded-full"
              style={{ backgroundColor: meta.color }}
              aria-hidden
            />
            <span className="flex-1 text-[14px] text-ink">{meta.label}</span>
            <span className="tabular text-[12px] text-ink-faint">
              {Math.round((amount / total) * 100)}%
            </span>
            <span className="tabular w-[92px] text-right text-[14px] font-medium text-ink">
              {symbol}
              {money(amount)}
            </span>
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}
