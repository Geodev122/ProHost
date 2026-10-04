import { test } from "node:test";
import * as assert from "node:assert/strict";
import {
  ADMIN_FORCED_PLAN_ID, BASE_PLAN_MONTHLY, BASE_PLAN_YEARLY, grantsAccess, planInterval, planLabel, statusFor,
} from "./playCatalog";
import { PlaySubscription, fromV2 } from "./playSubscription";

const base: PlaySubscription = {
  productId: "package_pro_mrr", basePlanId: BASE_PLAN_YEARLY, autoRenewing: true, expiryMillis: Date.now() + 86_400_000,
  state: "ACTIVE", isPending: false, acknowledged: true, obfuscatedAccountId: "u", linkedPurchaseToken: null,
  orderId: "GPA.1", priceMicros: null, currency: null,
};

test("Play states map to the billing lifecycle", () => {
  assert.equal(statusFor(base), "ACTIVE");
  assert.equal(statusFor({ ...base, state: "IN_GRACE_PERIOD" }), "GRACE_PERIOD");
  assert.equal(statusFor({ ...base, state: "ON_HOLD" }), "ON_HOLD");
  assert.equal(statusFor({ ...base, state: "PAUSED" }), "PAUSED");
  assert.equal(statusFor({ ...base, state: "CANCELED" }), "CANCELED");
  assert.equal(statusFor({ ...base, state: "EXPIRED" }), "EXPIRED");
  assert.equal(statusFor({ ...base, state: "PENDING", isPending: true }), "PENDING");
  assert.equal(statusFor({ ...base, state: "PENDING_PURCHASE_CANCELED" }), "EXPIRED");
  assert.equal(statusFor(base, "REVOKED"), "REVOKED");
});

test("only active, grace and still-paid canceled subscriptions grant Pro Host", () => {
  const now = Date.now();
  assert.equal(grantsAccess("ACTIVE", 0, now), true);
  assert.equal(grantsAccess("GRACE_PERIOD", 0, now), true);
  assert.equal(grantsAccess("CANCELED", now + 1000, now), true);
  assert.equal(grantsAccess("CANCELED", now - 1000, now), false);
  for (const s of ["ON_HOLD", "PAUSED", "EXPIRED", "REVOKED", "REFUNDED", "PENDING"] as const) {
    assert.equal(grantsAccess(s, now + 1000, now), false, s);
  }
});

test("plan labels and intervals come from base plan ids", () => {
  assert.equal(planLabel(BASE_PLAN_MONTHLY), "Pro Host Monthly");
  assert.equal(planLabel(BASE_PLAN_YEARLY), "Pro Host Yearly");
  assert.equal(planLabel(ADMIN_FORCED_PLAN_ID), "Pro Host (granted)");
  assert.equal(planLabel(null), "Pro Host");
  assert.equal(planInterval(BASE_PLAN_MONTHLY), "monthly");
  assert.equal(planInterval("other"), null);
});

test("v2 reads the base plan and auto-renew from the latest line item", () => {
  const s = fromV2({
    subscriptionState: "SUBSCRIPTION_STATE_ACTIVE",
    lineItems: [{
      productId: "package_pro_mrr",
      expiryTime: new Date(Date.now() + 1000).toISOString(),
      offerDetails: { basePlanId: "pro-yearly" },
      autoRenewingPlan: { autoRenewEnabled: true },
    }],
  });
  assert.equal(s.basePlanId, "pro-yearly");
  assert.equal(s.autoRenewing, true);
});
