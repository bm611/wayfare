import { useId } from "react";
import type { InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from "react";
import { cx } from "../lib/cx";

const CONTROL =
  "w-full rounded-xl border border-line bg-card px-3.5 py-3 text-ink placeholder:text-ink-faint " +
  "transition-colors focus:border-clay";

function Shell({
  label,
  hint,
  error,
  htmlFor,
  children,
}: {
  label: string;
  hint?: string;
  error?: string;
  htmlFor: string;
  children: ReactNode;
}) {
  return (
    <div className="flex flex-col gap-2">
      <label
        htmlFor={htmlFor}
        className="text-[13px] font-medium text-ink-soft"
      >
        {label}
      </label>
      {children}
      {error ? (
        <p id={`${htmlFor}-description`} role="alert" className="text-[12.5px] text-clay-deep">{error}</p>
      ) : hint ? (
        <p id={`${htmlFor}-description`} className="text-[12.5px] text-ink-faint">{hint}</p>
      ) : null}
    </div>
  );
}

export function Field({
  label,
  hint,
  error,
  prefix,
  className,
  autoFocus,
  ...rest
}: InputHTMLAttributes<HTMLInputElement> & {
  label: string;
  hint?: string;
  error?: string;
  prefix?: string;
}) {
  const id = useId();
  return (
    <Shell label={label} hint={hint} error={error} htmlFor={id}>
      <div className="relative">
        {prefix && (
          <span className="tabular pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-ink-faint">
            {prefix}
          </span>
        )}
        <input
          id={id}
          {...rest}
          aria-invalid={!!error || undefined}
          aria-describedby={error || hint ? `${id}-description` : undefined}
          data-autofocus={autoFocus || undefined}
          className={cx(
            CONTROL,
            prefix && "pl-9",
            error && "border-clay/55",
            className,
          )}
        />
      </div>
    </Shell>
  );
}

export function TextArea({
  label,
  hint,
  error,
  className,
  ...rest
}: TextareaHTMLAttributes<HTMLTextAreaElement> & {
  label: string;
  hint?: string;
  error?: string;
}) {
  const id = useId();
  return (
    <Shell label={label} hint={hint} error={error} htmlFor={id}>
      <textarea id={id} rows={2} {...rest} aria-invalid={!!error || undefined} aria-describedby={error || hint ? `${id}-description` : undefined} className={cx(CONTROL, "resize-none", className)} />
    </Shell>
  );
}

export function Select({
  label,
  hint,
  error,
  children,
  className,
  ...rest
}: SelectHTMLAttributes<HTMLSelectElement> & {
  label: string;
  hint?: string;
  error?: string;
}) {
  const id = useId();
  return (
    <Shell label={label} hint={hint} error={error} htmlFor={id}>
      <select id={id} {...rest} aria-invalid={!!error || undefined} aria-describedby={error || hint ? `${id}-description` : undefined} className={cx(CONTROL, "pr-9", className)}>
        {children}
      </select>
    </Shell>
  );
}
