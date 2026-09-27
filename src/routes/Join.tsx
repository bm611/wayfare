import { useEffect, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { motion } from "motion/react";
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
    <main className="mx-auto flex min-h-[100dvh] max-w-[480px] flex-col px-6 pb-10 pt-6">
      <Brand />

      <motion.div
        initial={{ opacity: 0, y: 14 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ type: "spring", stiffness: 130, damping: 20 }}
        className="mt-16"
      >
        <div className="rounded-card bg-cloud">
          <div className="flex flex-col gap-3 p-6">
            {failure ? (
              <>
                <ErrorNote message={failure} />
                <Link
                  to="/"
                  className="press mt-1 type-label text-accent-ink underline underline-offset-4"
                >
                  Back to your trips
                </Link>
              </>
            ) : (
              <>
                <p className="type-label text-ash">
                  Invite {code}
                </p>
                <h1 className="type-headline text-ink">
                  Checking you in
                </h1>
                <div className="flex gap-1 pt-1">
                  {[0, 1, 2].map((i) => (
                    <span
                      key={i}
                      className="beacon size-1.5 rounded-full bg-accent"
                      style={{ animationDelay: `${i * 180}ms` }}
                    />
                  ))}
                </div>
              </>
            )}
          </div>
        </div>
      </motion.div>
    </main>
  );
}
