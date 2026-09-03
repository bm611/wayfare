import { useCallback, useEffect, useState } from "react";
import { supabase } from "../lib/supabase";
import { useAuth } from "./useAuth";
import type { TripMember } from "../lib/types";

export function useMembers(tripId: string | undefined) {
  const { user } = useAuth();
  const [members, setMembers] = useState<TripMember[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!tripId) return;
    setError(null);

    const { data: rows, error: err } = await supabase
      .from("trip_members")
      .select("trip_id, user_id, role, joined_at")
      .eq("trip_id", tripId)
      .order("joined_at", { ascending: true });

    if (err) {
      setError(err.message);
      setLoading(false);
      return;
    }

    // Names live in profiles; RLS lets fellow members read each other's rows.
    const ids = (rows ?? []).map((r) => r.user_id);
    const { data: profiles } = ids.length
      ? await supabase.from("profiles").select("id, display_name").in("id", ids)
      : { data: [] };

    const names = new Map((profiles ?? []).map((p) => [p.id, p.display_name]));
    setMembers(
      (rows ?? []).map((row) => ({
        ...row,
        display_name: names.get(row.user_id) ?? null,
        is_you: row.user_id === user?.id,
      })),
    );
    setLoading(false);
  }, [tripId, user?.id]);

  useEffect(() => {
    setLoading(true);
    void load();
  }, [load]);

  const removeMember = useCallback(
    async (userId: string) => {
      if (!tripId) return;
      const { error: err } = await supabase
        .from("trip_members")
        .delete()
        .eq("trip_id", tripId)
        .eq("user_id", userId);
      if (err) throw err;
      setMembers((prev) => prev.filter((m) => m.user_id !== userId));
    },
    [tripId],
  );

  const leaveTrip = useCallback(async () => {
    if (!tripId || !user) return;
    const { error: err } = await supabase
      .from("trip_members")
      .delete()
      .eq("trip_id", tripId)
      .eq("user_id", user.id);
    if (err) throw err;
  }, [tripId, user]);

  return { members, loading, error, reload: load, removeMember, leaveTrip };
}

export type Membership = ReturnType<typeof useMembers>;



/** Redeems an invite code and returns the trip it unlocked. */
export async function joinTrip(code: string) {
  const { data, error } = await supabase.rpc("join_trip", { p_code: code.trim().toUpperCase() });
  if (error) throw error;
  return data as string;
}
