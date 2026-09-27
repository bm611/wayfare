import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { motion } from "motion/react";
import { ArrowClockwise, Compass, SignOut, Ticket, Trash, UserCircle } from "@phosphor-icons/react";
import { Brand, IconButton } from "../components/Brand";
import { Menu } from "../components/Menu";
import { NewTripButton, StampTile, TripCard } from "../components/TripCard";
import { TripSheet } from "../components/TripSheet";
import { JoinSheet } from "../components/JoinSheet";
import { EmptyState, ErrorNote, TripCardSkeleton } from "../components/States";
import { useAuth } from "../hooks/useAuth";
import { useTrips } from "../hooks/useTrips";
import { useTripCovers } from "../hooks/useTripCovers";
import { tripPhase } from "../lib/format";

const SECTIONS = [
  ["active", "Active trips"],
  ["upcoming", "Upcoming trips"],
  ["undated", "Dates open"],
  ["past", "Past trips"],
] as const;

export function Trips() {
  const { signOut } = useAuth();
  const navigate = useNavigate();
  const { trips, loading, error, reload, createTrip, applyCovers } = useTrips();
  const [tripSheet, setTripSheet] = useState(false);
  const [joinSheet, setJoinSheet] = useState(false);

  // Draws the passport stamp on each card, and watches for it to land.
  useTripCovers(trips, applyCovers);

  return (
    <main className="mx-auto max-w-[700px] px-6 pb-16 pt-2">
      {/* Top nav: the accent wordmark, then join and account as circular controls. */}
      <header className="flex items-center gap-2 pt-2">
        <div className="flex-1">
          <Brand />
        </div>
        <IconButton label="Join a trip" onClick={() => setJoinSheet(true)}>
          <Ticket size={18} />
        </IconButton>
        <Menu
          label="Account"
          icon={<UserCircle size={20} />}
          items={[
            { label: "Refresh", icon: <ArrowClockwise size={18} />, onSelect: () => void reload() },
            { label: "Sign out", icon: <SignOut size={18} />, onSelect: () => void signOut() },
            { label: "Delete account", icon: <Trash size={18} />, onSelect: () => navigate("/delete-account"), destructive: true },
          ]}
        />
      </header>

      <section className="mt-6 flex flex-col gap-5">
        <h1 className="type-display text-ink">Your trips</h1>
        <NewTripButton onClick={() => setTripSheet(true)} />
      </section>

      <div className="mt-6 flex flex-col gap-6">
        {error && <ErrorNote message={error} onRetry={() => void reload()} />}

        {loading ? (
          <TripCardSkeleton />
        ) : trips.length === 0 && !error ? (
          <EmptyState
            icon={<Compass size={28} weight="light" />}
            title="Good trips start here"
            body="Add a trip, set a budget, and log each cost as it lands."
            action={
              <button onClick={() => setJoinSheet(true)} className="press min-h-11 type-label text-ink">
                I have an invite code
              </button>
            }
          />
        ) : (
          <motion.div
            initial="hidden"
            animate="show"
            variants={{ show: { transition: { staggerChildren: 0.07 } } }}
            className="flex flex-col gap-6"
          >
            {SECTIONS.map(([phase, label]) => {
              const group = trips.filter((trip) => tripPhase(trip).kind === phase);
              if (!group.length) return null;
              return (
                <section key={phase} className="flex flex-col gap-6">
                  <h2 className="flex items-baseline gap-2 pt-2 type-headline text-ink">
                    {label}
                    <span className="tabular type-body-lg text-ash">{group.length}</span>
                  </h2>
                  {/* Trips still to come get a full card; finished ones are
                      collected, stamps on a passport page. */}
                  {phase === "past" ? (
                    <ul className="grid grid-cols-2 gap-x-4 gap-y-2">
                      {group.map((trip) => <StampTile key={trip.id} trip={trip} />)}
                    </ul>
                  ) : (
                    <ul className="flex flex-col gap-6">
                      {group.map((trip) => <TripCard key={trip.id} trip={trip} />)}
                    </ul>
                  )}
                </section>
              );
            })}
          </motion.div>
        )}
      </div>

      <TripSheet open={tripSheet} onClose={() => setTripSheet(false)} onSave={createTrip} />
      <JoinSheet open={joinSheet} onClose={() => setJoinSheet(false)} />
    </main>
  );
}
