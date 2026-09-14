import { createClient } from "@supabase/supabase-js";
import Together from "together-ai";

/**
 * Draws the destination illustration displayed on trip cards.
 *
 * A background function rather than a plain one: the model takes ~15s, which
 * is past what a synchronous Netlify function is allowed to hold open. The
 * caller gets a 202 immediately and watches `trips.cover_status` instead.
 */

const MODEL = "google/flash-image-3.1";
// Wide enough to stay sharp on a 2x phone screen, small enough that a list of
// cards is not megabytes of artwork.
const WIDTH = 1024;
const HEIGHT = 640;

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
 * Keep the watercolor direction consistent while letting the destination
 * determine the landmarks and geography, rather than reusing London's scene.
 */
function coverPrompt(subject: string, { place, year }: ReturnType<typeof coverLettering>) {
  return [
    `Create an elegant watercolor-and-ink travel print of ${subject} on textured warm cream paper, in a horizontal 8:5 composition.`,
    "The upper 65 percent is the panoramic illustration. Below it is plain cream paper holding the lettering. Keep the entire illustration and lettering inside the canvas with generous margins.",
    `Show the skyline and clearly recognizable landmarks of ${subject}, selecting three to five iconic architectural features authentic to this destination.`,
    "Arrange the landmarks harmoniously across the scene with locally characteristic architecture and soft clusters of greenery.",
    "Reflect the destination's actual geography: include its river, canals, coast, bridges and small boats only where appropriate, with gentle reflections in any water. For inland places without a defining waterfront, use characteristic streets, squares, gardens or terrain instead. Do not borrow landmarks from other cities.",
    "Style the scene as a sophisticated architectural travel sketch using delicate ink linework, translucent watercolor washes, soft pale blues, muted greens, warm beige stone tones, and subtle gray-blue shadows.",
    "Keep the painting airy and elegant with loose brush edges, minimal detail in the distant skyline, and plenty of clean negative space around the illustration.",
    "The illustration should feel panoramic and balanced, with the landmarks arranged harmoniously rather than crowded together. Let watercolor edges and reflections dissolve softly into the cream paper above the lettering.",
    "Avoid photorealism, heavy saturation, bold outlines, or cartoon styling. Aim for a timeless architectural editorial aesthetic, hand-painted watercolor texture, subtle paper grain, and understated sophistication.",
    `Below the illustration, centered horizontally, letter ${JSON.stringify(place)} in large, widely spaced uppercase serif capitals in muted ink blue, sitting about 78 percent of the way down the canvas.`,
    year
      ? `Directly beneath it, letter the year ${JSON.stringify(year)} in the same serif typeface and color, much smaller and widely spaced.`
      : "",
    "Copy the letters and digits exactly. Use a classic engraved serif typeface, never sans-serif, script or handwriting.",
    "Leave the bottom 10 percent of the canvas as empty cream paper.",
    `${year ? "Those two lines are" : "That line is"} the only text in the image: no punctuation, no season, no country label, no tagline, no subtitle, no shop or studio name, no captions, no signage, no logos, no watermark, no border, no decorative rules. Keep the paper light and clean, without gradients, shadows or dark panels. High resolution.`,
  ].filter(Boolean).join(" ");
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
