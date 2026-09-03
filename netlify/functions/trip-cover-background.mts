import { createClient } from "@supabase/supabase-js";
import Together from "together-ai";

/**
 * Draws the destination shot that sits behind a trip's boarding pass.
 *
 * A background function rather than a plain one: the model takes ~15s, which
 * is past what a synchronous Netlify function is allowed to hold open. The
 * caller gets a 202 immediately and watches `trips.cover_status` instead.
 */

const MODEL = "black-forest-labs/FLUX.2-pro";
// Wide enough to stay sharp on a 2x phone screen, small enough that a list of
// cards is not megabytes of photography.
const WIDTH = 1024;
const HEIGHT = 640;

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * The image has to survive a dark scrim and light text laid over it, so this
 * asks for haze, low contrast and an unpeopled wide shot rather than a punchy
 * postcard. The negatives earn their place: a hallucinated sign or a face in
 * frame reads as a mistake once the trip name is sitting on top of it.
 */
function coverPrompt(subject: string) {
  return [
    `Wide panoramic establishing photograph of ${subject}.`,
    "The iconic architecture, landmarks and landscape of the place, seen from an elevated distance at golden hour.",
    "Warm muted film palette: sun-bleached terracotta, sand, faded ochre, soft dusty teal sky.",
    "Gentle atmospheric haze, low contrast, fine 35mm grain, editorial travel photography, natural light.",
    "Calm and unpeopled, no foreground clutter.",
    "The scene fills the frame edge to edge with the horizon near the middle.",
    "No text, no lettering, no signage, no logos, no watermark, no people, no borders, no collage, no split frames.",
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
      .select("name, destination")
      .eq("id", tripId)
      .single();
    if (error || !trip) throw new Error(error?.message ?? "Trip is not readable");

    const subject = trip.destination?.trim() || trip.name.trim();
    if (!subject) throw new Error("Trip has nothing to draw");

    const together = new Together({ apiKey: togetherKey });
    const result = await together.images.generate({
      model: MODEL,
      prompt: coverPrompt(subject),
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
    const file = `${Date.now()}.jpg`;
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
