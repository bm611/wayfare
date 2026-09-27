/**
 * Passport stamps on trip cards. The drawing comes from the server
 * (`trips.cover_art`); how the stamp sits on the card is derived from the trip
 * id here, and identically in the Android `Stamp.kt` and iOS `Stamp.swift`, so
 * one trip looks the same everywhere. `tests/unit/trip-cover-stamp.mjs` pins
 * the fixtures those ports are tested against too.
 */

/** Validated server side: absolute M/L/C/Q/Z paths on a 0 0 64 64 grid. */
export type StampArt = { v: 1; label: string; paths: string[]; fallback: boolean };

export const STAMP_TINTS = ["peach", "sky", "sage", "lilac"] as const;
export type StampTint = (typeof STAMP_TINTS)[number];
export const STAMP_TILTS = [-3, 2.5, -1.5] as const;

export type StampStyle = { tint: StampTint; tilt: number; arched: boolean };

/**
 * A sum of character codes rather than a real hash: trip ids are random
 * UUIDs, so it spreads well enough, and it is trivial to repeat in Kotlin
 * and Swift without drifting.
 */
export function stampStyle(id: string): StampStyle {
  let sum = 0;
  for (let i = 0; i < id.length; i++) sum += id.charCodeAt(i);
  return {
    tint: STAMP_TINTS[sum % 4],
    tilt: STAMP_TILTS[Math.floor(sum / 4) % 3],
    arched: Math.floor(sum / 12) % 2 === 1,
  };
}

const MONTHS = ["JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"];

/** "SEP 2026" from a trip's start date; nothing for an undated trip. */
export function stampMonth(start: string | null) {
  const match = start?.match(/^(\d{4})-(\d{2})/);
  if (!match) return null;
  const month = MONTHS[Number(match[2]) - 1];
  return month ? `${month} ${match[1]}` : null;
}

/** Guards the renderer against a row written by an older or newer server. */
export function stampArt(value: unknown): StampArt | null {
  const art = value as Partial<StampArt> | null;
  if (!art || typeof art !== "object" || !Array.isArray(art.paths)) return null;
  if (!art.paths.every((d) => typeof d === "string")) return null;
  return { v: 1, label: typeof art.label === "string" ? art.label : "", paths: art.paths, fallback: art.fallback === true };
}
