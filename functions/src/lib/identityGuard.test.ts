import { test } from "node:test";
import assert from "node:assert/strict";

/**
 * Guard for per-service runtime identities (docs/GCP_IAM.md).
 *
 * PLAY_REACHING: functions whose code path calls the Google Play Developer API or Play Integrity with the
 * runtime credentials (found by a static import scan, 4 Nov 2026). Only the service account invited in
 * Play Console › Users and permissions may run them. Today that is the default compute account, so they must
 * NOT get a group account until the group account is invited and `npm run` maintenance tasks playcatalog /
 * integrityprobe pass under it. When that happens, list the group in PLAY_INVITED_GROUPS.
 */
const PLAY_REACHING = [
  "adminActivatePurchase",
  "adminBillingPending",
  "assignInitialRole",
  "billingHealthCheck",
  "billingRtdnSelfTest",
  "billingSyncJob",
  "expirePackages",
  "forceProHostUpgrade",
  "igCommentPoll",
  "igDmPoll",
  "igFollowUps",
  "igWebhook",
  "onMaintenanceRequest",
  "playBillingRtdn",
  "retryPendingPlayActivations",
  "revokeProHostRole",
  "runBillingSync",
  "verifyAndRestorePurchase",
  "waWebhook"
];
// 4 Nov 2026: run-billing and run-ig were invited in Play Console (all permissions). A function moves only after the
// maintenance tasks `playcatalog` and `integrity` pass under that account (canary: onMaintenanceRequest).
const PLAY_INVITED_GROUPS: string[] = ["billing", "ig"];

process.env.GCLOUD_PROJECT = process.env.GCLOUD_PROJECT || "prohost-f766f";
// eslint-disable-next-line @typescript-eslint/no-var-requires
const exportsMap = require("../index") as Record<string, { __endpoint?: { serviceAccountEmail?: unknown } } | undefined>;

function accountOf(name: string): string | undefined {
  const sa = exportsMap[name]?.__endpoint?.serviceAccountEmail;
  return typeof sa === "string" ? sa : undefined;
}

test("every custom runtime account is one of the six group accounts", () => {
  for (const name of Object.keys(exportsMap)) {
    const sa = accountOf(name);
    if (sa) assert.match(sa, /^run-(admin|auth|billing|ig|public|triggers)@prohost-f766f\.iam\.gserviceaccount\.com$/, name);
  }
});

test("functions that reach Google Play keep an account that is invited in Play Console", () => {
  for (const name of PLAY_REACHING) {
    assert.ok(exportsMap[name], `${name} is no longer exported: update PLAY_REACHING`);
    const sa = accountOf(name);
    if (!sa) continue; // default account: invited today
    const group = sa.split("@")[0].replace("run-", "");
    assert.ok(PLAY_INVITED_GROUPS.includes(group), `${name} runs as ${sa}, which is not invited in Play Console`);
  }
});
