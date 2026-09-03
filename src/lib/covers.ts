import { supabase } from "./supabase";
import type { Trip } from "./types";

const BUCKET = "trip-covers";

/**
 * What the cover is a picture of. A trip usually names its destination, but a
 * trip called "Lisbon with the Ferrante crew" is subject enough on its own.
 */
export function coverSubject(trip: Pick<Trip, "name" | "destination">) {
  return trip.destination?.trim() || trip.name.trim() || null;
}

/** Public bucket, so the card can point an <img> straight at the CDN. */
export function coverUrl(path: string | null) {
  if (!path) return null;
  return supabase.storage.from(BUCKET).getPublicUrl(path).data.publicUrl;
}
