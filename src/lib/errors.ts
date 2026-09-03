/**
 * Pulls a human-readable message out of whatever a failure turns out to be.
 *
 * Supabase rejects with plain objects, not `Error` instances, so the obvious
 * `err instanceof Error ? err.message : fallback` throws away the only useful
 * thing in them. That is how "new row violates row-level security policy for
 * table trips" reached a user as "Could not save the trip."
 */
export function errorMessage(err: unknown, fallback: string) {
  if (err instanceof Error && err.message.trim()) return err.message;
  if (typeof err === "string" && err.trim()) return err;

  if (err && typeof err === "object") {
    const { message, details, hint } = err as Record<string, unknown>;
    for (const candidate of [message, details, hint]) {
      if (typeof candidate === "string" && candidate.trim()) return candidate;
    }
  }

  return fallback;
}
