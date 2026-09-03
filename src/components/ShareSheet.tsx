import { useState } from "react";
import { Check, Copy, Crown, LinkSimple, UserMinus } from "@phosphor-icons/react";
import { Sheet } from "./Sheet";
import { Button } from "./Button";
import { ErrorNote, Shimmer } from "./States";
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
          <p className="mb-2.5 text-[11px] font-medium uppercase tracking-[0.14em] text-ink-soft">
            Invite code
          </p>

          <button
            type="button"
            onClick={() => shareCode && copy(shareCode, "code")}
            className="press hatch flex w-full items-center justify-between rounded-2xl border border-dashed border-line px-5 py-4 text-left"
          >
            <span className="tabular text-[24px] font-semibold tracking-[0.22em] text-ink">
              {shareCode ?? "········"}
            </span>
            <span className="text-ink-faint">
              {copied === "code" ? (
                <Check size={18} weight="bold" className="text-clay" />
              ) : (
                <Copy size={18} weight="bold" />
              )}
            </span>
          </button>

          <p className="mt-2.5 text-[12.5px] leading-relaxed text-ink-faint">
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
          <p className="mb-1 text-[11px] font-medium uppercase tracking-[0.14em] text-ink-soft">
            On this trip
          </p>

          {loading ? (
            <div className="flex flex-col gap-3 pt-3">
              <Shimmer className="h-9 w-full" />
              <Shimmer className="h-9 w-full" />
            </div>
          ) : (
            <ul className="divide-y divide-line-soft">
              {members.map((member) => (
                <li key={member.user_id} className="flex items-center gap-3 py-3">
                  <Avatar name={member.display_name} />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-[14.5px] font-medium text-ink">
                      {member.display_name ?? "Traveller"}
                      {member.is_you && <span className="text-ink-faint"> · you</span>}
                    </span>
                    <span className="flex items-center gap-1 text-[12px] text-ink-faint">
                      {member.role === "owner" && <Crown size={11} weight="fill" />}
                      {member.role === "owner" ? "Organiser" : "Member"}
                    </span>
                  </span>

                  {isOwner && !member.is_you && (
                    <button
                      onClick={() => void kick(member.user_id)}
                      aria-label={`Remove ${member.display_name ?? "traveller"}`}
                      className="press grid size-8 place-items-center rounded-full border border-line text-ink-faint hover:border-clay/40 hover:bg-clay-wash hover:text-clay-deep"
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
        "border border-line bg-paper-deep text-[12px] font-semibold text-ink-soft",
        className,
      )}
      aria-hidden
    >
      {initials || "··"}
    </span>
  );
}
