import { useEffect, useMemo, useState } from "react";
import { Trash } from "@phosphor-icons/react";
import { Sheet } from "./Sheet";
import { Field, Select, TextArea } from "./Field";
import { Button } from "./Button";
import { ErrorNote } from "./States";
import { CATEGORY_LIST } from "../lib/categories";
import { money, symbolFor, todayISO } from "../lib/format";
import { CURRENCIES, convert, rateBetween } from "../lib/fx";
import { useRates } from "../hooks/useRates";
import type { CategoryKey, Expense, ExpenseInput } from "../lib/types";

type Draft = Omit<ExpenseInput, "trip_id">;

export function ExpenseSheet({
  open,
  onClose,
  currency,
  editing,
  onCreate,
  onUpdate,
  onDelete,
}: {
  open: boolean;
  onClose: () => void;
  /** The trip's currency — everything is stored converted into this. */
  currency: string;
  editing: Expense | null;
  onCreate: (input: Draft) => Promise<unknown>;
  onUpdate: (id: string, patch: Partial<Draft>) => Promise<unknown>;
  onDelete: (id: string) => Promise<unknown>;
}) {
  const { rates, stale } = useRates();

  const [title, setTitle] = useState("");
  const [amount, setAmount] = useState("");
  const [paidIn, setPaidIn] = useState(currency);
  const [category, setCategory] = useState<CategoryKey>("food");
  const [spentOn, setSpentOn] = useState(todayISO());
  const [note, setNote] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [failure, setFailure] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  // Reload the draft whenever the sheet opens on a different row.
  useEffect(() => {
    if (!open) return;
    setTitle(editing?.title ?? "");
    setAmount(editing ? String(editing.original_amount ?? editing.amount) : "");
    setPaidIn(editing?.original_currency ?? currency);
    setCategory(editing?.category ?? "food");
    setSpentOn(editing?.spent_on ?? todayISO());
    setNote(editing?.note ?? "");
    setErrors({});
    setFailure(null);
    setConfirmDelete(false);
  }, [open, editing, currency]);

  const foreign = paidIn !== currency;
  const parsed = Number(amount);
  const preview = useMemo(() => {
    if (!foreign || !amount || Number.isNaN(parsed) || parsed <= 0) return null;
    const converted = convert(parsed, paidIn, currency, rates);
    const rate = rateBetween(paidIn, currency, rates);
    if (converted === null || rate === null) return null;
    return { converted, rate };
  }, [foreign, amount, parsed, paidIn, currency, rates]);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const next: Record<string, string> = {};
    if (!title.trim()) next.title = "What was it for?";
    if (!amount || Number.isNaN(parsed) || parsed <= 0) next.amount = "Enter an amount above zero.";
    if (foreign && rateBetween(paidIn, currency, rates) === null) {
      next.amount = `No rate available for ${paidIn} to ${currency}.`;
    }
    setErrors(next);
    if (Object.keys(next).length) return;

    const rate = foreign ? rateBetween(paidIn, currency, rates) : null;
    const base = foreign ? convert(parsed, paidIn, currency, rates)! : Math.round(parsed * 100) / 100;

    const draft: Draft = {
      title: title.trim(),
      amount: base,
      original_amount: foreign ? Math.round(parsed * 100) / 100 : null,
      original_currency: foreign ? paidIn : null,
      fx_rate: rate,
      category,
      spent_on: spentOn || todayISO(),
      note: note.trim() || null,
    };

    setSaving(true);
    setFailure(null);
    try {
      if (editing) await onUpdate(editing.id, draft);
      else await onCreate(draft);
      onClose();
    } catch (err) {
      setFailure(err instanceof Error ? err.message : "Could not save that expense.");
    } finally {
      setSaving(false);
    }
  }

  async function remove() {
    if (!editing) return;
    setSaving(true);
    try {
      await onDelete(editing.id);
      onClose();
    } catch (err) {
      setFailure(err instanceof Error ? err.message : "Could not delete that expense.");
      setSaving(false);
    }
  }

  return (
    <Sheet
      open={open}
      onClose={onClose}
      eyebrow={editing ? "Edit entry" : "New entry"}
      title={editing ? "Fix the details" : "What did it cost?"}
    >
      <form onSubmit={submit} className="flex flex-col gap-5">
        {failure && <ErrorNote message={failure} />}

        <div className="grid grid-cols-[1fr_112px] gap-3">
          <Field
            label="Amount"
            type="number"
            inputMode="decimal"
            step="0.01"
            min="0"
            placeholder="0.00"
            prefix={symbolFor(paidIn).trim()}
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            error={errors.amount}
            autoFocus={!editing}
            className="tabular text-[19px] font-medium"
          />
          <Select label="Paid in" value={paidIn} onChange={(e) => setPaidIn(e.target.value)}>
            {CURRENCIES.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </Select>
        </div>

        {preview && (
          <div className="-mt-2 flex items-baseline justify-between rounded-xl border border-line bg-paper px-3.5 py-2.5">
            <span className="tabular text-[15px] font-medium text-ink">
              = {symbolFor(currency)}
              {money(preview.converted)}
            </span>
            <span className="tabular text-[11px] text-ink-faint">
              1 {paidIn} = {preview.rate.toFixed(4)} {currency}
              {stale ? " · offline rate" : ""}
            </span>
          </div>
        )}

        <Field
          label="For"
          placeholder="Tram tickets to Belém"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          error={errors.title}
          maxLength={120}
        />

        <fieldset className="flex flex-col gap-2.5">
          <legend className="mb-0.5 text-[11px] font-medium uppercase tracking-[0.14em] text-ink-soft">
            Category
          </legend>
          <div className="flex flex-wrap gap-2">
            {CATEGORY_LIST.map((meta) => {
              const Icon = meta.icon;
              const active = category === meta.key;
              return (
                <button
                  key={meta.key}
                  type="button"
                  onClick={() => setCategory(meta.key)}
                  aria-pressed={active}
                  className="press inline-flex items-center gap-1.5 rounded-full border px-3 py-2 text-[13px] font-medium"
                  style={{
                    backgroundColor: active ? meta.wash : "transparent",
                    borderColor: active ? meta.color : "var(--color-line)",
                    color: active ? meta.color : "var(--color-ink-soft)",
                  }}
                >
                  <Icon size={15} weight="bold" />
                  {meta.label}
                </button>
              );
            })}
          </div>
        </fieldset>

        <Field
          label="Date"
          type="date"
          value={spentOn}
          onChange={(e) => setSpentOn(e.target.value)}
        />

        <TextArea
          label="Note"
          placeholder="Split three ways"
          value={note}
          onChange={(e) => setNote(e.target.value)}
          maxLength={500}
        />

        <Button type="submit" variant="accent" full loading={saving}>
          {editing ? "Save changes" : "Add to the ledger"}
        </Button>

        {editing &&
          (confirmDelete ? (
            <div className="flex items-center gap-2.5 rounded-2xl border border-clay/30 bg-clay-wash/50 p-2.5">
              <p className="flex-1 pl-1.5 text-[13px] text-clay-deep">Delete this entry?</p>
              <Button type="button" variant="quiet" onClick={() => setConfirmDelete(false)} className="h-10 px-3.5">
                Keep
              </Button>
              <Button type="button" variant="accent" onClick={remove} className="h-10 px-3.5">
                Delete
              </Button>
            </div>
          ) : (
            <Button type="button" variant="danger" full onClick={() => setConfirmDelete(true)}>
              <Trash size={16} weight="bold" />
              Delete entry
            </Button>
          ))}
      </form>
    </Sheet>
  );
}
