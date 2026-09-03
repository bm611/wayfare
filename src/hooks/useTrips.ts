import { useCallback, useEffect, useState } from "react";
import { supabase } from "../lib/supabase";
import type { Trip, TripInput } from "../lib/types";
import type { CoverPatch } from "./useTripCovers";
import { useAuth } from "./useAuth";

export type TripWithSpend = Trip & { spent: number; entries: number };

export function useTrips() {
  const { user } = useAuth();
  const [trips, setTrips] = useState<TripWithSpend[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!user) return;
    setError(null);
    const [tripsRes, expensesRes] = await Promise.all([
      supabase.from("trips").select("*").order("created_at", { ascending: false }),
      supabase.from("expenses").select("trip_id, amount"),
    ]);

    if (tripsRes.error || expensesRes.error) {
      setError((tripsRes.error ?? expensesRes.error)!.message);
      setLoading(false);
      return;
    }

    const totals = new Map<string, { spent: number; entries: number }>();
    for (const row of expensesRes.data ?? []) {
      const prev = totals.get(row.trip_id) ?? { spent: 0, entries: 0 };
      totals.set(row.trip_id, { spent: prev.spent + Number(row.amount), entries: prev.entries + 1 });
    }

    setTrips(
      (tripsRes.data ?? []).map((trip) => ({
        ...trip,
        budget: Number(trip.budget),
        ...(totals.get(trip.id) ?? { spent: 0, entries: 0 }),
      })),
    );
    setLoading(false);
  }, [user]);

  useEffect(() => {
    if (!user) {
      setTrips([]);
      setLoading(false);
      return;
    }
    setLoading(true);
    void load();
  }, [user, load]);

  const createTrip = useCallback(
    async (input: TripInput) => {
      if (!user) throw new Error("Not signed in");
      const { data, error: err } = await supabase
        .from("trips")
        .insert({ ...input, user_id: user.id })
        .select()
        .single();
      if (err) throw err;
      const trip: TripWithSpend = { ...data, budget: Number(data.budget), spent: 0, entries: 0 };
      setTrips((prev) => [trip, ...prev]);
      return trip;
    },
    [user],
  );

  const deleteTrip = useCallback(async (id: string) => {
    const { error: err } = await supabase.from("trips").delete().eq("id", id);
    if (err) throw err;
    setTrips((prev) => prev.filter((t) => t.id !== id));
  }, []);

  /**
   * Folds cover progress into the list without refetching everything.
   * Returns the previous array untouched when nothing moved — the cover poll
   * runs on a timer, and a new array every tick would rerender the whole list.
   */
  const applyCovers = useCallback((patches: CoverPatch[]) => {
    setTrips((prev) => {
      const byId = new Map(patches.map((patch) => [patch.id, patch]));
      let changed = false;
      const next = prev.map((trip) => {
        const patch = byId.get(trip.id);
        if (!patch) return trip;
        if (patch.cover_status === trip.cover_status && patch.cover_path === trip.cover_path) {
          return trip;
        }
        changed = true;
        return { ...trip, cover_path: patch.cover_path, cover_status: patch.cover_status };
      });
      return changed ? next : prev;
    });
  }, []);

  return { trips, loading, error, reload: load, createTrip, deleteTrip, applyCovers };
}

export function useTrip(tripId: string | undefined) {
  const [trip, setTrip] = useState<Trip | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!tripId) return;
    setError(null);
    const { data, error: err } = await supabase.from("trips").select("*").eq("id", tripId).maybeSingle();
    if (err) setError(err.message);
    else if (!data) setError("That trip no longer exists.");
    else setTrip({ ...data, budget: Number(data.budget) });
    setLoading(false);
  }, [tripId]);

  useEffect(() => {
    setLoading(true);
    void load();
  }, [load]);

  const updateTrip = useCallback(
    async (patch: Partial<TripInput>) => {
      if (!tripId) return;
      const { data, error: err } = await supabase
        .from("trips")
        .update(patch)
        .eq("id", tripId)
        .select()
        .single();
      if (err) throw err;
      setTrip({ ...data, budget: Number(data.budget) });
    },
    [tripId],
  );

  const removeTrip = useCallback(async () => {
    if (!tripId) return;
    const { error: err } = await supabase.from("trips").delete().eq("id", tripId);
    if (err) throw err;
  }, [tripId]);

  return { trip, loading, error, reload: load, updateTrip, removeTrip };
}
