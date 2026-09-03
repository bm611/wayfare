import { useCallback, useEffect, useState } from "react";
import { supabase } from "../lib/supabase";
import type { Expense, ExpenseInput } from "../lib/types";
import { useAuth } from "./useAuth";

export function useExpenses(tripId: string | undefined) {
  const { user } = useAuth();
  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!tripId) return;
    setError(null);
    const { data, error: err } = await supabase
      .from("expenses")
      .select("*")
      .eq("trip_id", tripId)
      .order("spent_on", { ascending: false })
      .order("created_at", { ascending: false });

    if (err) setError(err.message);
    else setExpenses((data ?? []).map((e) => ({ ...e, amount: Number(e.amount) })));
    setLoading(false);
  }, [tripId]);

  useEffect(() => {
    setLoading(true);
    void load();
  }, [load]);

  const addExpense = useCallback(
    async (input: Omit<ExpenseInput, "trip_id">) => {
      if (!user || !tripId) throw new Error("Not signed in");
      const { data, error: err } = await supabase
        .from("expenses")
        .insert({ ...input, trip_id: tripId, user_id: user.id })
        .select()
        .single();
      if (err) throw err;
      const created: Expense = { ...data, amount: Number(data.amount) };
      setExpenses((prev) => sortExpenses([created, ...prev]));
      return created;
    },
    [tripId, user],
  );

  const updateExpense = useCallback(async (id: string, patch: Partial<ExpenseInput>) => {
    const { data, error: err } = await supabase
      .from("expenses")
      .update(patch)
      .eq("id", id)
      .select()
      .single();
    if (err) throw err;
    const next: Expense = { ...data, amount: Number(data.amount) };
    setExpenses((prev) => sortExpenses(prev.map((e) => (e.id === id ? next : e))));
    return next;
  }, []);

  const deleteExpense = useCallback(async (id: string) => {
    const { error: err } = await supabase.from("expenses").delete().eq("id", id);
    if (err) throw err;
    setExpenses((prev) => prev.filter((e) => e.id !== id));
  }, []);

  return { expenses, loading, error, reload: load, addExpense, updateExpense, deleteExpense };
}

function sortExpenses(list: Expense[]) {
  return [...list].sort((a, b) =>
    a.spent_on === b.spent_on
      ? b.created_at.localeCompare(a.created_at)
      : b.spent_on.localeCompare(a.spent_on),
  );
}
