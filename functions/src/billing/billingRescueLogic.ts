/**
 * Pure helpers for Admin › Packages' billing health check and rescue panel
 * (billingRescue.ts). Unit-tested in billingRescueLogic.test.ts.
 */
import type { PlayErrorKind } from "./playSubscription";

/** Play refunds a purchase left unacknowledged this long after purchase. */
export const PLAY_ACK_WINDOW_MS = 3 * 24 * 60 * 60 * 1000;

export type PlayApiHealth = "ok" | "config" | "transient";

/**
 * The health probe reads a deliberately unknown token: Play answering "invalid" (400/404)
 * proves the credentials and package access work; 401/403 is a setup problem.
 */
export function healthFromProbe(kind: PlayErrorKind | null): PlayApiHealth {
  if (kind === null || kind === "invalid") return "ok";
  return kind;
}

/** Whole hours left before Play's 3-day refund, counted from [sinceMillis]; never negative. */
export function hoursUntilRefund(sinceMillis: number, now: number): number {
  return Math.max(0, Math.floor((sinceMillis + PLAY_ACK_WINDOW_MS - now) / 3_600_000));
}

/** What the admin must do for a parked purchase, from its stored error. */
export function rescueHint(lastError: string | undefined, needsAdmin: boolean): string {
  const e = (lastError ?? "").toLowerCase();
  if (e.startsWith("unsupported_product")) {
    return "Retired plan (not package_pro_mrr), bought with an old app version — Activate to honour it as Pro Host, " +
      "or leave it and Google Play refunds the buyer 3 days after purchase.";
  }
  if (needsAdmin || e.startsWith("owned_by_other")) {
    return "Tagged for another ProHost account — check with the buyer, then Activate for the right account.";
  }
  if (e.startsWith("config")) {
    return "Google Play access is not set up for the server — fix Play Console access (see the health check), then Retry.";
  }
  if (e.startsWith("transient")) return "Google Play was unreachable — Retry now.";
  if (e.includes("acknowledge")) return "Granted but not acknowledged — Retry now to acknowledge before the refund.";
  return "Retry now; if it fails again, check Play Console › Order management.";
}
