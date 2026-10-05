/**
 * What the hourly expiry sweep does with a profile whose stored expiry has passed.
 * Pure, so it is unit-tested (expiryLogic.test.ts).
 *
 * Google Play is the authority for Play subscribers: the sweep re-reads the subscription
 * first. A failed re-read must never demote a paying member — a Play outage or a Play
 * Console permission problem would otherwise strip every subscriber whose renewal
 * notification was missed.
 */
export type PlayRecheck =
  | { kind: "not_play" }          // admin grant, legacy plan, or no purchase token on file
  | { kind: "has_access" }        // Play says active / grace / canceled-but-paid
  | { kind: "no_access" }         // Play says expired / on hold / paused / revoked
  | { kind: "error"; errorKind: "config" | "transient" | "invalid" };

export type ExpiryDecision = "keep" | "skip" | "demote";

export function expiryDecision(recheck: PlayRecheck): ExpiryDecision {
  switch (recheck.kind) {
    case "has_access": return "keep";
    case "no_access": return "demote";
    case "not_play": return "demote";
    case "error":
      // Play doesn't know the token any more (400/404/410): nothing left to wait for.
      // Access denied or Play unreachable: try again next hour, never demote blindly.
      return recheck.errorKind === "invalid" ? "demote" : "skip";
  }
}
