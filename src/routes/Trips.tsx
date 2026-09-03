import { useMemo, useState } from "react";
import { motion } from "motion/react";
import { Plus, SignOut, Ticket as TicketIcon } from "@phosphor-icons/react";
import { Brand, IconButton, IconGroup, IconGroupDivider } from "../components/Brand";
import { TripCard } from "../components/TripCard";
import { TripSheet } from "../components/TripSheet";
import { JoinSheet } from "../components/JoinSheet";
import { Button } from "../components/Button";
import { EmptyState, ErrorNote, RouteArt, TripCardSkeleton } from "../components/States";
import { CountUp } from "../components/CountUp";
import { useAuth } from "../hooks/useAuth";
import { useTrips } from "../hooks/useTrips";
import { useTripCovers } from "../hooks/useTripCovers";
import { symbolFor, tripPhase } from "../lib/format";
import { BASE_CURRENCY } from "../lib/fx";

export function Trips() {
  const { user, signOut } = useAuth();
  const { trips, loading, error, reload, createTrip, applyCovers } = useTrips();
  const [tripSheet, setTripSheet] = useState(false);
  const [joinSheet, setJoinSheet] = useState(false);

  // Draws the destination shot behind each pass, and watches for it to land.
  useTripCovers(trips, applyCovers);

  // Every trip is euro-denominated, so a single total is always meaningful.
  const summary = useMemo(() => {
    if (trips.length === 0) return null;
    return {
      spent: trips.reduce((sum, t) => sum + t.spent, 0),
      active: trips.filter((t) => tripPhase(t).kind === "active").length,
    };
  }, [trips]);

  return (
    <main className="grain mx-auto max-w-[560px] px-5 pb-16 pt-6">
      <header className="flex items-center gap-2.5">
        <div className="flex-1 truncate">
          <Brand size="lg" />
        </div>
        <Button size="sm" variant="accent" onClick={() => setTripSheet(true)}>
          <Plus size={15} weight="bold" />
          New trip
        </Button>
        <IconGroup>
          <IconButton flush label="Join a trip with a code" onClick={() => setJoinSheet(true)}>
            <TicketIcon size={16} weight="bold" />
          </IconButton>
          <IconGroupDivider />
          <IconButton flush label="Sign out" onClick={() => void signOut()}>
            <SignOut size={16} weight="bold" />
          </IconButton>
        </IconGroup>
      </header>

      <section className="mt-10">
        {summary ? (
          <>
            <h1 className="font-display text-[40px] font-semibold leading-none tracking-tight text-ink">
              <span className="text-ink-faint">{symbolFor(BASE_CURRENCY).trim()}</span>
              <CountUp value={summary.spent} cents={false} />
            </h1>
            <p className="mt-2.5 text-[14px] text-ink-soft">
              logged across <span className="tabular font-medium text-ink">{trips.length}</span>{" "}
              {trips.length === 1 ? "trip" : "trips"}
              {summary.active > 0 && (
                <>
                  {" · "}
                  <span className="tabular font-medium text-clay">{summary.active} in motion</span>
                </>
              )}
            </p>
          </>
        ) : (
          <h1 className="font-display text-[32px] font-semibold leading-tight tracking-tight text-ink">
            Nothing booked yet
          </h1>
        )}
      </section>

      <div className="mt-8">
        {error && <ErrorNote message={error} onRetry={() => void reload()} />}

        {loading ? (
          <div className="flex flex-col gap-4">
            <TripCardSkeleton />
            <TripCardSkeleton />
          </div>
        ) : trips.length === 0 && !error ? (
          <EmptyState
            art={<RouteArt />}
            title="Your first stub is waiting"
            body="Add a trip, set what you're willing to spend, and start logging costs as they land. Or join one someone else has already started."
            action={
              <div className="flex flex-wrap justify-center gap-3">
                <Button variant="accent" onClick={() => setTripSheet(true)}>
                  Add a trip
                </Button>
                <Button variant="quiet" onClick={() => setJoinSheet(true)}>
                  I have a code
                </Button>
              </div>
            }
          />
        ) : (
          <motion.ul
            initial="hidden"
            animate="show"
            variants={{ show: { transition: { staggerChildren: 0.07 } } }}
            className="flex flex-col gap-4"
          >
            {trips.map((trip) => (
              <TripCard key={trip.id} trip={trip} shared={trip.user_id !== user?.id} />
            ))}
          </motion.ul>
        )}
      </div>

      <TripSheet open={tripSheet} onClose={() => setTripSheet(false)} onSave={createTrip} />
      <JoinSheet open={joinSheet} onClose={() => setJoinSheet(false)} />
    </main>
  );
}
