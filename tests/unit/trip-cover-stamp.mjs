import test from "node:test";
import assert from "node:assert/strict";

import {
  coverSubject,
  coverMessages,
  drawStamp,
} from "../../netlify/functions/trip-cover-background.mts";
import {
  FALLBACKS,
  FALLBACK_ICONS,
  StampError,
  normalizePath,
  stampLabel,
  validatePaths,
} from "../../netlify/lib/stamp.mts";
import { stampMonth, stampStyle } from "../../src/lib/stamp.ts";

// --- Subject: destination decides the place, the name only the season.

test("destination decides the place, name decides the season", () => {
  const subject = coverSubject("finland winter '26", "Finland");
  assert.deepEqual(subject, { place: "Finland", season: "winter" });

  const ask = coverMessages(subject).at(-1).content;
  assert.match(ask, /Destination: Finland/);
  assert.match(ask, /snow/);
  assert.doesNotMatch(ask, /winter '26/);
});

test("a descriptive name never overrides the destination", () => {
  assert.deepEqual(coverSubject("Ski trip!! 2026", "Tokyo"), { place: "Tokyo", season: "winter" });
});

test("with no destination the name still yields a clean place", () => {
  assert.deepEqual(coverSubject("Iceland - Winter '26", null), { place: "Iceland", season: "winter" });
});

test("an unseasonal trip asks for no seasonal touch", () => {
  const ask = coverMessages(coverSubject("Work trip", "Lisbon")).at(-1).content;
  assert.equal(ask, "Destination: Lisbon");
});

test("every few-shot example is itself a valid drawing", () => {
  const messages = coverMessages(coverSubject("x", "Lisbon"));
  const answers = messages.filter((m) => m.role === "assistant").map((m) => JSON.parse(m.content));
  assert.equal(answers.length, 8);
  for (const answer of answers) {
    assert.doesNotThrow(() => validatePaths(answer.paths), answer.label);
    assert.ok(FALLBACKS.includes(answer.fallback));
  }
});

// --- Path validation and normalisation.

test("normalises relative, shorthand and arc commands to absolute M/L/C/Q/Z", () => {
  assert.equal(normalizePath("M4 52 H60"), "M4 52 L60 52");
  assert.equal(normalizePath("m10 10 5 0 0 5 z"), "M10 10 L15 10 L15 15 Z");
  assert.equal(normalizePath("M4 51 q4 -3 8 0 t8 0"), "M4 51 Q8 48 12 51 Q16 54 20 51");
  assert.equal(normalizePath("M10 10 C12 8 14 8 16 10 S20 12 22 10"), "M10 10 C12 8 14 8 16 10 C18 12 20 12 22 10");
  // A semicircular arch: two quarter cubics, landing exactly on the endpoint.
  assert.equal(
    normalizePath("M12 52 v-6 a3 3 0 0 1 6 0 v6"),
    "M12 52 L12 46 C12 44.34 13.34 43 15 43 C16.66 43 18 44.34 18 46 L18 52",
  );
  // Compact arc flags, as some writers emit them.
  assert.equal(normalizePath("M12 46 a3 3 0 016 0"), normalizePath("M12 46 a3 3 0 0 1 6 0"));
});

test("only path data on the grid gets through", () => {
  const rejects = {
    "off the grid": "M0 0 L70 10",
    "negative coordinate": "M2 2 l-4 0",
    "control point off the grid": "M4 52 Q32 -10 60 52",
    "markup": 'M1 1 L2 2"/><script>',
    "unknown command": "M1 1 R2 2",
    "no moveto": "L1 1 L2 2",
    "stray number": "M10 10 Z 5",
    "nothing drawn": "M1 1",
    "too long": `M1 1 ${"L2 2 ".repeat(200)}`,
  };
  for (const [why, d] of Object.entries(rejects)) {
    assert.throws(() => normalizePath(d), StampError, why);
  }
});

test("drawings are capped in count and size", () => {
  assert.throws(() => validatePaths(["M4 52 H60"]), /expected 2–8 paths/);
  assert.throws(() => validatePaths(Array(9).fill("M4 52 H60")), /expected 2–8 paths/);
  assert.throws(() => validatePaths("M4 52 H60"), /not a list/);
  assert.throws(() => validatePaths(["M4 52 H60", 42]), /must be a string/);
  assert.throws(() => validatePaths(Array(8).fill(`M1 1 ${"L2 2 ".repeat(90)}`)), /too long/);
  assert.throws(() => validatePaths(["M4 52 H60", "M1 1 L99 1"]), /path 2: point 99/);
});

test("every fallback icon is a valid, normalised drawing", () => {
  for (const name of FALLBACKS) {
    const paths = FALLBACK_ICONS[name];
    assert.ok(paths.length >= 2 && paths.length <= 8, name);
    for (const d of paths) assert.match(d, /^M[\d. MLCQZ]+$/, name);
  }
});

test("labels stay short and plain", () => {
  assert.equal(stampLabel("Lisboa", "Lisbon"), "LISBOA");
  assert.equal(stampLabel("<b>Roma</b>", "Rome"), "B ROMA B");
  assert.equal(stampLabel("A label far too long to print", "Rio de Janeiro"), "RIO DE JANEIRO");
  assert.equal(stampLabel(null, "San Francisco Bay Area"), "SAN");
  assert.equal(stampLabel(42, "Kyoto"), "KYOTO");
});

// --- Retry and fallback.

const good = JSON.stringify({ label: "Lisboa", fallback: "city", paths: ["M4 52 H60", "M10 52 V30 H20 V52"] });
const bad = JSON.stringify({ label: "Lisboa", fallback: "harbour", paths: ["M4 52 H60", "M0 0 L90 90"] });

test("a valid first drawing is stored as drawn", async () => {
  let calls = 0;
  const art = await drawStamp({ place: "Lisbon", season: null }, async () => (calls++, good));
  assert.equal(calls, 1);
  assert.deepEqual(art, { v: 1, label: "LISBOA", paths: ["M4 52 L60 52", "M10 52 L10 30 L20 30 L20 52"], fallback: false });
});

test("a rejected drawing is retried once with the reason", async () => {
  const seen = [];
  const art = await drawStamp({ place: "Lisbon", season: null }, async (messages) => {
    seen.push(messages.at(-1).content);
    return seen.length === 1 ? bad : good;
  });
  assert.equal(seen.length, 2);
  assert.match(seen[1], /rejected: path 2: point 90/);
  assert.equal(art.fallback, false);
});

test("two rejections fall back to the icon the model picked", async () => {
  let calls = 0;
  const art = await drawStamp({ place: "Lisbon", season: null }, async () => (calls++, bad));
  assert.equal(calls, 2);
  assert.deepEqual(art, { v: 1, label: "LISBOA", paths: FALLBACK_ICONS.harbour, fallback: true });
});

test("unparseable answers fall back to a generic city", async () => {
  const art = await drawStamp({ place: "Lisbon", season: null }, async () => "not json");
  assert.deepEqual(art, { v: 1, label: "LISBON", paths: FALLBACK_ICONS.city, fallback: true });
});

test("an API error before any answer is a failure, not a fallback", async () => {
  await assert.rejects(
    drawStamp({ place: "Lisbon", season: null }, async () => { throw new Error("503"); }),
    /503/,
  );
});

// --- How the stamp sits on the card. The same fixtures are pinned in the
// Android StampTest.kt and iOS StampTests.swift; change all three together.

test("stamp style is derived from the trip id", () => {
  assert.deepEqual(stampStyle("0f8e2a4c-1b3d-4e5f-8a9b-0c1d2e3f4a5b"), { tint: "lilac", tilt: 2.5, arched: false });
  assert.deepEqual(stampStyle("7c9e6679-7425-40de-944b-e07fc1f90ae7"), { tint: "peach", tilt: -1.5, arched: true });
  assert.deepEqual(stampStyle("a3bb189e-8bf9-3888-9912-ace4e6543002"), { tint: "sage", tilt: -1.5, arched: false });
  assert.deepEqual(stampStyle("e1f2d3c4-b5a6-4789-9abc-def012345678"), { tint: "peach", tilt: 2.5, arched: false });
});

test("stamp month is the start month", () => {
  assert.equal(stampMonth("2026-09-03"), "SEP 2026");
  assert.equal(stampMonth(null), null);
});
