import { useEffect } from "react";
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
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  eyebrow?: string;
  children: ReactNode;
}) {
  const controls = useDragControls();

  useEffect(() => {
    if (!open) return;
    const previous = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => {
      document.body.style.overflow = previous;
      window.removeEventListener("keydown", onKey);
    };
  }, [open, onClose]);

  return (
    <AnimatePresence>
      {open && (
        <div className="fixed inset-0 z-50 flex items-end justify-center">
          <motion.div
            className="absolute inset-0 bg-ink/25 backdrop-blur-[3px]"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.22 }}
            onClick={onClose}
          />

          <motion.div
            role="dialog"
            aria-modal="true"
            aria-label={title}
            className="relative w-full max-w-[560px] rounded-t-[28px] border border-b-0 border-line bg-card shadow-sheet"
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
              if (info.offset.y > 130 || info.velocity.y > 620) onClose();
            }}
          >
            <div
              className="cursor-grab touch-none pt-3 pb-1 active:cursor-grabbing"
              onPointerDown={(e) => controls.start(e)}
            >
              <div className="mx-auto h-1 w-10 rounded-full bg-line" />
            </div>

            <header className="flex items-start justify-between gap-4 px-6 pb-4 pt-2">
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
                onClick={onClose}
                aria-label="Close"
                className="press -mr-1 grid size-9 shrink-0 place-items-center rounded-full border border-line text-ink-soft hover:bg-paper-deep"
              >
                <X size={16} weight="bold" />
              </button>
            </header>

            <div className="max-h-[72dvh] overflow-y-auto px-6 pb-6 safe-b">{children}</div>
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  );
}
