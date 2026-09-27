import { useState } from "react";
import { Check, Copy, Crown, LinkSimple, UserMinus } from "@phosphor-icons/react";
import { Sheet } from "./Sheet";
import { Button } from "./Button";
import { ErrorNote } from "./States";
import type { Membership } from "../hooks/useMembers";
import { cx } from "../lib/cx";
import { errorMessage } from "../lib/errors";

export function ShareSheet({
  open,
  onClose,
  tripName,
  shareCode,
  isOwner,
  membership,
  onLeave,
}: {
  open: boolean;
  onClose: () => void;
  tripName: string;
  shareCode: string | null;
  isOwner: boolean;
  membership: Membership;
  onLeave: () => void;
}) {
  const { members, loading, error, reload, removeMember } = membership;
  const [copied, setCopied] = useState<"code" | "link" | null>(null);
  const [failure, setFailure] = useState<string | null>(null);

  const inviteLink = shareCode ? `${window.location.origin}/join/${shareCode}` : null;

  async function copy(value: string, which: "code" | "link") {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(which);
      window.setTimeout(() => setCopied(null), 1800);
    } catch {
      setFailure("Your browser blocked the clipboard. Select the code and copy it by hand.");
    }
  }

  async function kick(userId: string) {
    setFailure(null);
    try {
      await removeMember(userId);
    } catch (err) {
      setFailure(errorMessage(err, "Could not remove that person."));
    }
  }

  return (
    <Sheet open={open} onClose={onClose} eyebrow="Travelling together" title={`Share ${tripName}`}>
      <div className="flex flex-col gap-6">
        {failure && <ErrorNote message={failure} />}
        {error && <ErrorNote message={error} onRetry={() => void reload()} />}

        <div>
          <p className="mb-2.5 type-label text-ash">
            Invite code
          </p>

          <button
            type="button"
            onClick={() => shareCode && copy(shareCode, "code")}
            className="press flex w-full items-center justify-between rounded-card bg-cloud px-5 py-4 text-left"
          >
            <span className="tabular type-headline tracking-[0.22em] text-ink">
              {shareCode ?? "········"}
            </span>
            <span className="text-ash">
              {copied === "code" ? (
                <Check size={18} weight="bold" className="text-accent-ink" />
              ) : (
                <Copy size={18} weight="bold" />
              )}
            </span>
          </button>

          <p className="mt-2.5 type-body-sm text-ash">
            Anyone with this code can join the trip and log their own spending against the same
            budget.
          </p>

          {inviteLink && (
            <Button
              variant="solid"
              full
              className="mt-4"
              onClick={() => void copy(inviteLink, "link")}
            >
              {copied === "link" ? (
                <>
                  <Check size={16} weight="bold" />
                  Link copied
                </>
              ) : (
                <>
                  <LinkSimple size={16} weight="bold" />
                  Copy invite link
                </>
              )}
            </Button>
          )}
        </div>

        <div>
          <p className="mb-1 type-label text-ash">
            On this trip
          </p>

          {loading ? (
            <div className="flex flex-col gap-3 pt-3">
              <div className="h-9 w-full rounded-control bg-hairline" />
              <div className="h-9 w-full rounded-control bg-hairline" />
            </div>
          ) : (
            <ul className="divide-y divide-cloud">
              {members.map((member) => (
                <li key={member.user_id} className="flex items-center gap-3 py-3">
                  <Avatar name={member.display_name} />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate type-title text-ink">
                      {member.display_name ?? "Traveller"}
                      {member.is_you && <span className="text-ash"> · you</span>}
                    </span>
                    <span className="flex items-center gap-1 type-body-sm text-ash">
                      {member.role === "owner" && <Crown size={11} weight="fill" />}
                      {member.role === "owner" ? "Organiser" : "Member"}
                    </span>
                  </span>

                  {isOwner && !member.is_you && (
                    <button
                      onClick={() => void kick(member.user_id)}
                      aria-label={`Remove ${member.display_name ?? "traveller"}`}
                      className="press grid size-8 place-items-center rounded-full border border-hairline text-ash hover:border-hairline/40 hover:bg-cloud hover:text-error"
                    >
                      <UserMinus size={14} weight="bold" />
                    </button>
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>

        {!isOwner && (
          <Button variant="danger" full onClick={onLeave}>
            Leave this trip
          </Button>
        )}
      </div>
    </Sheet>
  );
}

/** Initials on a tinted plate — no stock avatar art. */
export function Avatar({ name, className }: { name: string | null; className?: string }) {
  const initials = (name ?? "")
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("");

  return (
    <span
      className={cx(
        "tabular grid size-9 shrink-0 place-items-center rounded-full",
        "border border-hairline bg-cloud type-body-sm font-semibold text-ash",
        className,
      )}
      aria-hidden
    >
      {initials || "··"}
    </span>
  );
}
