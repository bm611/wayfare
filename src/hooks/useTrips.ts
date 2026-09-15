import { useCallback } from "react";
import { supabase } from "../lib/supabase";
import type { Trip, TripInput } from "../lib/types";
import type { CoverPatch } from "./useTripCovers";
import { useAuth } from "./useAuth";

import { useRemote } from "./useRemote";
import { loadPages } from "../lib/pages";

export type TripWithSpend = Trip & { spent: number; entries: number };

export function useTrips() {
  const { user } = useAuth();
  const fetch = useCallback(async (signal: AbortSignal) => {
    const rows = await loadPages((from, to) => supabase.from("trip_summaries").select("*")
      .order("created_at", { ascending: false }).order("id").range(from, to).abortSignal(signal), signal);
    return rows.map((trip) => ({ ...trip, budget: Number(trip.budget), spent: Number(trip.spent), entries: Number(trip.entries) }));
  }, []);
  const { data: trips, setData: setTrips, loading, error, reload: load } = useRemote<TripWithSpend[]>(user?.id, [], fetch);

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
    [user, setTrips],
  );

  const deleteTrip = useCallback(async (id: string) => {
    const { error: err } = await supabase.from("trips").delete().eq("id", id);
    if (err) throw err;
    setTrips((prev) => prev.filter((t) => t.id !== id));
  }, [setTrips]);

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
  }, [setTrips]);

  return { trips, loading, error, reload: load, createTrip, deleteTrip, applyCovers };
}

export function useTrip(tripId: string | undefined) {
  const { user } = useAuth();
  const fetch = useCallback(async (signal: AbortSignal) => {
    const { data, error } = await supabase.from("trips").select("*").eq("id", tripId!).abortSignal(signal).maybeSingle();
    if (error) throw error;
    if (!data) throw new Error("That trip no longer exists.");
    return { ...data, budget: Number(data.budget) };
  }, [tripId]);
  const { data: trip, setData: setTrip, loading, error, reload: load } = useRemote<Trip | null>(
    user && tripId ? user.id + "/" + tripId : undefined, null, fetch);

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
    [tripId, setTrip],
  );

  const removeTrip = useCallback(async () => {
    if (!tripId) return;
    const { error: err } = await supabase.from("trips").delete().eq("id", tripId);
    if (err) throw err;
  }, [tripId]);

  return { trip, loading, error, reload: load, updateTrip, removeTrip };
}
