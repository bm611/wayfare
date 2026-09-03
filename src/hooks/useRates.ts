import { useEffect, useState } from "react";
import { loadRates, ratesNow } from "../lib/fx";

/**
 * Exchange rates for the current render. Serves the cached snapshot straight
 * away and re-renders once fresh ECB rates land.
 */
export function useRates() {
  const [snapshot, setSnapshot] = useState(() => ratesNow());

  useEffect(() => {
    let active = true;
    void loadRates().then((next) => {
      if (active) setSnapshot(next);
    });
    return () => {
      active = false;
    };
  }, []);

  return snapshot;
}
