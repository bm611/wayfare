import { useId } from "react";
import type { InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from "react";
import { cx } from "../lib/cx";

/* White behind a hairline at the control radius, switching to ink on focus.
   The system never tints a field with the accent. */
const CONTROL =
  "min-h-12 w-full rounded-control border border-hairline bg-canvas px-4 py-3 text-ink placeholder:text-ash " +
  "transition-colors focus:border-ink focus:text-charcoal focus:outline-none aria-[invalid=true]:border-error";

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
        className="type-body text-ash"
      >
        {label}
      </label>
      {children}
      {error ? (
        <p id={`${htmlFor}-description`} role="alert" className="type-body-sm text-error">{error}</p>
      ) : hint ? (
        <p id={`${htmlFor}-description`} className="type-body-sm text-ash">{hint}</p>
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
          <span className="tabular pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-ash">
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
