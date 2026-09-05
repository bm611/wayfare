import { useEffect, useId, useMemo, useState } from "react";
import { Trash } from "@phosphor-icons/react";
import { Sheet } from "./Sheet";
import { Field, Select, TextArea } from "./Field";
import { Button } from "./Button";
import { ErrorNote } from "./States";
import { CATEGORY_LIST } from "../lib/categories";
import { errorMessage } from "../lib/errors";
import { money, symbolFor, todayISO } from "../lib/format";
import { CURRENCIES, convert, rateBetween } from "../lib/fx";
import { useRates } from "../hooks/useRates";
import type { CategoryKey, Expense, ExpenseInput } from "../lib/types";

type Draft = Omit<ExpenseInput, "trip_id">;

export function ExpenseSheet({
  open,
  onClose,
  currency,
  tripId,
  readOnly = false,
  payer,
  editing,
  onCreate,
  onUpdate,
  onDelete,
}: {
  open: boolean;
  onClose: () => void;
  /** The trip's currency — everything is stored converted into this. */
  currency: string;
  tripId: string;
  readOnly?: boolean;
  payer?: string;
  editing: Expense | null;
  onCreate: (input: Draft) => Promise<unknown>;
  onUpdate: (id: string, patch: Partial<Draft>) => Promise<unknown>;
  onDelete: (id: string) => Promise<unknown>;
}) {
  const { rates, stale } = useRates();
  const formId = useId();

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
  const [initialDraft, setInitialDraft] = useState("");
  const [moreDetails, setMoreDetails] = useState(false);

  const draftKey = JSON.stringify([title, amount, paidIn, category, spentOn, note]);

  // Reload the draft whenever the sheet opens on a different row.
  useEffect(() => {
    if (!open) return;
    let remembered = currency;
    try {
      const stored = localStorage.getItem(`wayfare.paidIn.${tripId}`);
      if (stored && CURRENCIES.includes(stored)) remembered = stored;
    } catch { /* Currency preference is optional when storage is unavailable. */ }
    setTitle(editing?.title ?? "");
    setAmount(editing ? String(editing.original_amount ?? editing.amount) : "");
    const initialCurrency = editing ? editing.original_currency ?? currency : remembered;
    setPaidIn(initialCurrency);
    setCategory(editing?.category ?? "food");
    setSpentOn(editing?.spent_on ?? todayISO());
    setNote(editing?.note ?? "");
    setInitialDraft(JSON.stringify([
      editing?.title ?? "", editing ? String(editing.original_amount ?? editing.amount) : "",
      initialCurrency, editing?.category ?? "food", editing?.spent_on ?? todayISO(), editing?.note ?? "",
    ]));
    setMoreDetails(!!editing?.note);
    setSaving(false);
    setErrors({});
    setFailure(null);
    setConfirmDelete(false);
  }, [open, editing, currency, tripId]);

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
    if (readOnly || saving) return;
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
      try { localStorage.setItem(`wayfare.paidIn.${tripId}`, paidIn); } catch { /* Optional preference. */ }
      onClose();
    } catch (err) {
      setFailure(errorMessage(err, "Could not save that expense."));
    } finally {
      setSaving(false);
    }
  }

  async function remove() {
    if (!editing || readOnly || saving) return;
    setSaving(true);
    try {
      await onDelete(editing.id);
      onClose();
    } catch (err) {
      setFailure(errorMessage(err, "Could not delete that expense."));
      setSaving(false);
    }
  }

  return (
    <Sheet
      open={open}
      onClose={onClose}
      dirty={!readOnly && initialDraft !== "" && draftKey !== initialDraft}
      busy={saving}
      eyebrow={readOnly ? "Expense details" : editing ? "Edit entry" : "New entry"}
      title={readOnly ? editing?.title ?? "Expense" : editing ? "Fix the details" : "What did it cost?"}
      footer={!readOnly && <Button type="submit" form={formId} variant="accent" full loading={saving}>
        {editing ? "Save changes" : "Add to the ledger"}
      </Button>}
    >
      {readOnly && editing ? (
        <div className="space-y-5 pb-4">
          <p className="tabular text-3xl font-medium">{symbolFor(currency)}{money(editing.amount)}</p>
          <p className="text-sm text-ink-soft">Paid by {payer ?? "another traveller"}. Only the person who added this expense can edit or delete it.</p>
          <dl className="grid grid-cols-2 gap-3 text-sm">
            <dt className="text-ink-soft">Category</dt><dd>{CATEGORY_LIST.find((c) => c.key === editing.category)?.label}</dd>
            <dt className="text-ink-soft">Date</dt><dd>{editing.spent_on}</dd>
            {editing.original_currency && <><dt className="text-ink-soft">Originally paid</dt><dd>{editing.original_currency} {money(editing.original_amount ?? editing.amount)}</dd></>}
          </dl>
          {editing.note && <p className="whitespace-pre-wrap break-words text-sm">{editing.note}</p>}
        </div>
      ) : (
      <form id={formId} onSubmit={submit} className="flex flex-col gap-5">
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
          <div className="-mt-2 flex flex-wrap items-baseline justify-between gap-2 rounded-xl border border-line bg-paper px-3.5 py-2.5">
            <span className="tabular text-[15px] font-medium text-ink">
              = {symbolFor(currency)}
              {money(preview.converted)}
            </span>
            <span className="tabular text-xs text-ink-faint">
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
          <legend className="mb-0.5 text-[13px] font-medium text-ink-soft">
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
                  className="press inline-flex min-h-11 items-center gap-1.5 rounded-full border px-3 py-2 text-[13px] font-medium"
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

        <button type="button" aria-expanded={moreDetails} onClick={() => setMoreDetails(!moreDetails)} className="press min-h-11 self-start text-sm font-medium text-clay underline underline-offset-4">
          {moreDetails ? "Hide optional note" : "More details · add a note"}
        </button>
        {moreDetails && <TextArea
          label="Note"
          placeholder="Anything you want to remember"
          value={note}
          onChange={(e) => setNote(e.target.value)}
          maxLength={500}
        />}

        {editing &&
          (confirmDelete ? (
            <div className="flex items-center gap-2.5 rounded-2xl border border-clay/30 bg-clay-wash/50 p-2.5">
              <p className="flex-1 pl-1.5 text-[13px] text-clay-deep">Delete this entry?</p>
              <Button type="button" variant="quiet" onClick={() => setConfirmDelete(false)} className="h-10 px-3.5">
                Keep
              </Button>
              <Button type="button" variant="accent" onClick={remove} loading={saving} className="h-10 px-3.5">
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
      )}
    </Sheet>
  );
}
