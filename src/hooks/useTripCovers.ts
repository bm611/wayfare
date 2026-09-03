import { useEffect, useRef } from "react";
import { supabase } from "../lib/supabase";
import { coverSubject } from "../lib/covers";
import type { Trip } from "../lib/types";

const ENDPOINT = "/.netlify/functions/trip-cover-background";
const POLL_MS = 4000;
/** Roughly three minutes. Drawing takes ~15s; past this it is not coming. */
const MAX_POLLS = 45;

export type CoverPatch = Pick<Trip, "id" | "cover_path" | "cover_status">;

type Watchable = Pick<Trip, "id" | "name" | "destination" | "cover_status">;

/**
 * Keeps trip covers moving toward 'ready'.
 *
 * Generation lives in a background function that answers 202 and reports back
 * through the row, so this asks for the ones that are missing and then watches
 * the table until they land.
 */
export function useTripCovers(trips: Watchable[], apply: (patches: CoverPatch[]) => void) {
  // Asked once per trip per session. The server's claim is the real guard
  // against duplicate work; this only avoids pointless round trips on rerender.
  const asked = useRef(new Set<string>());

  const wanted = trips
    .filter((t) => coverSubject(t) && (t.cover_status === "idle" || t.cover_status === "failed"))
    .map((t) => t.id)
    .join(",");

  const pending = trips
    .filter((t) => t.cover_status === "pending")
    .map((t) => t.id)
    .join(",");

  useEffect(() => {
    const todo = wanted.split(",").filter((id) => id && !asked.current.has(id));
    if (todo.length === 0) return;

    let cancelled = false;
    void (async () => {
      const { data } = await supabase.auth.getSession();
      const token = data.session?.access_token;
      if (!token || cancelled) return;

      const started: string[] = [];
      // One at a time: a first run with several old trips should not fire a
      // handful of image jobs at once.
      for (const tripId of todo) {
        if (cancelled) return;
        asked.current.add(tripId);
        try {
          const res = await fetch(ENDPOINT, {
            method: "POST",
            headers: { "content-type": "application/json", authorization: `Bearer ${token}` },
            body: JSON.stringify({ tripId }),
          });
          if (res.ok) started.push(tripId);
          else asked.current.delete(tripId);
        } catch {
          asked.current.delete(tripId);
        }
      }

      // Show the developing state now rather than a poll interval from now.
      if (!cancelled && started.length > 0) {
        apply(started.map((id) => ({ id, cover_path: null, cover_status: "pending" as const })));
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [wanted, apply]);

  useEffect(() => {
    const ids = pending.split(",").filter(Boolean);
    if (ids.length === 0) return;

    let polls = 0;
    let timer: ReturnType<typeof setTimeout>;

    const tick = async () => {
      polls += 1;
      const { data } = await supabase
        .from("trips")
        .select("id, cover_path, cover_status")
        .in("id", ids);
      if (data && data.length > 0) apply(data as CoverPatch[]);
      if (polls < MAX_POLLS) timer = setTimeout(() => void tick(), POLL_MS);
    };

    timer = setTimeout(() => void tick(), POLL_MS);
    return () => clearTimeout(timer);
  }, [pending, apply]);
}
