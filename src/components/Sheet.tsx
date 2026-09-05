import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import type { ReactNode } from "react";
import { AnimatePresence, motion, useDragControls } from "motion/react";
import { X } from "@phosphor-icons/react";

/**
 * Bottom sheet. Dragging is bound to the grabber only, so the form fields
 * inside never fight the gesture.
 */
export function Sheet({
  open,
  onClose,
  title,
  eyebrow,
  children,
  footer,
  dirty = false,
  busy = false,
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  eyebrow?: string;
  children: ReactNode;
  footer?: ReactNode;
  dirty?: boolean;
  busy?: boolean;
}) {
  const controls = useDragControls();
  const overlayRef = useRef<HTMLDivElement>(null);
  const dialogRef = useRef<HTMLDivElement>(null);
  const keepEditingRef = useRef<HTMLButtonElement>(null);
  const restoreFocusRef = useRef<HTMLElement | null>(null);
  const onCloseRef = useRef(onClose);
  const dirtyRef = useRef(dirty);
  const busyRef = useRef(busy);
  const [confirmingDiscard, setConfirmingDiscard] = useState(false);
  const [viewport, setViewport] = useState<{ height: number; top: number } | null>(null);

  useLayoutEffect(() => {
    onCloseRef.current = onClose;
    dirtyRef.current = dirty;
    busyRef.current = busy;
  }, [busy, dirty, onClose]);

  const requestClose = useCallback(() => {
    if (busyRef.current) return;
    if (dirtyRef.current) {
      setConfirmingDiscard(true);
      return;
    }
    onCloseRef.current();
  }, []);

  useEffect(() => {
    if (!open) return;
    const resetConfirmation = window.setTimeout(() => setConfirmingDiscard(false), 0);
    restoreFocusRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const previous = document.body.style.overflow;
    document.body.style.overflow = "hidden";

    const hidden: Array<{ element: HTMLElement; ariaHidden: string | null; inert: boolean }> = [];
    let branch: HTMLElement | null = overlayRef.current;
    while (branch?.parentElement) {
      for (const sibling of branch.parentElement.children) {
        if (sibling === branch || !(sibling instanceof HTMLElement)) continue;
        hidden.push({
          element: sibling,
          ariaHidden: sibling.getAttribute("aria-hidden"),
          inert: sibling.inert,
        });
        sibling.inert = true;
        sibling.setAttribute("aria-hidden", "true");
      }
      branch = branch.parentElement;
    }

    const focusable = () =>
      Array.from(
        dialogRef.current?.querySelectorAll<HTMLElement>(
          'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
        ) ?? [],
      ).filter((element) => element.getClientRects().length > 0 && !element.closest("[inert]") && element.getAttribute("aria-hidden") !== "true");

    const frame = requestAnimationFrame(() => {
      const initial = dialogRef.current?.querySelector<HTMLElement>("[data-autofocus]") ?? focusable()[0];
      (initial ?? dialogRef.current)?.focus({ preventScroll: true });
    });
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        e.preventDefault();
        requestClose();
        return;
      }
      if (e.key !== "Tab") return;
      const items = focusable();
      if (!items.length) {
        e.preventDefault();
        dialogRef.current?.focus();
        return;
      }
      const first = items[0];
      const last = items[items.length - 1];
      if (e.shiftKey && (document.activeElement === first || !dialogRef.current?.contains(document.activeElement))) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    };
    document.addEventListener("keydown", onKey);
    return () => {
      window.clearTimeout(resetConfirmation);
      cancelAnimationFrame(frame);
      document.body.style.overflow = previous;
      document.removeEventListener("keydown", onKey);
      for (const item of hidden) {
        item.element.inert = item.inert;
        if (item.ariaHidden === null) item.element.removeAttribute("aria-hidden");
        else item.element.setAttribute("aria-hidden", item.ariaHidden);
      }
      restoreFocusRef.current?.focus({ preventScroll: true });
    };
  }, [open, requestClose]);

  useEffect(() => {
    if (!open) return;
    const visualViewport = window.visualViewport;
    const update = () => {
      if (visualViewport) {
        setViewport({ height: visualViewport.height, top: visualViewport.offsetTop });
        requestAnimationFrame(() =>
          (document.activeElement as HTMLElement | null)?.scrollIntoView?.({ block: "nearest" }),
        );
      } else {
        setViewport(null);
      }
    };
    update();
    visualViewport?.addEventListener("resize", update);
    visualViewport?.addEventListener("scroll", update);
    return () => {
      visualViewport?.removeEventListener("resize", update);
      visualViewport?.removeEventListener("scroll", update);
    };
  }, [open]);

  useEffect(() => {
    if (confirmingDiscard) keepEditingRef.current?.focus();
  }, [confirmingDiscard]);

  return (
    <AnimatePresence>
      {open && (
        <div
          ref={overlayRef}
          className="fixed inset-x-0 z-50 flex items-end justify-center"
          style={viewport ? { height: viewport.height, top: viewport.top } : { insetBlock: 0 }}
        >
          <motion.div
            className="absolute inset-0 bg-ink/25 backdrop-blur-[3px]"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.22 }}
            onClick={requestClose}
          />

          <motion.div
            ref={dialogRef}
            role="dialog"
            aria-modal="true"
            aria-label={title}
            aria-busy={busy || undefined}
            tabIndex={-1}
            className="relative flex w-full max-w-[560px] flex-col rounded-t-[28px] border border-b-0 border-line bg-card shadow-sheet"
            style={{ maxHeight: viewport ? Math.max(240, viewport.height - 8) : "72dvh" }}
            initial={{ y: "100%" }}
            animate={{ y: 0 }}
            exit={{ y: "100%" }}
            transition={{ type: "spring", stiffness: 320, damping: 34, mass: 0.8 }}
            drag="y"
            dragListener={false}
            dragControls={controls}
            dragConstraints={{ top: 0, bottom: 0 }}
            dragElastic={{ top: 0, bottom: 0.45 }}
            onDragEnd={(_, info) => {
              if (info.offset.y > 130 || info.velocity.y > 620) requestClose();
            }}
          >
            <div
              className="cursor-grab touch-none pt-3 pb-1 active:cursor-grabbing"
              onPointerDown={(e) => controls.start(e)}
            >
              <div className="mx-auto h-1 w-10 rounded-full bg-line" />
            </div>

            <header className="flex shrink-0 items-start justify-between gap-4 px-6 pb-4 pt-2">
              <div>
                {eyebrow && (
                  <p className="tabular text-[10.5px] uppercase tracking-[0.2em] text-ink-faint">
                    {eyebrow}
                  </p>
                )}
                <h2 className="font-display text-[22px] font-semibold tracking-tight text-ink">
                  {title}
                </h2>
              </div>
              <button
                onClick={requestClose}
                disabled={busy}
                aria-label="Close"
                className="press -mr-1 grid size-11 shrink-0 place-items-center rounded-full border border-line text-ink-soft hover:bg-paper-deep disabled:cursor-not-allowed disabled:opacity-40"
              >
                <X size={16} weight="bold" />
              </button>
            </header>

            {confirmingDiscard && (
              <div role="alert" className="mx-6 mb-4 rounded-2xl border border-line bg-paper-deep p-4">
                <p className="text-sm font-semibold text-ink">Discard your changes?</p>
                <p className="mt-1 text-sm text-ink-soft">Anything you changed in this sheet will be lost.</p>
                <div className="mt-3 flex justify-end gap-2">
                  <button
                    ref={keepEditingRef}
                    type="button"
                    className="press min-h-11 rounded-full border border-line px-4 py-2 text-sm font-semibold text-ink"
                    onClick={() => {
                      setConfirmingDiscard(false);
                      dialogRef.current?.querySelector<HTMLElement>("[data-autofocus], input, button")?.focus();
                    }}
                  >
                    Keep editing
                  </button>
                  <button
                    type="button"
                    disabled={busy}
                    className="press min-h-11 rounded-full bg-ink px-4 py-2 text-sm font-semibold text-card disabled:opacity-40"
                    onClick={() => {
                      if (busyRef.current) return;
                      setConfirmingDiscard(false);
                      onCloseRef.current();
                    }}
                  >
                    Discard
                  </button>
                </div>
              </div>
            )}

            <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-6 pb-6 safe-b">
              {children}
            </div>
            {footer && <footer className="shrink-0 border-t border-line bg-card px-6 pt-3 safe-b">{footer}</footer>}
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  );
}
