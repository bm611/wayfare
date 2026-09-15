import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import type { SetStateAction } from "react";
import { errorMessage } from "../lib/errors";

/** A keyed read with cancellation; local edits invalidate any older read. */
export function useRemote<T>(key: string | undefined, initial: T, fetch: (signal: AbortSignal) => Promise<T>) {
  const [state, setState] = useState({ key, data: initial, loading: !!key, error: null as string | null });
  const empty = useRef(initial);
  const identity = useRef(key);
  useLayoutEffect(() => { identity.current = key; }, [key]);
  const request = useRef<AbortController | null>(null);
  const reload = useCallback(async () => {
    request.current?.abort();
    if (!key) return;
    const controller = new AbortController();
    request.current = controller;
    try {
      const data = await fetch(controller.signal);
      if (!controller.signal.aborted && identity.current === key) setState({ key, data, loading: false, error: null });
    } catch (error) {
      if (!controller.signal.aborted && identity.current === key) setState((previous) => ({
        ...previous, key, data: previous.key === key ? previous.data : empty.current, loading: false, error: errorMessage(error, "Could not load data."),
      }));
    }
  }, [key, fetch]);
  useEffect(() => {
    // oxlint-disable-next-line react/set-state-in-effect -- publication follows the asynchronous read.
    void reload();
    return () => request.current?.abort();
  }, [reload]);
  const setData = useCallback((update: SetStateAction<T>) => {
    if (identity.current !== key) return;
    request.current?.abort();
    setState((previous) => {
      const data = typeof update === "function"
        ? (update as (value: T) => T)(previous.key === key ? previous.data : empty.current) : update;
      if (previous.key === key && data === previous.data && !previous.loading && !previous.error) return previous;
      return { key, data, loading: false, error: null };
    });
  }, [key]);
  return { data: state.key === key ? state.data : initial, setData,
    loading: state.key === key ? state.loading : !!key, error: state.key === key ? state.error : null, reload };
}
