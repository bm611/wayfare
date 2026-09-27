import { createClient } from "@supabase/supabase-js";
import Together from "together-ai";
import {
  FALLBACKS,
  FALLBACK_ICONS,
  MAX_LABEL_CHARS,
  MAX_PATHS,
  MIN_PATHS,
  StampError,
  isFallback,
  stampLabel,
  validatePaths,
  type Fallback,
  type StampArt,
} from "../lib/stamp.mts";

/**
 * Draws the passport stamp shown on trip cards: a few lines of SVG path data
 * from a text model, validated and normalised here before anything is stored.
 *
 * A background function keeps the model call off the request path. The caller
 * gets a 202 immediately and watches `trips.cover_status` instead.
 */

const MODEL = "deepseek-ai/DeepSeek-V4.1-Flash";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

type Season = "winter" | "spring" | "summer" | "autumn";

/**
 * What a descriptive trip name is allowed to contribute. The vocabulary is
 * seasonal only on purpose: the destination stays the sole authority on
 * geography, so "ski" buys snow, never an invented mountain.
 *
 * Mirrored by `trip_cover_season()` in the trip-cover-season migration, which
 * decides when a rename is worth redrawing for. Words added here belong there.
 */
const SEASONS: Array<[RegExp, Season]> = [
  [/\b(winter|snow|snowy|ski|skiing|christmas|xmas|new year|aurora|northern lights)\b/i, "winter"],
  [/\b(spring|blossom|blossoms|cherry blossom|easter)\b/i, "spring"],
  [/\b(summer|beach|seaside|sun|sunny|heatwave)\b/i, "summer"],
  [/\b(autumn|fall|foliage|harvest)\b/i, "autumn"],
];

/** A single small touch per season; the landmark stays the subject. */
const SEASON_TOUCH: Record<Season, string> = {
  winter: "It is deep winter: add snow — a short snow line on roofs or peaks, or a few falling flakes as tiny strokes.",
  spring: "It is spring: add one small blossoming branch or a few blossom dots.",
  summer: "It is high summer: add a sun (a small circle) or a few heat-shimmer strokes.",
  autumn: "It is autumn: add two or three small falling leaves.",
};

/**
 * Strips the decoration off a subject so the stamp names a place.
 * "Iceland - Winter '26" comes back as "Iceland"; a plain destination is
 * untouched.
 */
function cleanPlace(subject: string) {
  return (
    subject
      .replace(/\s*[-–—·,|/]?\s*\b(spring|summer|autumn|fall|winter)\b.*$/i, "")
      .replace(/\s*[-–—·,|/]?\s*'?\d{2,4}\s*$/, "")
      .replace(/['’.]/g, "")
      .replace(/[^\p{L}\p{N} ]+/gu, " ")
      .replace(/\s+/g, " ")
      .trim() || subject.trim()
  );
}

function detectSeason(name: string): Season | null {
  for (const [pattern, season] of SEASONS) if (pattern.test(name)) return season;
  return null;
}

/**
 * Splits the two form fields into the two things the drawing needs.
 * Destination answers *where*, and only it reaches the landmark and the label;
 * the trip name answers *when*, and only through the seasonal vocabulary above.
 * A trip named "finland winter '26" bound for "Finland" draws Finland in snow.
 */
export function coverSubject(name: string, destination: string | null) {
  return {
    place: cleanPlace(destination?.trim() || name.trim()),
    season: detectSeason(name),
  };
}

/**
 * The portfolio's PlaceMark drawings, reduced to bare path data: circles and
 * rects written as paths, Pisa's rotation applied to its points, and the
 * dashed tow line drawn solid. They set the style the model should match.
 */
const EXAMPLES: Array<{ place: string; label: string; fallback: Fallback; paths: string[] }> = [
  {
    place: "Mount Hood, Oregon",
    label: "MT HOOD",
    fallback: "mountain",
    paths: [
      "M6 52 L30 13 L38 25 L42 20 L58 52",
      "M23 24 L27 28 L31 23 L35 28 L37 25",
      "M45 14 a4 4 0 1 0 8 0 a4 4 0 1 0 -8 0",
      "M4 52 H60",
    ],
  },
  {
    place: "Rome",
    label: "ROMA",
    fallback: "temple",
    paths: [
      "M8 52 V20 Q28 14 46 17 V24 H52 V32 H56 V52",
      "M8 30 Q28 25 46 27 H52 M8 41 H56",
      "M12 52 v-6 a3 3 0 0 1 6 0 v6 M22 52 v-6 a3 3 0 0 1 6 0 v6 M32 52 v-6 a3 3 0 0 1 6 0 v6 M42 52 v-6 a3 3 0 0 1 6 0 v6",
      "M12 41 v-5 a3 3 0 0 1 6 0 v5 M22 41 v-5 a3 3 0 0 1 6 0 v5 M32 41 v-5 a3 3 0 0 1 6 0 v5 M42 41 v-5 a3 3 0 0 1 6 0 v5",
      "M14 24 v2 M22 22 v2 M30 22 v2 M38 22 v2",
      "M4 52 H60",
    ],
  },
  {
    place: "Florence",
    label: "FIRENZE",
    fallback: "temple",
    paths: [
      "M18 52 V38 H46 V52",
      "M20 38 Q20 19 32 14 Q44 19 44 38",
      "M26 38 Q25 23 32 14 Q39 23 38 38",
      "M29 14 V9 H35 V14 M32 9 V5",
      "M8 52 V43 H18 M46 43 H56 V52",
      "M4 52 H60",
    ],
  },
  {
    place: "Pisa",
    label: "PISA",
    fallback: "tower",
    paths: [
      "M28.6 14.3 L44.4 16.3 L39.9 53 L24.1 51 Z",
      "M31.8 8.7 L42.7 10 L42 15.9 L31.1 14.6 Z",
      "M27.7 21.2 L43.6 23.2 M27 27.2 L42.9 29.2 M26.3 33.2 L42.1 35.1 M25.5 39.1 L41.4 41.1 M24.8 45.1 L40.7 47",
      "M31.7 21.7 L31 27.7 M35.7 22.2 L34.9 28.2 M30.2 33.6 L29.5 39.6 M34.2 34.1 L33.5 40.1",
      "M4 52 H60",
    ],
  },
  {
    place: "Dubrovnik",
    label: "DUBROVNIK",
    fallback: "castle",
    paths: [
      "M6 52 V34 H9 V31 H12 V34 H15 V31 H18 V34 H21 V31 H24 V34 H27 V31 H30 V34 H34",
      "M34 52 V16 H37 V19 H40 V16 H44 V19 H47 V16 H50 V52",
      "M34 36 H50 M42 25 v5",
      "M16 52 v-7 a4 4 0 0 1 8 0 v7",
      "M50 42 L55 37 L60 42 V52",
      "M4 52 H60",
    ],
  },
  {
    place: "Koločep, Croatia",
    label: "KOLOČEP",
    fallback: "island",
    paths: [
      "M8 45 Q20 32 31 37 Q42 28 56 45",
      "M41 32 V25 M35 27 Q41 18 47 27",
      "M6 11 L52 23 M28 17 v5",
      "M26 24 a2 2 0 1 0 4 0 a2 2 0 1 0 -4 0",
      "M4 51 q4 -3 8 0 t8 0 t8 0 t8 0 t8 0 t8 0 t8 0",
    ],
  },
  {
    place: "Mysore",
    label: "MYSORE",
    fallback: "temple",
    paths: [
      "M8 52 V36 H56 V52",
      "M26 36 V22 H38 V36 M26 22 Q26 13 32 10 Q38 13 38 22 M32 10 V6",
      "M10 36 V28 H18 V36 M10 28 Q14 21 18 28 M14 24.5 V21",
      "M46 36 V28 H54 V36 M46 28 Q50 21 54 28 M50 24.5 V21",
      "M13 52 v-6 a3 3 0 0 1 6 0 v6 M29 52 v-7 a3 3 0 0 1 6 0 v7 M45 52 v-6 a3 3 0 0 1 6 0 v6",
      "M4 52 H60",
    ],
  },
  {
    place: "Varkala, Kerala",
    label: "VARKALA",
    fallback: "beach",
    paths: [
      "M4 52 V30 H20 L22 36 L21 42 L25 52",
      "M12 30 Q13 22 11 16 M11 16 Q6 14 4 18 M11 16 Q16 13 19 17 M11 16 Q9 11 5 11 M11 16 Q14 10 18 11",
      "M38 14 Q46 6 54 14 M38 14 L46 24 M54 14 L46 24",
      "M44.5 25.5 a1.5 1.5 0 1 0 3 0 a1.5 1.5 0 1 0 -3 0 M46 27 L56 46",
      "M26 51 q4 -3 8 0 t8 0 t8 0 t8 0",
    ],
  },
];

const RULES = [
  "You draw minimalist passport-stamp line art of travel destinations.",
  "Canvas: a 64×64 grid, viewBox 0 0 64 64, y grows downward. Every coordinate — including curve control points — must stay between 0 and 64.",
  "Style: stroke-only line drawing, 2px round strokes, no fills, no text. One recognisable landmark or landscape of the place, simple enough to read at 48px. Stand it on a ground line at y=52 (usually \"M4 52 H60\"; a wavy sea line near y=51 for coasts).",
  `Output ${MIN_PATHS}–${MAX_PATHS} path strings (aim for 3–6), each an SVG path "d" attribute using only the commands M L H V C S Q T A Z (absolute or relative). No <path> tags, no circle or rect elements — write circles as two arcs, e.g. "M45 14 a4 4 0 1 0 8 0 a4 4 0 1 0 -8 0". Keep each path under 400 characters.`,
  `label: the place's short name as a local would write it on a stamp, uppercase, at most ${MAX_LABEL_CHARS} characters.`,
  `fallback: the generic icon closest to this place, chosen from: ${FALLBACKS.join(", ")}. It is used only if your drawing cannot be rendered.`,
  "Answer with JSON only.",
].join("\n");

export function coverMessages({ place, season }: ReturnType<typeof coverSubject>, feedback?: string) {
  const examples = EXAMPLES.flatMap((example) => [
    { role: "user" as const, content: `Destination: ${example.place}` },
    {
      role: "assistant" as const,
      content: JSON.stringify({ label: example.label, fallback: example.fallback, paths: example.paths }),
    },
  ]);
  const ask = [`Destination: ${place}`, season ? SEASON_TOUCH[season] : null].filter(Boolean).join("\n");
  return [
    { role: "system" as const, content: RULES },
    ...examples,
    { role: "user" as const, content: ask },
    ...(feedback
      ? [{ role: "user" as const, content: `That drawing was rejected: ${feedback}. Draw it again, following the rules exactly.` }]
      : []),
  ];
}

const RESPONSE_FORMAT = {
  type: "json_schema" as const,
  json_schema: {
    name: "stamp",
    strict: true,
    schema: {
      type: "object",
      properties: {
        label: { type: "string" },
        fallback: { type: "string", enum: FALLBACKS },
        paths: { type: "array", items: { type: "string" } },
      },
      required: ["label", "fallback", "paths"],
      additionalProperties: false,
    },
  },
};

/**
 * Asks for a drawing, retries once with the reason it was rejected, and then
 * settles for the generic icon the model picked. An API error on the first
 * call is thrown, so the row is marked failed and retried later; once the
 * model has answered at all, the trip always ends up with a stamp.
 */
export async function drawStamp(
  subject: ReturnType<typeof coverSubject>,
  complete: (messages: ReturnType<typeof coverMessages>) => Promise<string>,
): Promise<StampArt> {
  let feedback: string | undefined;
  let fallback: Fallback | null = null;
  let label: unknown = null;

  for (let attempt = 0; attempt < 2; attempt++) {
    let content: string;
    try {
      content = await complete(coverMessages(subject, feedback));
    } catch (err) {
      if (attempt === 0) throw err;
      break;
    }
    try {
      const parsed = JSON.parse(content) as { label?: unknown; fallback?: unknown; paths?: unknown };
      if (isFallback(parsed.fallback)) fallback = parsed.fallback;
      if (typeof parsed.label === "string") label = parsed.label;
      const paths = validatePaths(parsed.paths);
      return { v: 1, label: stampLabel(label, subject.place), paths, fallback: false };
    } catch (err) {
      feedback = err instanceof StampError ? err.message : "the response was not valid JSON";
      console.warn(`trip-cover: attempt ${attempt + 1} rejected — ${feedback}`);
    }
  }

  const icon = fallback ?? "city";
  return { v: 1, label: stampLabel(label, subject.place), paths: FALLBACK_ICONS[icon], fallback: true };
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

    const subject = coverSubject(trip.name ?? "", trip.destination);
    if (!subject.place) throw new Error("Trip has nothing to draw");

    const together = new Together({ apiKey: togetherKey });
    const art = await drawStamp(subject, async (messages) => {
      const result = await together.chat.completions.create({
        model: MODEL,
        messages,
        response_format: RESPONSE_FORMAT,
        temperature: 0.4,
        max_tokens: 4000,
      });
      const content = result.choices?.[0]?.message?.content;
      if (!content) throw new Error("Model returned no content");
      return content;
    });

    const { error: saveError } = await supabase.rpc("set_trip_cover", {
      p_trip: tripId,
      p_art: art,
      p_subject: subject.place,
    });
    if (saveError) throw new Error(saveError.message);

    console.log(`trip-cover: ${tripId} ready${art.fallback ? " (fallback icon)" : ""}`);
  } catch (err) {
    console.error(`trip-cover: ${tripId} failed —`, err instanceof Error ? err.message : err);
    // Record the failure so the card stops waiting and the retry clock starts.
    await supabase.rpc("set_trip_cover", { p_trip: tripId, p_art: null, p_subject: null });
  }
};
