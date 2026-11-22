import { test } from "node:test";
import assert from "node:assert/strict";
import { AppleSubscriptionFacts, appAccountTokenFor, appleAccessUntil, appleStatusFor, isAppleProduct } from "./appleCatalog";

const NOW = 1_800_000_000_000;
const base: AppleSubscriptionFacts = {
  productId: "prohost.premium.monthly", originalTransactionId: "1000", transactionId: "1001",
  expiresMillis: NOW + 86_400_000, revocationMillis: null, autoRenews: true, inBillingRetry: false,
  gracePeriodExpiresMillis: null, appAccountToken: null, environment: "Production",
};

test("Apple lifecycle maps onto Play's statuses", () => {
  assert.equal(appleStatusFor(base, NOW), "ACTIVE");
  assert.equal(appleStatusFor({ ...base, autoRenews: false }, NOW), "CANCELED");
  assert.equal(appleStatusFor({ ...base, autoRenews: null }, NOW), "ACTIVE");
  assert.equal(appleStatusFor({ ...base, revocationMillis: NOW - 1 }, NOW), "REVOKED");
  const lapsed = { ...base, expiresMillis: NOW - 1 };
  assert.equal(appleStatusFor(lapsed, NOW), "EXPIRED");
  assert.equal(appleStatusFor({ ...lapsed, inBillingRetry: true }, NOW), "ON_HOLD");
  assert.equal(appleStatusFor({ ...lapsed, inBillingRetry: true, gracePeriodExpiresMillis: NOW + 5 }, NOW), "GRACE_PERIOD");
  assert.equal(appleAccessUntil({ ...lapsed, gracePeriodExpiresMillis: NOW + 5 }), NOW + 5);
});

test("appAccountToken is a stable v4-shaped UUID per account", () => {
  const t = appAccountTokenFor("uid-123");
  assert.match(t, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  assert.equal(appAccountTokenFor("uid-123"), t);
  assert.notEqual(appAccountTokenFor("uid-124"), t);
});

test("only the two Premium products count", () => {
  assert.ok(isAppleProduct("prohost.premium.monthly"));
  assert.ok(isAppleProduct("prohost.premium.yearly"));
  assert.ok(!isAppleProduct("package_pro_mrr"));
  assert.ok(!isAppleProduct(null));
});
