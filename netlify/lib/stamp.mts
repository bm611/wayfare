/**
 * Passport-stamp line art: validation and normalisation of model-drawn SVG
 * paths, plus the built-in icons used when a drawing cannot be trusted.
 *
 * Everything a client renders comes out of `normalizePath`, which reduces any
 * accepted path to absolute M/L/C/Q/Z commands. That keeps the bounds check
 * exact (control points included) and lets the Android and iOS parsers stay a
 * few dozen lines long.
 */

/** The drawing grid. Every coordinate, control points included, lies inside it. */
export const GRID = 64;
export const MIN_PATHS = 2;
export const MAX_PATHS = 8;
/** Per path as the model wrote it, before normalisation. */
export const MAX_PATH_CHARS = 600;
export const MAX_TOTAL_CHARS = 3000;
/** Per path after normalisation, where arcs have become several cubics. */
const MAX_SEGMENTS = 96;
export const MAX_LABEL_CHARS = 16;

export type StampArt = { v: 1; label: string; paths: string[]; fallback: boolean };

export class StampError extends Error {}

// ---------------------------------------------------------------------------
// Path data

const COMMANDS = "MmLlHhVvCcSsQqTtAaZz";

class Scanner {
  i = 0;
  readonly s: string;
  constructor(s: string) {
    this.s = s;
  }

  skip() {
    while (this.i < this.s.length && /[\s,]/.test(this.s[this.i])) this.i++;
  }

  done() {
    this.skip();
    return this.i >= this.s.length;
  }

  /** True when the next token starts a number rather than a command. */
  atNumber() {
    this.skip();
    return this.i < this.s.length && /[0-9.+-]/.test(this.s[this.i]);
  }

  number() {
    this.skip();
    const match = /^[+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?/.exec(this.s.slice(this.i));
    if (!match) throw new StampError(`expected a number at ${this.i}`);
    this.i += match[0].length;
    const value = Number(match[0]);
    if (!Number.isFinite(value)) throw new StampError("number out of range");
    return value;
  }

  /** Arc flags may be written without separators ("a4 4 0 01 8 0"). */
  flag() {
    this.skip();
    const ch = this.s[this.i];
    if (ch !== "0" && ch !== "1") throw new StampError(`expected an arc flag at ${this.i}`);
    this.i++;
    return ch === "1";
  }
}

type Seg = ["M" | "L", number, number] | ["Q", number, number, number, number] | ["C", number, number, number, number, number, number] | ["Z"];

/**
 * Parses one path's `d`, returning it as absolute M/L/C/Q/Z with coordinates
 * rounded to two decimals. Throws a StampError on anything else: unknown
 * commands, stray characters, a point off the grid, or too much of it.
 */
export function normalizePath(d: string): string {
  if (typeof d !== "string") throw new StampError("path is not a string");
  if (d.length > MAX_PATH_CHARS) throw new StampError("path is too long");
  if (/[^MmLlHhVvCcSsQqTtAaZz0-9.,eE+\-\s]/.test(d)) throw new StampError("path has characters outside path data");

  const scan = new Scanner(d.trim());
  const out: Seg[] = [];
  let x = 0, y = 0, startX = 0, startY = 0;
  // The previous segment's second control point, for S and T reflection.
  let lastCubic: [number, number] | null = null;
  let lastQuad: [number, number] | null = null;
  while (!scan.done()) {
    // Commands with arguments consume every repeated group below, so a number
    // here means one with nothing to belong to: a leading number, or one after Z.
    const command = scan.s[scan.i];
    if (!COMMANDS.includes(command)) throw new StampError(`unexpected number at ${scan.i}`);
    scan.i++;
    if (out.length === 0 && command !== "M" && command !== "m") throw new StampError("path must start with a moveto");

    let op = command.toLowerCase();
    const rel = command === op;

    if (op === "z") {
      out.push(["Z"]);
      x = startX; y = startY;
      lastCubic = lastQuad = null;
      continue;
    }

    // One group of arguments per pass, repeated while numbers follow.
    do {
      const ox = rel ? x : 0, oy = rel ? y : 0;
      let nextCubic: [number, number] | null = null;
      let nextQuad: [number, number] | null = null;
      switch (op) {
        case "m": {
          x = ox + scan.number(); y = oy + scan.number();
          startX = x; startY = y;
          out.push(["M", x, y]);
          // Pairs after a moveto are implicit linetos.
          op = "l";
          break;
        }
        case "l": {
          x = ox + scan.number(); y = oy + scan.number();
          out.push(["L", x, y]);
          break;
        }
        case "h": {
          x = (rel ? x : 0) + scan.number();
          out.push(["L", x, y]);
          break;
        }
        case "v": {
          y = (rel ? y : 0) + scan.number();
          out.push(["L", x, y]);
          break;
        }
        case "c": {
          const x1 = ox + scan.number(), y1 = oy + scan.number();
          const x2 = ox + scan.number(), y2 = oy + scan.number();
          x = ox + scan.number(); y = oy + scan.number();
          out.push(["C", x1, y1, x2, y2, x, y]);
          nextCubic = [x2, y2];
          break;
        }
        case "s": {
          const [x1, y1]: [number, number] = lastCubic ? [2 * x - lastCubic[0], 2 * y - lastCubic[1]] : [x, y];
          const x2 = ox + scan.number(), y2 = oy + scan.number();
          x = ox + scan.number(); y = oy + scan.number();
          out.push(["C", x1, y1, x2, y2, x, y]);
          nextCubic = [x2, y2];
          break;
        }
        case "q": {
          const x1 = ox + scan.number(), y1 = oy + scan.number();
          x = ox + scan.number(); y = oy + scan.number();
          out.push(["Q", x1, y1, x, y]);
          nextQuad = [x1, y1];
          break;
        }
        case "t": {
          const x1: number = lastQuad ? 2 * x - lastQuad[0] : x;
          const y1: number = lastQuad ? 2 * y - lastQuad[1] : y;
          x = ox + scan.number(); y = oy + scan.number();
          out.push(["Q", x1, y1, x, y]);
          nextQuad = [x1, y1];
          break;
        }
        case "a": {
          const rx = scan.number(), ry = scan.number(), rotation = scan.number();
          const large = scan.flag(), sweep = scan.flag();
          const x2 = ox + scan.number(), y2 = oy + scan.number();
          for (const cubic of arcToCubics(x, y, rx, ry, rotation, large, sweep, x2, y2)) out.push(cubic);
          x = x2; y = y2;
          break;
        }
      }
      lastCubic = nextCubic;
      lastQuad = nextQuad;
      if (out.length > MAX_SEGMENTS) throw new StampError("path has too many segments");
    } while (scan.atNumber());
  }

  if (out.length < 2) throw new StampError("path draws nothing");

  return out
    .map(([cmd, ...nums]) => {
      for (const n of nums) {
        const r = round(n);
        if (r < 0 || r > GRID) throw new StampError(`point ${r} is off the ${GRID}×${GRID} grid`);
      }
      return nums.length ? `${cmd}${nums.map((n) => String(round(n))).join(" ")}` : cmd;
    })
    .join(" ");
}

function round(n: number) {
  // `+ 0` folds -0 into 0 so it prints as "0".
  return Math.round(n * 100) / 100 + 0;
}

/**
 * SVG elliptical arc to cubic Béziers, via the centre parameterisation in the
 * SVG spec (F.6.5), split into quarter turns or less so each cubic stays close
 * to the true curve. Each cubic's control points sit within the arc's bounding
 * box, so the grid check on the output is as strict as it looks.
 */
function arcToCubics(
  x1: number, y1: number, rxIn: number, ryIn: number, rotation: number,
  large: boolean, sweep: boolean, x2: number, y2: number,
): Seg[] {
  if (x1 === x2 && y1 === y2) return [];
  let rx = Math.abs(rxIn), ry = Math.abs(ryIn);
  if (rx === 0 || ry === 0) return [["L", x2, y2]];

  const phi = (rotation * Math.PI) / 180;
  const cos = Math.cos(phi), sin = Math.sin(phi);
  const dx = (x1 - x2) / 2, dy = (y1 - y2) / 2;
  const x1p = cos * dx + sin * dy;
  const y1p = -sin * dx + cos * dy;

  const lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry);
  if (lambda > 1) {
    rx *= Math.sqrt(lambda);
    ry *= Math.sqrt(lambda);
  }

  const num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p;
  const den = rx * rx * y1p * y1p + ry * ry * x1p * x1p;
  const coef = (large !== sweep ? 1 : -1) * Math.sqrt(Math.max(0, num / den));
  const cxp = (coef * rx * y1p) / ry;
  const cyp = (-coef * ry * x1p) / rx;
  const cx = cos * cxp - sin * cyp + (x1 + x2) / 2;
  const cy = sin * cxp + cos * cyp + (y1 + y2) / 2;

  const angle = (ux: number, uy: number, vx: number, vy: number) =>
    Math.atan2(ux * vy - uy * vx, ux * vx + uy * vy);
  const theta1 = angle(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry);
  let delta = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry);
  if (!sweep && delta > 0) delta -= 2 * Math.PI;
  if (sweep && delta < 0) delta += 2 * Math.PI;

  const segments = Math.max(1, Math.ceil(Math.abs(delta) / (Math.PI / 2) - 1e-9));
  const step = delta / segments;
  const k = (4 / 3) * Math.tan(step / 4);

  const point = (a: number): [number, number] => [
    cx + rx * Math.cos(a) * cos - ry * Math.sin(a) * sin,
    cy + rx * Math.cos(a) * sin + ry * Math.sin(a) * cos,
  ];
  const tangent = (a: number): [number, number] => [
    -rx * Math.sin(a) * cos - ry * Math.cos(a) * sin,
    -rx * Math.sin(a) * sin + ry * Math.cos(a) * cos,
  ];

  const out: Seg[] = [];
  for (let i = 0; i < segments; i++) {
    const a1 = theta1 + i * step, a2 = a1 + step;
    const [px1, py1] = point(a1);
    const [tx1, ty1] = tangent(a1);
    const [tx2, ty2] = tangent(a2);
    // Land exactly on the requested endpoint rather than a float away from it.
    const [px2, py2] = i === segments - 1 ? [x2, y2] : point(a2);
    out.push(["C", px1 + k * tx1, py1 + k * ty1, px2 - k * tx2, py2 - k * ty2, px2, py2]);
  }
  return out;
}

// ---------------------------------------------------------------------------
// Drawings

/**
 * Checks a model's `paths` and returns them normalised, or throws. Limits are
 * checked on the raw strings first so a runaway response is never parsed.
 */
export function validatePaths(paths: unknown): string[] {
  if (!Array.isArray(paths)) throw new StampError("paths is not a list");
  if (paths.length < MIN_PATHS || paths.length > MAX_PATHS) {
    throw new StampError(`expected ${MIN_PATHS}–${MAX_PATHS} paths, got ${paths.length}`);
  }
  if (!paths.every((d): d is string => typeof d === "string")) throw new StampError("every path must be a string");
  const total = paths.reduce((sum, d) => sum + d.length, 0);
  if (total > MAX_TOTAL_CHARS) throw new StampError("drawing is too long");
  return paths.map((d, i) => {
    try {
      return normalizePath(d);
    } catch (err) {
      throw new StampError(`path ${i + 1}: ${err instanceof Error ? err.message : err}`);
    }
  });
}

/**
 * The words under the drawing. The model's label if it is short and plain,
 * otherwise the place itself, and failing that its first word.
 */
export function stampLabel(label: unknown, place: string) {
  const clean = (s: string) =>
    s.normalize("NFC").replace(/[^\p{L}\p{N} '’.-]+/gu, " ").replace(/\s+/g, " ").trim().toLocaleUpperCase("en");
  for (const candidate of [typeof label === "string" ? label : "", place, place.split(/\s+/)[0] ?? ""]) {
    const c = clean(candidate);
    if (c && [...c].length <= MAX_LABEL_CHARS) return c;
  }
  return [...clean(place)].slice(0, MAX_LABEL_CHARS).join("").trim();
}

// ---------------------------------------------------------------------------
// Fallback icons

/**
 * Generic marks for when the model's own drawing fails validation twice. Same
 * grid and ground line as the drawings; written in full path syntax and passed
 * through `normalizePath` below, which also proves they are valid.
 */
const FALLBACK_SOURCE = {
  mountain: [
    "M6 52 L26 18 L34 30 L40 22 L58 52",
    "M20 28 L24 32 L28 27 L32 31",
    "M46 14 a4 4 0 1 0 8 0 a4 4 0 1 0 -8 0",
    "M4 52 H60",
  ],
  beach: [
    "M12 30 Q24 17 36 30 Z",
    "M24 30 L28 50",
    "M46 14 a4 4 0 1 0 8 0 a4 4 0 1 0 -8 0",
    "M4 51 q4 -3 8 0 t8 0 t8 0 t8 0 t8 0 t8 0 t8 0",
  ],
  island: [
    "M12 45 Q24 32 34 37 Q44 30 54 45",
    "M30 35 Q31 28 29 22",
    "M29 22 Q23 19 20 23 M29 22 Q35 18 39 22 M29 22 Q27 16 22 15 M29 22 Q32 15 37 15",
    "M4 51 q4 -3 8 0 t8 0 t8 0 t8 0 t8 0 t8 0 t8 0",
  ],
  city: [
    "M6 52 V30 H16 V52",
    "M16 52 V18 H28 V52",
    "M28 52 V36 H38 V52",
    "M38 52 V24 H50 V52 M44 24 V16",
    "M20 24 h4 M20 30 h4 M20 36 h4 M42 30 h4 M42 36 h4",
    "M4 52 H60",
  ],
  village: [
    "M8 52 V36 L16 28 L24 36 V52",
    "M24 52 V32 L34 22 L44 32 V52",
    "M44 52 V40 L51 34 L58 40 V52",
    "M31 52 V44 H37 V52",
    "M4 52 H60",
  ],
  temple: [
    "M8 22 L32 10 L56 22 Z",
    "M8 22 H56 M10 26 H54",
    "M14 26 V48 M23 26 V48 M32 26 V48 M41 26 V48 M50 26 V48",
    "M8 48 H56 M4 52 H60",
  ],
  tower: [
    "M22 52 L29 14 H35 L42 52",
    "M26 34 H38 M24 44 H40",
    "M29 14 L32 6 L35 14",
    "M28 52 v-4 a4 4 0 0 1 8 0 v4",
    "M4 52 H60",
  ],
  lake: [
    "M4 40 L18 22 L28 34 L38 20 L60 40",
    "M4 40 H60",
    "M10 46 H26 M34 46 H54",
    "M16 52 H48",
  ],
  desert: [
    "M4 44 Q18 34 30 42 Q44 32 60 42",
    "M20 52 V34 a3 3 0 0 1 6 0 V52 M20 42 H16 V36 M26 44 H30 V38",
    "M43 16 a5 5 0 1 0 10 0 a5 5 0 1 0 -10 0",
    "M4 52 H60",
  ],
  forest: [
    "M8 46 L16 24 L24 46 Z M16 46 V52",
    "M26 46 L36 14 L46 46 Z M36 46 V52",
    "M44 46 L51 28 L58 46 Z M51 46 V52",
    "M4 52 H60",
  ],
  bridge: [
    "M18 52 V16 M46 52 V16",
    "M4 36 Q12 36 18 16 Q32 40 46 16 Q52 36 60 36",
    "M4 38 H60",
    "M25 38 V25 M32 38 V28 M39 38 V25",
    "M4 51 q4 -3 8 0 t8 0 t8 0 t8 0 t8 0 t8 0 t8 0",
  ],
  castle: [
    "M8 52 V20 H11 V17 H14 V20 H17 V17 H20 V52",
    "M44 52 V20 H47 V17 H50 V20 H53 V17 H56 V52",
    "M20 30 H24 V27 H28 V30 H32 V27 H36 V30 H40 V27 H44",
    "M26 52 v-8 a6 6 0 0 1 12 0 v8",
    "M50 17 V10 L56 12 L50 14",
    "M4 52 H60",
  ],
  harbour: [
    "M16 44 H44 L40 50 H20 Z",
    "M30 44 V18 L44 40 H30",
    "M46 14 a4 4 0 1 0 8 0 a4 4 0 1 0 -8 0",
    "M4 51 q4 -3 8 0 t8 0 t8 0 t8 0 t8 0 t8 0 t8 0",
  ],
} as const;

export type Fallback = keyof typeof FALLBACK_SOURCE;
export const FALLBACKS = Object.keys(FALLBACK_SOURCE) as Fallback[];

export const FALLBACK_ICONS = Object.fromEntries(
  FALLBACKS.map((name) => [name, validatePaths([...FALLBACK_SOURCE[name]])]),
) as Record<Fallback, string[]>;

export function isFallback(value: unknown): value is Fallback {
  return typeof value === "string" && Object.hasOwn(FALLBACK_SOURCE, value);
}
