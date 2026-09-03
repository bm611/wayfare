import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Sheet } from "./Sheet";
import { Field } from "./Field";
import { Button } from "./Button";
import { ErrorNote } from "./States";
import { joinTrip } from "../hooks/useMembers";
import { errorMessage } from "../lib/errors";

export function JoinSheet({ open, onClose }: { open: boolean; onClose: () => void }) {
  const navigate = useNavigate();
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!code.trim()) {
      setFailure("Paste the code your travel companion sent you.");
      return;
    }
    setBusy(true);
    setFailure(null);
    try {
      const tripId = await joinTrip(code);
      onClose();
      navigate(`/trip/${tripId}`);
    } catch (err) {
      setFailure(errorMessage(err, "That code did not work."));
      setBusy(false);
    }
  }

  return (
    <Sheet open={open} onClose={onClose} eyebrow="Someone invited you" title="Join a trip">
      <form onSubmit={submit} className="flex flex-col gap-5">
        {failure && <ErrorNote message={failure} />}

        <Field
          label="Invite code"
          placeholder="XKMP4RTQ"
          value={code}
          onChange={(e) => setCode(e.target.value.toUpperCase())}
          hint="Eight characters, from whoever set the trip up."
          autoFocus
          maxLength={8}
          className="tabular text-[19px] font-medium tracking-[0.2em] uppercase"
        />

        <Button type="submit" variant="accent" full loading={busy}>
          Join the trip
        </Button>
      </form>
    </Sheet>
  );
}
