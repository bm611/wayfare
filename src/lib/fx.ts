/**
 * Currency conversion, anchored to the euro.
 *
 * Rates come from Frankfurter (European Central Bank reference rates, no key
 * required) and are cached for twelve hours. If the network is unavailable the
 * app falls back to a bundled snapshot rather than refusing to record a cost —
 * whatever rate was actually used is stored on the expense, so a stale figure
 * stays traceable.
 */

const ENDPOINT = "https://api.frankfurter.dev/v1/latest?base=EUR";
const CACHE_KEY = "wayfare.fx.v1";
const MAX_AGE_MS = 12 * 60 * 60 * 1000;

/**
 * Units of each currency per 1 EUR. Offline snapshot only — ECB reference
 * rates as of 2026-09-02. The rate actually used is stored on every expense,
 * so a figure recorded against this table stays traceable.
 */
const FALLBACK: Record<string, number> = {
  EUR: 1, USD: 1.1578, GBP: 0.8587, CHF: 0.9424, SEK: 11.1575, NOK: 10.809,
  DKK: 7.475, PLN: 4.3265, CZK: 24.191, HUF: 368.2, RON: 5.2558, ISK: 140.6,
  TRY: 55.9145, JPY: 184.78, CNY: 7.7822, HKD: 9.0801, SGD: 1.4741,
  KRW: 1577.57, INR: 109.962, IDR: 20511.18, MYR: 4.6839, PHP: 72.415,
  THB: 38.468, ILS: 3.5064, AUD: 1.6199, NZD: 1.9868, CAD: 1.6122,
  BRL: 5.9635, MXN: 19.6835, ZAR: 18.6341,
};

export const BASE_CURRENCY = "EUR";

/** Every currency the converter can handle, euro first. */
export const CURRENCIES = Object.keys(FALLBACK);

type Snapshot = { rates: Record<string, number>; date: string; fetchedAt: number; stale: boolean };

let memory: Snapshot | null = null;
let inFlight: Promise<Snapshot> | null = null;

function readCache(): Snapshot | null {
  try {
    const raw = localStorage.getItem(CACHE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Snapshot;
    if (!parsed?.rates?.USD) return null;
    return parsed;
  } catch {
    return null;
  }
}

function writeCache(snapshot: Snapshot) {
  try {
    localStorage.setItem(CACHE_KEY, JSON.stringify(snapshot));
  } catch {
    // Private browsing or a full quota — the in-memory copy still serves.
  }
}

export function ratesNow(): Snapshot {
  if (memory) return memory;
  const cached = readCache();
  if (cached) {
    memory = cached;
    return cached;
  }
  return { rates: FALLBACK, date: "snapshot", fetchedAt: 0, stale: true };
}

export async function loadRates(): Promise<Snapshot> {
  const current = memory ?? readCache();
  if (current && !current.stale && Date.now() - current.fetchedAt < MAX_AGE_MS) {
    memory = current;
    return current;
  }
  if (inFlight) return inFlight;

  inFlight = (async () => {
    try {
      const res = await fetch(ENDPOINT);
      if (!res.ok) throw new Error(`FX request failed: ${res.status}`);
      const body = (await res.json()) as { date: string; rates: Record<string, number> };
      const snapshot: Snapshot = {
        rates: { EUR: 1, ...body.rates },
        date: body.date,
        fetchedAt: Date.now(),
        stale: false,
      };
      memory = snapshot;
      writeCache(snapshot);
      return snapshot;
    } catch {
      const fallback = current ?? { rates: FALLBACK, date: "snapshot", fetchedAt: 0, stale: true };
      memory = fallback;
      return fallback;
    } finally {
      inFlight = null;
    }
  })();

  return inFlight;
}

/** How many units of `to` one unit of `from` buys. */
export function rateBetween(from: string, to: string, rates = ratesNow().rates) {
  if (from === to) return 1;
  const fromRate = rates[from];
  const toRate = rates[to];
  if (!fromRate || !toRate) return null;
  return toRate / fromRate;
}

export function convert(amount: number, from: string, to: string, rates = ratesNow().rates) {
  const rate = rateBetween(from, to, rates);
  if (rate === null) return null;
  return Math.round(amount * rate * 100) / 100;
}
