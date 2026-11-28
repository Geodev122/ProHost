import { test } from "node:test";
import assert from "node:assert/strict";
import { bestActiveSubscription } from "./bestSubscription";

const NOW = 1_800_000_000_000;
const DAY = 86_400_000;

test("Unlimited outranks 1 listing while both are active (deferred downgrade)", () => {
  const best = bestActiveSubscription([
    { id: "single", basePlanId: "single-monthly", status: "ACTIVE", expiryDate: NOW + 40 * DAY },
    { id: "unl", basePlanId: "pro-monthly1", status: "CANCELED", expiryDate: NOW + 5 * DAY },
  ], NOW);
  assert.equal(best?.id, "unl");
});

test("when Unlimited has ended, the 1 listing plan takes over", () => {
  const best = bestActiveSubscription([
    { id: "single", basePlanId: "single-monthly", status: "ACTIVE", expiryDate: NOW + 40 * DAY },
    { id: "unl", basePlanId: "pro-monthly1", status: "EXPIRED", expiryDate: NOW - DAY },
  ], NOW);
  assert.equal(best?.id, "single");
});

test("Apple products are ranked by tier too", () => {
  const best = bestActiveSubscription([
    { id: "apple_1", platform: "ios", productId: "prohost.single.yearly", status: "ACTIVE", expiryDate: NOW + 300 * DAY },
    { id: "apple_2", platform: "ios", productId: "prohost.premium.monthly", status: "GRACE_PERIOD", expiryDate: NOW + 2 * DAY },
  ], NOW);
  assert.equal(best?.id, "apple_2");
});

test("same tier: later expiry wins; revoked, pending and lapsed ones never count", () => {
  assert.equal(bestActiveSubscription([
    { id: "a", basePlanId: "pro-monthly1", status: "ACTIVE", expiryDate: NOW + 10 * DAY },
    { id: "b", basePlanId: "pro-yearly", status: "ACTIVE", expiryDate: NOW + 300 * DAY },
  ], NOW)?.id, "b");
  assert.equal(bestActiveSubscription([
    { id: "r", basePlanId: "pro-yearly", status: "ACTIVE", expiryDate: NOW + 300 * DAY, adminRevoked: true },
    { id: "p", basePlanId: "pro-yearly", status: "PENDING", expiryDate: NOW + 300 * DAY },
    { id: "h", basePlanId: "pro-yearly", status: "ON_HOLD", expiryDate: NOW + 300 * DAY },
    { id: "c", basePlanId: "pro-yearly", status: "CANCELED", expiryDate: NOW - 1 },
  ], NOW), null);
});
