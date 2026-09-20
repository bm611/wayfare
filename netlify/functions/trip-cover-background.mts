import { createClient } from "@supabase/supabase-js";
import Together from "together-ai";

/**
 * Draws the destination illustration displayed on trip cards.
 *
 * A background function keeps image generation off the request path. The
 * caller gets a 202 immediately and watches `trips.cover_status` instead.
 */

const MODEL = "Qwen/Qwen-Image-2.0";
// Wide enough to stay sharp on a 2x phone screen, small enough that a list of
// cards is not megabytes of artwork.
const WIDTH = 1264;
const HEIGHT = 848;

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * The words lettered onto the print, built here rather than left to the model,
 * which otherwise invents subtitles and drops characters. A destination like
 * "Iceland - Winter '26" is trimmed back to the place; the year comes from the
 * trip's own dates.
 */
function coverLettering(subject: string, startDate: string | null) {
  const place = subject
    .replace(/\s*[-–—·,|/]?\s*\b(spring|summer|autumn|fall|winter)\b.*$/i, "")
    .replace(/\s*[-–—·,|/]?\s*'?\d{2,4}\s*$/, "")
    .replace(/['’.]/g, "")
    .replace(/[^\p{L}\p{N} ]+/gu, " ")
    .replace(/\s+/g, " ")
    .trim() || subject.trim();
  return { place: place.toUpperCase(), year: startDate?.match(/^\d{4}/)?.[0] ?? null };
}

/**
 * Keep the isometric direction consistent while letting the destination
 * determine the landmarks, architecture and geography.
 */
function coverPrompt(subject: string, { place, year }: ReturnType<typeof coverLettering>) {
  return [
    `A premium 3D isometric miniature city diorama of ${subject}, horizontal 3:2 composition on a pure white studio background.`,
    "Elevated orthographic camera at 35 degrees, no perspective distortion.",
    `One cohesive compact diorama with recognizable landmarks, local architecture, streets and trees of ${subject}, grounded in its actual geography. Include rivers, coast or canals only if the destination has them; otherwise use local streets, squares or terrain. No landmarks from other places.`,
    "Carefully modeled masonry, matte ceramic roofs, warm terracotta accents and deep green foliage, with clear blue-green water where geographically appropriate. Refined architectural scale model, crisp geometry, realistic material textures, soft afternoon sunlight from the upper left, subtle ambient occlusion and a soft contact shadow beneath the model.",
    "Balanced visual hierarchy, charming small details, ample white margin, every building fully within frame.",
    `Integrate ${JSON.stringify(place)} into the front face of the diorama's stone base as large, inset navy Roman capitals.${year ? ` Place ${JSON.stringify(year)} beside it in smaller, clearly readable engraved numerals.` : ""}`,
    "Keep all lettering on one clean architectural surface, facing the viewer with minimal perspective distortion. Use strong contrast and restrained detailing so the lettering remains readable at mobile card size. The typography should feel built into the miniature city. No separate caption or white text band below the scene.",
    "No other text, no logos, no border, no watercolor, no painted brushwork, no floating disconnected landmarks, no exaggerated skyscrapers.",
  ].join(" ");
}

export default async (req: Request) => {
  const authorization = req.headers.get("authorization") ?? "";
  if (!authorization.startsWith("Bearer ")) return;

  let body: unknown;
  try {
    body = await req.json();
  } catch {
    return;
  }
  const tripId = (body as { tripId?: unknown } | null)?.tripId;
  if (typeof tripId !== "string" || !UUID.test(tripId)) return;

  const supabaseUrl = process.env.SUPABASE_URL ?? process.env.VITE_SUPABASE_URL;
  const supabaseKey =
    process.env.SUPABASE_PUBLISHABLE_KEY ?? process.env.VITE_SUPABASE_PUBLISHABLE_KEY;
  const togetherKey = process.env.TOGETHER_API_KEY;

  if (!supabaseUrl || !supabaseKey || !togetherKey) {
    console.error("trip-cover: missing configuration, refusing to run");
    return;
  }

  // The caller's own token, never a service key: every read and write below
  // stays subject to the same row level security the browser gets.
  const supabase = createClient(supabaseUrl, supabaseKey, {
    global: { headers: { Authorization: authorization } },
    auth: { persistSession: false, autoRefreshToken: false },
  });

  const { data: claimed, error: claimError } = await supabase.rpc("claim_trip_cover", {
    p_trip: tripId,
  });
  if (claimError) {
    console.error("trip-cover: could not claim", claimError.message);
    return;
  }
  if (!claimed) {
    // Someone else is already drawing this one, or it is too soon to retry.
    console.log(`trip-cover: ${tripId} already claimed, standing down`);
    return;
  }

  try {
    const { data: trip, error } = await supabase
      .from("trips")
      .select("name, destination, start_date")
      .eq("id", tripId)
      .single();
    if (error || !trip) throw new Error(error?.message ?? "Trip is not readable");

    const subject = trip.destination?.trim() || trip.name.trim();
    if (!subject) throw new Error("Trip has nothing to draw");

    const together = new Together({ apiKey: togetherKey });
    const result = await together.images.generate({
      model: MODEL,
      prompt: coverPrompt(subject, coverLettering(subject, trip.start_date)),
      width: WIDTH,
      height: HEIGHT,
      response_format: "base64",
      output_format: "jpeg",
      disable_safety_checker: true,
    });

    const b64 = (result.data?.[0] as { b64_json?: string } | undefined)?.b64_json;
    if (!b64) throw new Error("Model returned no image data");

    // A fresh name per generation, so a regenerated cover is never served from
    // a stale CDN cache under the old URL.
    // Native cards recognize the print suffix and omit their duplicate title.
    const file = `${Date.now()}-print.jpg`;
    const path = `${tripId}/${file}`;
    const { error: uploadError } = await supabase.storage
      .from("trip-covers")
      .upload(path, Buffer.from(b64, "base64"), {
        contentType: "image/jpeg",
        cacheControl: "31536000",
        upsert: true,
      });
    if (uploadError) throw new Error(uploadError.message);

    const { error: saveError } = await supabase.rpc("set_trip_cover", {
      p_trip: tripId,
      p_path: path,
      p_subject: subject,
    });
    if (saveError) throw new Error(saveError.message);

    // Regenerating would otherwise leave every previous frame behind.
    const { data: existing } = await supabase.storage.from("trip-covers").list(tripId);
    const stale = (existing ?? [])
      .filter((object) => object.name !== file)
      .map((object) => `${tripId}/${object.name}`);
    if (stale.length) await supabase.storage.from("trip-covers").remove(stale);

    console.log(`trip-cover: ${tripId} ready at ${path}`);
  } catch (err) {
    console.error(`trip-cover: ${tripId} failed —`, err instanceof Error ? err.message : err);
    // Record the failure so the card stops waiting and the retry clock starts.
    await supabase.rpc("set_trip_cover", { p_trip: tripId, p_path: null, p_subject: null });
  }
};
