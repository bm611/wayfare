import { useState } from "react";
import { Navigate } from "react-router-dom";
import { motion } from "motion/react";
import { CheckCircle } from "@phosphor-icons/react";
import { Ticket } from "../components/Ticket";
import { Field } from "../components/Field";
import { Button } from "../components/Button";
import { ErrorNote } from "../components/States";
import { Brand } from "../components/Brand";
import { useAuth } from "../hooks/useAuth";
import { REDIRECT_KEY } from "../App";

type Mode = "signin" | "signup";

/** Pops the invite path stashed when an unauthenticated user hit a join link. */
function consumeRedirect() {
  try {
    const target = sessionStorage.getItem(REDIRECT_KEY);
    if (target) sessionStorage.removeItem(REDIRECT_KEY);
    return target || "/";
  } catch {
    return "/";
  }
}

export function Auth() {
  const { session, loading, signIn, signUp } = useAuth();
  const [mode, setMode] = useState<Mode>("signin");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);
  const [sent, setSent] = useState(false);

  if (!loading && session) return <Navigate to={consumeRedirect()} replace />;

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setFailure(null);
    try {
      if (mode === "signin") {
        await signIn(email.trim(), password);
      } else {
        const { needsConfirmation } = await signUp(email.trim(), password, name.trim());
        if (needsConfirmation) setSent(true);
      }
    } catch (err) {
      setFailure(err instanceof Error ? err.message : "Something went wrong. Try again.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="grain mx-auto flex min-h-[100dvh] max-w-[440px] flex-col px-6 pb-10 pt-8">
      <Brand size="lg" />

      <motion.div
        initial={{ opacity: 0, y: 14 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ type: "spring", stiffness: 120, damping: 20 }}
        className="mt-14"
      >
        <p className="tabular text-[10.5px] uppercase tracking-[0.24em] text-ink-faint">
          Trip ledger · est. 2026
        </p>
        <h1 className="mt-3 font-display text-[38px] font-semibold leading-[1.04] tracking-tight text-ink">
          Know what the
          <br />
          trip actually cost.
        </h1>
        <p className="mt-3 max-w-[34ch] text-[15px] leading-relaxed text-ink-soft">
          Flights, the room, every coffee and cable car — logged against a budget you set before you
          leave.
        </p>
      </motion.div>

      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ type: "spring", stiffness: 120, damping: 20, delay: 0.08 }}
        className="mt-10"
      >
        {sent ? (
          <Ticket>
            <div className="flex flex-col items-start gap-3 p-6">
              <CheckCircle size={26} weight="duotone" className="text-clay" />
              <h2 className="font-display text-[20px] font-semibold tracking-tight">
                Check your inbox
              </h2>
              <p className="text-[14px] leading-relaxed text-ink-soft">
                We sent a confirmation link to{" "}
                <span className="font-medium text-ink">{email}</span>. Open it, then come back and
                sign in.
              </p>
              <button
                onClick={() => {
                  setSent(false);
                  setMode("signin");
                }}
                className="press mt-1 text-[14px] font-medium text-clay underline underline-offset-4"
              >
                Back to sign in
              </button>
            </div>
          </Ticket>
        ) : (
          <Ticket
            stub={
              <div className="flex items-center justify-between px-6 py-4">
                <p className="text-[13px] text-ink-soft">
                  {mode === "signin" ? "First trip with us?" : "Already have a ledger?"}
                </p>
                <button
                  type="button"
                  onClick={() => {
                    setMode(mode === "signin" ? "signup" : "signin");
                    setFailure(null);
                  }}
                  className="press text-[13px] font-medium text-clay underline underline-offset-4"
                >
                  {mode === "signin" ? "Create an account" : "Sign in"}
                </button>
              </div>
            }
          >
            <form onSubmit={submit} className="flex flex-col gap-4 p-6">
              {failure && <ErrorNote message={failure} />}

              {mode === "signup" && (
                <Field
                  label="Name"
                  placeholder="Rosalind"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  autoComplete="given-name"
                />
              )}

              <Field
                label="Email"
                type="email"
                required
                placeholder="you@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                autoComplete="email"
              />

              <Field
                label="Password"
                type="password"
                required
                minLength={6}
                placeholder="At least 6 characters"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete={mode === "signin" ? "current-password" : "new-password"}
              />

              <Button type="submit" variant="accent" full loading={busy} className="mt-1">
                {mode === "signin" ? "Sign in" : "Create account"}
              </Button>
            </form>
          </Ticket>
        )}
      </motion.div>

      <p className="mt-auto pt-10 text-[12px] leading-relaxed text-ink-faint">
        Your ledger is private. Row-level security keeps every trip scoped to your account.
      </p>
    </main>
  );
}
