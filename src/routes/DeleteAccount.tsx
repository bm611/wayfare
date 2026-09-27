import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Brand } from "../components/Brand";
import { Button } from "../components/Button";
import { ErrorNote } from "../components/States";
import { useAuth } from "../hooks/useAuth";
import { supabase } from "../lib/supabase";
import { errorMessage } from "../lib/errors";

/**
 * Google Play requires an app that lets people create an account to offer a
 * deletion route that anyone can reach — including someone who no longer has the
 * app installed, or who cannot sign in. This page is that route, which is why it
 * sits outside `RequireAuth` and explains itself to signed-out visitors.
 *
 * It calls the same `delete_account` function the Android app does. See
 * supabase/migrations/20260926120000_delete_account.sql.
 */

/** Replace before publishing: only a monitored address satisfies the policy. */
const SUPPORT_EMAIL = "support@getwayfare.app";

type Stage = "idle" | "confirm" | "working" | "done";

export function DeleteAccount() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [stage, setStage] = useState<Stage>("idle");
  const [failure, setFailure] = useState<string | null>(null);

  async function remove() {
    if (!user) return;
    setStage("working");
    setFailure(null);
    try {
      const { error } = await supabase.rpc("delete_account");
      if (error) throw error;

      // The account is gone, so there is no session left to revoke server-side:
      // clear it locally rather than calling an endpoint that would refuse.
      await supabase.auth.signOut({ scope: "local" });
      setStage("done");
    } catch (err) {
      setFailure(errorMessage(err, "Could not delete the account. Try again."));
      setStage("confirm");
    }
  }

  return (
    <main className="mx-auto flex min-h-[100dvh] max-w-[480px] flex-col px-6 pb-10 pt-6">
      <Brand />

      <div className="mt-10">
        <div className="rounded-card bg-cloud">
          <div className="flex flex-col gap-4 p-6">
            {stage === "done" ? (
              <>
                <p className="type-label text-ash">
                  Account closed
                </p>
                <h1 className="type-headline text-ink">
                  Your account is deleted
                </h1>
                <p className="type-body-lg text-ash">
                  Your trips, expenses and sign-in details are gone. Anything you
                  still have open in the app will stop working the next time it
                  reaches the server.
                </p>
                <Link to="/" className="type-label text-accent-ink underline underline-offset-4">
                  Back to Wayfare
                </Link>
              </>
            ) : (
              <>
                <p className="type-label text-ash">
                  Your data
                </p>
                <h1 className="type-headline text-ink">
                  Delete your account
                </h1>

                <p className="type-body-lg text-ash">
                  Deleting removes your account, every trip you own, and all the
                  expenses logged on them. It cannot be undone.
                </p>

                <ul className="flex flex-col gap-2 type-body text-ash">
                  <li className="flex gap-2">
                    <span aria-hidden className="text-accent-ink">—</span>
                    Trips you own are deleted for every traveller on them, not just you.
                  </li>
                  <li className="flex gap-2">
                    <span aria-hidden className="text-accent-ink">—</span>
                    Trips someone else owns stay with them; you only lose access.
                  </li>
                  <li className="flex gap-2">
                    <span aria-hidden className="text-accent-ink">—</span>
                    Any entries still waiting to sync on your phone are removed too.
                  </li>
                </ul>

                {failure && <ErrorNote message={failure} />}

                {user ? (
                  stage === "confirm" || stage === "working" ? (
                    <div className="flex flex-col gap-2 pt-1">
                      <p className="type-label text-ink">
                        Signed in as {user.email}. Delete this account?
                      </p>
                      <div className="flex gap-2">
                        <Button variant="accent" loading={stage === "working"} onClick={() => void remove()}>
                          Yes, delete it
                        </Button>
                        <Button
                          variant="quiet"
                          disabled={stage === "working"}
                          onClick={() => setStage("idle")}
                        >
                          Keep my account
                        </Button>
                      </div>
                    </div>
                  ) : (
                    <div className="flex flex-col gap-3 pt-1">
                      <Button variant="danger" full onClick={() => setStage("confirm")}>
                        Delete my account
                      </Button>
                      <Link
                        to="/"
                        className="press text-center type-label text-ash underline underline-offset-4"
                      >
                        Back to your trips
                      </Link>
                    </div>
                  )
                ) : (
                  <div className="flex flex-col gap-3 pt-1">
                    <p className="type-body-lg text-ash">
                      Sign in and come back to this page to delete your account
                      yourself.
                    </p>
                    <Button variant="solid" full onClick={() => navigate("/auth")}>
                      Sign in
                    </Button>
                    <p className="type-body-sm text-ash">
                      Locked out, or no longer have the app? Email{" "}
                      <a
                        href={`mailto:${SUPPORT_EMAIL}?subject=Delete%20my%20Wayfare%20account`}
                        className="font-medium text-accent-ink underline underline-offset-4"
                      >
                        {SUPPORT_EMAIL}
                      </a>{" "}
                      from the address on the account and we will delete it for you.
                    </p>
                  </div>
                )}
              </>
            )}
          </div>
        </div>
      </div>
    </main>
  );
}
