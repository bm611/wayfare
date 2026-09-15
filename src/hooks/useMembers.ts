import { useCallback } from "react";
import { supabase } from "../lib/supabase";
import { useAuth } from "./useAuth";
import type { TripMember } from "../lib/types";

import { useRemote } from "./useRemote";
import { loadPages } from "../lib/pages";

export function useMembers(tripId: string | undefined) {
  const { user } = useAuth();
  const fetch = useCallback(async (signal: AbortSignal) => {
    const rows = await loadPages((from, to) => supabase.from("trip_members")
      .select("trip_id, user_id, role, joined_at").eq("trip_id", tripId!)
      .order("joined_at").order("user_id").range(from, to).abortSignal(signal), signal);
    const profiles = [];
    for (let offset = 0; offset < rows.length; offset += 100) {
      profiles.push(...await loadPages((from, to) => supabase.from("profiles").select("id, display_name")
        .in("id", rows.slice(offset, offset + 100).map((row) => row.user_id)).order("id")
        .range(from, to).abortSignal(signal), signal));
    }
    const names = new Map(profiles.map((profile) => [profile.id, profile.display_name]));
    return rows.map((row) => ({ ...row, display_name: names.get(row.user_id) ?? null, is_you: row.user_id === user?.id }));
  }, [tripId, user?.id]);
  const { data: members, setData: setMembers, loading, error, reload: load } = useRemote<TripMember[]>(
    user && tripId ? user.id + "/" + tripId : undefined, [], fetch);

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
    [tripId, setMembers],
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
