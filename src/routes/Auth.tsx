import { useEffect, useState } from "react";
import { Navigate } from "react-router-dom";
import { motion } from "motion/react";
import { CheckCircle } from "@phosphor-icons/react";
import { Ticket } from "../components/Ticket";
import { Field } from "../components/Field";
import { Button } from "../components/Button";
import { GoogleMark } from "../components/GoogleMark";
import { ErrorNote } from "../components/States";
import { Brand } from "../components/Brand";
import { useAuth } from "../hooks/useAuth";
import { errorMessage } from "../lib/errors";
import { REDIRECT_KEY } from "../App";

type Mode = "signin" | "signup" | "forgot" | "recovery";

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
  const {
    session,
    loading,
    recoveringPassword,
    signIn,
    signInWithGoogle,
    signUp,
    requestPasswordReset,
    updatePassword,
    cancelRecovery,
    resendConfirmation,
  } = useAuth();
  const [selectedMode, setMode] = useState<Mode>(() =>
    new URLSearchParams(window.location.search).get("mode") === "recovery"
      ? "recovery"
      : "signin",
  );
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [googleBusy, setGoogleBusy] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);
  const [sent, setSent] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [resendCooldown, setResendCooldown] = useState(0);
  const mode = recoveringPassword ? "recovery" : selectedMode;

  useEffect(() => {
    if (resendCooldown <= 0) return;
    const timer = window.setTimeout(() => setResendCooldown((seconds) => seconds - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [resendCooldown]);

  if (!loading && session && !recoveringPassword && mode !== "recovery") {
    return <Navigate to={consumeRedirect()} replace />;
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (busy || loading || (mode === "forgot" && resendCooldown > 0) || (mode === "recovery" && !session)) return;
    setBusy(true);
    setFailure(null);
    try {
      if (mode === "signin") {
        await signIn(email.trim(), password);
      } else if (mode === "signup") {
        const { needsConfirmation } = await signUp(email.trim(), password, name.trim());
        if (needsConfirmation) {
          setSent(true);
          setResendCooldown(60);
        }
      } else if (mode === "forgot") {
        await requestPasswordReset(email.trim());
        setNotice("If an account exists for this email, you’ll receive a password reset link.");
        setResendCooldown(60);
      } else {
        await updatePassword(password);
        setMode("signin");
      }
    } catch (err) {
      setFailure(errorMessage(err, "Something went wrong. Try again."));
    } finally {
      setBusy(false);
    }
  }

  async function google() {
    if (busy || googleBusy) return;
    setGoogleBusy(true);
    setFailure(null);
    try {
      // Resolves once the redirect is under way, so the spinner stays up until the
      // browser leaves the page rather than flickering off first.
      await signInWithGoogle();
    } catch (err) {
      setFailure(errorMessage(err, "Could not start Google sign-in."));
      setGoogleBusy(false);
    }
  }

  async function resend() {
    if (busy || resendCooldown > 0) return;
    setBusy(true);
    setFailure(null);
    try {
      await resendConfirmation(email.trim());
      setNotice("Confirmation email resent.");
      setResendCooldown(60);
    } catch (err) {
      setFailure(errorMessage(err, "Could not resend the confirmation email."));
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
              {failure && <ErrorNote message={failure} />}
              {notice && <p role="status" className="text-[13px] text-ink-soft">{notice}</p>}
              <button
                type="button"
                disabled={busy || resendCooldown > 0}
                onClick={resend}
                className="press min-h-11 text-[14px] font-medium text-clay underline underline-offset-4 disabled:opacity-45"
              >
                {resendCooldown > 0 ? `Resend in ${resendCooldown}s` : "Resend confirmation email"}
              </button>
              <button
                onClick={() => {
                  setSent(false);
                  setMode("signin");
                }}
                className="press mt-1 min-h-11 text-[14px] font-medium text-clay underline underline-offset-4"
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
                    if (mode === "recovery") cancelRecovery();
                    setMode(mode === "signin" ? "signup" : "signin");
                    setFailure(null);
                    setNotice(null);
                  }}
                  className="press min-h-11 text-[13px] font-medium text-clay underline underline-offset-4"
                >
                  {mode === "signin" ? "Create an account" : "Sign in"}
                </button>
              </div>
            }
          >
            <form onSubmit={submit} className="flex flex-col gap-4 p-6">
              {(mode === "forgot" || mode === "recovery") && <h2 className="font-display text-xl font-semibold">{mode === "forgot" ? "Reset your password" : "Choose a new password"}</h2>}
              {failure && <ErrorNote message={failure} />}
              {notice && <p role="status" className="text-[13px] leading-relaxed text-ink-soft">{notice}</p>}
              {mode === "recovery" && !loading && !session && <div className="text-sm text-ink-soft">
                <p>This reset link is invalid or has expired. Request a new one to continue.</p>
                <button type="button" className="min-h-11 text-clay underline" onClick={() => { cancelRecovery(); setMode("forgot"); }}>Request a new link</button>
              </div>}

              {(mode === "signin" || mode === "signup") && (
                <>
                  <Button
                    type="button"
                    variant="google"
                    full
                    loading={googleBusy}
                    disabled={loading || busy}
                    onClick={google}
                  >
                    <GoogleMark />
                    Continue with Google
                  </Button>
                  <div className="flex items-center gap-3" aria-hidden="true">
                    <span className="h-px flex-1 bg-line" />
                    <span className="text-[11px] uppercase tracking-[0.18em] text-ink-faint">or</span>
                    <span className="h-px flex-1 bg-line" />
                  </div>
                </>
              )}

              {mode === "signup" && (
                <Field
                  label="Name"
                  placeholder="Rosalind"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  autoComplete="given-name"
                />
              )}

              {mode !== "recovery" && (
                <Field
                  label="Email"
                  type="email"
                  required
                  placeholder="you@example.com"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  autoComplete="email"
                />
              )}

              {mode !== "forgot" && (
                <div className="flex flex-col gap-2">
                  <Field
                    label={mode === "recovery" ? "New password" : "Password"}
                    type={showPassword ? "text" : "password"}
                    required
                    minLength={6}
                    placeholder="At least 6 characters"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    autoComplete={mode === "signin" ? "current-password" : "new-password"}
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword((shown) => !shown)}
                    aria-pressed={showPassword}
                    className="press min-h-11 self-end text-[13px] font-medium text-ink-soft underline underline-offset-4"
                  >
                    {showPassword ? "Hide password" : "Show password"}
                  </button>
                </div>
              )}

              {mode === "signin" && (
                <button
                  type="button"
                  onClick={() => {
                    setMode("forgot");
                    setFailure(null);
                    setNotice(null);
                  }}
                  className="press min-h-11 self-end text-[13px] font-medium text-clay underline underline-offset-4"
                >
                  Forgot password?
                </button>
              )}

              <Button type="submit" variant="accent" full loading={busy} disabled={loading || (mode === "recovery" && !session) || (mode === "forgot" && resendCooldown > 0)} className="mt-1">
                {mode === "signin"
                  ? "Sign in"
                  : mode === "signup"
                    ? "Create account"
                    : mode === "forgot"
                      ? resendCooldown > 0 ? `Send again in ${resendCooldown}s` : "Send reset link"
                      : "Update password"}
              </Button>
            </form>
          </Ticket>
        )}
      </motion.div>

      <p className="mt-auto pt-10 text-[12px] leading-relaxed text-ink-faint">
        Only you and people you invite can access your trips.
      </p>
    </main>
  );
}
