const SYMBOLS: Record<string, string> = {
  EUR: "\u20ac", USD: "$", GBP: "\u00a3", CHF: "CHF ", SEK: "kr ", NOK: "kr ",
  DKK: "kr ", PLN: "z\u0142 ", CZK: "K\u010d ", HUF: "Ft ", RON: "lei ",
  ISK: "kr ", TRY: "\u20ba", JPY: "\u00a5", CNY: "\u00a5", HKD: "HK$", SGD: "S$",
  KRW: "\u20a9", INR: "\u20b9", IDR: "Rp", MYR: "RM", PHP: "\u20b1", THB: "\u0e3f",
  ILS: "\u20aa", AUD: "A$", NZD: "NZ$", CAD: "C$", BRL: "R$", MXN: "MX$",
  ZAR: "R",
};

export function symbolFor(currency: string) {
  return SYMBOLS[currency] ?? `${currency} `;
}

/** 2140.5 -> "2,140.50". Cents are dropped once a number gets long enough to crowd a phone. */
export function money(value: number, opts: { cents?: boolean } = {}) {
  const cents = opts.cents ?? Math.abs(value) < 100_000;
  return value.toLocaleString("en-US", {
    minimumFractionDigits: cents ? 2 : 0,
    maximumFractionDigits: cents ? 2 : 0,
  });
}

export function compact(value: number) {
  if (Math.abs(value) < 1000) return money(value, { cents: false });
  return value.toLocaleString("en-US", { notation: "compact", maximumFractionDigits: 1 });
}

/** Parses a date-only string without letting the local timezone shift the day. */
export function parseDay(iso: string) {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(y, (m ?? 1) - 1, d ?? 1);
}

export function todayISO() {
  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

export function shortDate(iso: string) {
  const d = parseDay(iso);
  return `${MONTHS[d.getMonth()]} ${d.getDate()}`;
}

export function dateRange(start: string | null, end: string | null) {
  if (!start && !end) return "Dates open";
  if (start && !end) return `From ${shortDate(start)}`;
  if (!start && end) return `Until ${shortDate(end!)}`;
  return `${shortDate(start!)} — ${shortDate(end!)}`;
}

export function dayLabel(iso: string) {
  const d = parseDay(iso);
  const today = parseDay(todayISO());
  const diff = Math.round((d.getTime() - today.getTime()) / 86_400_000);
  if (diff === 0) return "Today";
  if (diff === -1) return "Yesterday";
  return d.toLocaleDateString("en-US", { weekday: "short", month: "short", day: "numeric" });
}

export type TripPhase =
  | { kind: "upcoming"; days: number }
  | { kind: "active"; day: number; total: number }
  | { kind: "past" }
  | { kind: "undated" };

export function tripPhase(trip: { start_date: string | null; end_date: string | null }): TripPhase {
  const { start_date, end_date } = trip;
  if (!start_date && !end_date) return { kind: "undated" };
  const today = parseDay(todayISO()).getTime();
  const start = start_date ? parseDay(start_date).getTime() : today;
  const end = end_date ? parseDay(end_date).getTime() : start;

  if (today < start) return { kind: "upcoming", days: Math.ceil((start - today) / 86_400_000) };
  if (today > end) return { kind: "past" };
  return {
    kind: "active",
    day: Math.round((today - start) / 86_400_000) + 1,
    total: Math.round((end - start) / 86_400_000) + 1,
  };
}

/** "Day 3 of 7", "In 26 days": the phase as the native apps word it. */
export function phaseLabel(phase: TripPhase) {
  switch (phase.kind) {
    case "active":
      return `Day ${phase.day} of ${phase.total}`;
    case "upcoming":
      return `In ${phase.days} ${phase.days === 1 ? "day" : "days"}`;
    case "past":
      return "Past trip";
    default:
      return "Dates open";
  }
}
