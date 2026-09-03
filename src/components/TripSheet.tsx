import { useState } from "react";
import { Sheet } from "./Sheet";
import { Field } from "./Field";
import { Button } from "./Button";
import { ErrorNote } from "./States";
import { BASE_CURRENCY } from "../lib/fx";
import { errorMessage } from "../lib/errors";
import { symbolFor } from "../lib/format";
import type { TripInput } from "../lib/types";

const BLANK = {
  name: "",
  destination: "",
  start_date: "",
  end_date: "",
  budget: "",
};

export function TripSheet({
  open,
  onClose,
  onCreate,
}: {
  open: boolean;
  onClose: () => void;
  onCreate: (input: TripInput) => Promise<unknown>;
}) {
  const [form, setForm] = useState(BLANK);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [failure, setFailure] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const set = (key: keyof typeof BLANK) => (e: { target: { value: string } }) =>
    setForm((prev) => ({ ...prev, [key]: e.target.value }));

  function close() {
    setForm(BLANK);
    setErrors({});
    setFailure(null);
    onClose();
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const next: Record<string, string> = {};
    if (!form.name.trim()) next.name = "Give the trip a name.";
    const budget = form.budget === "" ? 0 : Number(form.budget);
    if (Number.isNaN(budget) || budget < 0) next.budget = "Budget has to be a positive number.";
    if (form.start_date && form.end_date && form.end_date < form.start_date) {
      next.end_date = "The return date lands before departure.";
    }
    setErrors(next);
    if (Object.keys(next).length) return;

    setSaving(true);
    setFailure(null);
    try {
      await onCreate({
        name: form.name.trim(),
        destination: form.destination.trim() || null,
        start_date: form.start_date || null,
        end_date: form.end_date || null,
        budget,
        currency: BASE_CURRENCY,
        accent: "clay",
      });
      close();
    } catch (err) {
      setFailure(errorMessage(err, "Could not save the trip."));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Sheet open={open} onClose={close} eyebrow="New itinerary" title="Where are you headed?">
      <form onSubmit={submit} className="flex flex-col gap-5">
        {failure && <ErrorNote message={failure} />}

        <Field
          label="Trip name"
          placeholder="Lisbon with the Ferrante crew"
          value={form.name}
          onChange={set("name")}
          error={errors.name}
          autoFocus
          maxLength={80}
        />

        <Field
          label="Destination"
          placeholder="Lisbon, Portugal"
          value={form.destination}
          onChange={set("destination")}
          maxLength={120}
        />

        <div className="grid grid-cols-2 gap-3">
          <Field label="Depart" type="date" value={form.start_date} onChange={set("start_date")} />
          <Field
            label="Return"
            type="date"
            value={form.end_date}
            onChange={set("end_date")}
            error={errors.end_date}
          />
        </div>

        <Field
          label="Budget"
          type="number"
          inputMode="decimal"
          step="0.01"
          min="0"
          placeholder="2500"
          prefix={symbolFor(BASE_CURRENCY)}
          value={form.budget}
          onChange={set("budget")}
          error={errors.budget}
          hint="In euros. Leave blank to just track spend — you can log costs in any currency."
        />

        <Button type="submit" variant="accent" full loading={saving}>
          Start the ledger
        </Button>
      </form>
    </Sheet>
  );
}
