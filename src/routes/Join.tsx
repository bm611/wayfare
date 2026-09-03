import { useEffect, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { motion } from "motion/react";
import { Ticket } from "../components/Ticket";
import { Brand } from "../components/Brand";
import { ErrorNote } from "../components/States";
import { joinTrip } from "../hooks/useMembers";
import { errorMessage } from "../lib/errors";

/** Redeems an invite link the moment a signed-in traveller lands on it. */
export function Join() {
  const { code } = useParams<{ code: string }>();
  const navigate = useNavigate();
  const [failure, setFailure] = useState<string | null>(null);
  const attempted = useRef(false);

  useEffect(() => {
    if (!code || attempted.current) return;
    attempted.current = true;

    joinTrip(code)
      .then((tripId) => navigate(`/trip/${tripId}`, { replace: true }))
      .catch((err: unknown) =>
        setFailure(errorMessage(err, "That invite code did not work.")),
      );
  }, [code, navigate]);

  return (
    <main className="grain mx-auto flex min-h-[100dvh] max-w-[440px] flex-col px-6 pb-10 pt-8">
      <Brand size="lg" />

      <motion.div
        initial={{ opacity: 0, y: 14 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ type: "spring", stiffness: 130, damping: 20 }}
        className="mt-16"
      >
        <Ticket>
          <div className="flex flex-col gap-3 p-6">
            {failure ? (
              <>
                <ErrorNote message={failure} />
                <Link
                  to="/"
                  className="press mt-1 text-[14px] font-medium text-clay underline underline-offset-4"
                >
                  Back to your trips
                </Link>
              </>
            ) : (
              <>
                <p className="tabular text-[10.5px] uppercase tracking-[0.22em] text-ink-faint">
                  Invite {code}
                </p>
                <h1 className="font-display text-[22px] font-semibold tracking-tight text-ink">
                  Checking you in
                </h1>
                <div className="flex gap-1 pt-1">
                  {[0, 1, 2].map((i) => (
                    <span
                      key={i}
                      className="beacon size-1.5 rounded-full bg-clay"
                      style={{ animationDelay: `${i * 180}ms` }}
                    />
                  ))}
                </div>
              </>
            )}
          </div>
        </Ticket>
      </motion.div>
    </main>
  );
}
