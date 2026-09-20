import test from "node:test";
import assert from "node:assert/strict";

import {
  coverSubject,
  coverPrompt,
} from "../../netlify/functions/trip-cover-background.mts";

// The split between the two form fields is the whole contract: destination
// decides the place and the words on the base, the name only decides the
// season. These cases are the ones that were getting it wrong.

test("destination decides the place, name decides the season", () => {
  const subject = coverSubject("finland winter '26", "Finland", null);
  assert.equal(subject.place, "Finland");
  assert.equal(subject.lettering, "FINLAND");
  assert.equal(subject.season, "winter");

  const prompt = coverPrompt(subject);
  assert.match(prompt, /diorama of Finland/);
  assert.match(prompt, /snow lying on the roofs/);
  assert.match(prompt, /"FINLAND"/);
  // The name must never reach the scene description as a subject.
  assert.doesNotMatch(prompt, /winter '26/);
});

test("a descriptive name never overrides the destination's geography", () => {
  const prompt = coverPrompt(coverSubject("Ski trip!! 2026", "Tokyo", "2026-02-03"));
  assert.match(prompt, /diorama of Tokyo/);
  assert.match(prompt, /"TOKYO"/);
  assert.match(prompt, /"2026"/);
  assert.match(prompt, /snow lying on the roofs/);
});

test("with no destination the name still yields a clean place", () => {
  const subject = coverSubject("Iceland - Winter '26", null, null);
  assert.equal(subject.place, "Iceland");
  assert.equal(subject.lettering, "ICELAND");
  assert.equal(subject.season, "winter");
  assert.doesNotMatch(coverPrompt(subject), /Winter '26/);
});

test("an unseasonal name falls back to neutral light", () => {
  const subject = coverSubject("Work trip", "Lisbon", null);
  assert.equal(subject.season, null);
  const prompt = coverPrompt(subject);
  assert.match(prompt, /Soft afternoon sunlight/);
  assert.equal(subject.year, null);
  assert.doesNotMatch(prompt, /"null"/);
});

test("every cover carries the same series direction", () => {
  const a = coverPrompt(coverSubject("Summer escape", "Nice", null));
  const b = coverPrompt(coverSubject("Winter break", "Oslo", null));
  const series = "Elevated orthographic camera at 35 degrees";
  assert.ok(a.includes(series) && b.includes(series));
  // ...but the palette is no longer pinned to terracotta for every destination.
  assert.doesNotMatch(a, /warm terracotta accents/);
  assert.match(b, /vernacular architecture of Oslo/);
});
