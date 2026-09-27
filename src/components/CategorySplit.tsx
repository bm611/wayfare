import { CATEGORY_LIST } from "../lib/categories";
import { money, symbolFor } from "../lib/format";
import type { CategoryKey, Expense } from "../lib/types";

/**
 * A horizontal bar chart: each category's glyph, label, share and amount above
 * a bar sized to its share of the total. Bars use the one accent. Choosing a
 * row filters the ledger to it.
 */
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
    amount: expenses.filter((e) => e.category === meta.key).reduce((sum, e) => sum + e.amount, 0),
  }))
    .filter((row) => row.amount > 0)
    .sort((a, b) => b.amount - a.amount);

  const symbol = symbolFor(currency);

  return (
    <ul className="flex flex-col gap-1">
      {rows.map(({ meta, amount }) => {
        const Icon = meta.icon;
        const share = amount / total;
        return (
          <li key={meta.key}>
            <button
              type="button"
              onClick={() => onSelect(meta.key)}
              aria-label={`${meta.label}, ${Math.round(share * 100)}%, ${symbol}${money(amount)}. Show these expenses`}
              className="press flex w-full flex-col gap-2 py-2 text-left"
            >
              <span className="flex w-full items-center gap-3">
                <Icon size={20} className="w-6 shrink-0 text-ink" aria-hidden />
                <span className="flex-1 type-body-lg text-ink">{meta.label}</span>
                <span className="type-body text-ash">{Math.round(share * 100)}%</span>
                <span className="tabular type-label text-ink">
                  {symbol}
                  {money(amount)}
                </span>
              </span>
              <span className="h-2 w-full overflow-hidden rounded-full bg-cloud" aria-hidden>
                <span
                  className="block h-full min-w-2 rounded-full bg-accent-ink transition-[width] duration-500"
                  style={{ width: `${share * 100}%` }}
                />
              </span>
            </button>
          </li>
        );
      })}
    </ul>
  );
}
