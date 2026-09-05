import { useEffect, useId, useMemo, useState } from "react";
import { Sheet } from "./Sheet";
import { Field } from "./Field";
import { Button } from "./Button";
import { ErrorNote } from "./States";
import { BASE_CURRENCY } from "../lib/fx";
import { errorMessage } from "../lib/errors";
import { symbolFor } from "../lib/format";
import type { Trip, TripInput } from "../lib/types";

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
  onSave,
  trip,
}: {
  open: boolean;
  onClose: () => void;
  onSave: (input: TripInput) => Promise<unknown>;
  trip?: Trip;
}) {
  const [dirty, setDirty] = useState(false);
  const [busy, setBusy] = useState(false);
  const formId = useId();

  return (
    <Sheet
      open={open}
      onClose={onClose}
      dirty={dirty}
      busy={busy}
      eyebrow={trip ? "Edit itinerary" : "New itinerary"}
      title={trip ? "Update your trip" : "Where are you headed?"}
      footer={<Button type="submit" form={formId} variant="accent" full loading={busy}>
        {trip ? "Save changes" : "Start the ledger"}
      </Button>}
    >
      <TripForm
        formId={formId}
        trip={trip}
        onClose={onClose}
        onSave={onSave}
        onDirtyChange={setDirty}
        onBusyChange={setBusy}
      />
    </Sheet>
  );
}

function TripForm({
  formId,
  trip,
  onClose,
  onSave,
  onDirtyChange,
  onBusyChange,
}: {
  formId: string;
  trip?: Trip;
  onClose: () => void;
  onSave: (input: TripInput) => Promise<unknown>;
  onDirtyChange: (dirty: boolean) => void;
  onBusyChange: (busy: boolean) => void;
}) {
  const initial = useMemo(
    () =>
      trip
        ? {
            name: trip.name,
            destination: trip.destination ?? "",
            start_date: trip.start_date ?? "",
            end_date: trip.end_date ?? "",
            budget: String(trip.budget),
          }
        : BLANK,
    [trip],
  );
  const [form, setForm] = useState(initial);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [failure, setFailure] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    onDirtyChange(false);
    onBusyChange(false);
  }, [onDirtyChange, onBusyChange]);

  const set = (key: keyof typeof BLANK) => (e: { target: { value: string } }) => {
    const next = { ...form, [key]: e.target.value };
    setForm(next);
    onDirtyChange(Object.keys(BLANK).some((field) => next[field as keyof typeof BLANK] !== initial[field as keyof typeof BLANK]));
  };

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (saving) return;
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
    onBusyChange(true);
    setFailure(null);
    try {
      await onSave({
        name: form.name.trim(),
        destination: form.destination.trim() || null,
        start_date: form.start_date || null,
        end_date: form.end_date || null,
        budget,
        currency: trip?.currency ?? BASE_CURRENCY,
        accent: trip?.accent ?? "clay",
      });
      onClose();
    } catch (err) {
      setFailure(errorMessage(err, "Could not save the trip."));
    } finally {
      setSaving(false);
      onBusyChange(false);
    }
  }

  return (
    <form id={formId} onSubmit={submit} className="flex flex-col gap-5">
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

      <div className="grid grid-cols-1 gap-3 min-[380px]:grid-cols-2">
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

    </form>
  );
}
