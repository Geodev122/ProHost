import { test } from "node:test";
import * as assert from "node:assert/strict";
import { classifyPlayError, fromV1, fromV2, PlayApiError } from "./playSubscription";

test("401/403 are setup problems, 400/404/410 unknown purchases, the rest transient", () => {
  assert.equal(classifyPlayError({ response: { status: 403 }, message: "x" }).kind, "config");
  assert.equal(classifyPlayError({ code: 401 }).kind, "config");
  assert.equal(classifyPlayError({ code: "404" }).kind, "invalid");
  assert.equal(classifyPlayError({ status: 410 }).kind, "invalid");
  assert.equal(classifyPlayError({ code: 503 }).kind, "transient");
  assert.equal(classifyPlayError(new Error("ECONNRESET")).kind, "transient");
  const already = new PlayApiError("config", 403, "m");
  assert.equal(classifyPlayError(already), already);
});

test("v2 maps the latest line item, account id, acknowledgement and price", () => {
  const future = new Date(Date.now() + 86_400_000).toISOString();
  const s = fromV2({
    subscriptionState: "SUBSCRIPTION_STATE_ACTIVE",
    acknowledgementState: "ACKNOWLEDGEMENT_STATE_PENDING",
    externalAccountIdentifiers: { obfuscatedExternalAccountId: "uid-1" },
    latestOrderId: "GPA.1",
    lineItems: [
      { productId: "old", expiryTime: "2020-01-01T00:00:00Z" },
      { productId: "pro_monthly", expiryTime: future, autoRenewingPlan: { recurringPrice: { currencyCode: "USD", units: "9", nanos: 990000000 } } } as never,
    ],
  });
  assert.equal(s.productId, "pro_monthly");
  assert.equal(s.expiryMillis, Date.parse(future));
  assert.equal(s.state, "ACTIVE");
  assert.equal(s.isPending, false);
  assert.equal(s.acknowledged, false);
  assert.equal(s.obfuscatedAccountId, "uid-1");
  assert.equal(s.orderId, "GPA.1");
  assert.equal(s.priceMicros, 9_990_000);
  assert.equal(s.currency, "USD");
});

test("v2 pending purchase is never grantable", () => {
  const s = fromV2({ subscriptionState: "SUBSCRIPTION_STATE_PENDING", lineItems: [] }, "pro_monthly");
  assert.equal(s.isPending, true);
  assert.equal(s.productId, "pro_monthly");
  assert.equal(s.expiryMillis, 0);
});

test("v1 fallback maps paymentState 0 to pending", () => {
  const s = fromV1({ paymentState: 0, expiryTimeMillis: String(Date.now() + 1000), acknowledgementState: 0 }, "p");
  assert.equal(s.isPending, true);
  assert.equal(s.acknowledged, false);
  const ok = fromV1({ paymentState: 1, expiryTimeMillis: String(Date.now() + 100000), acknowledgementState: 1, obfuscatedExternalAccountId: "u" }, "p");
  assert.equal(ok.state, "ACTIVE");
  assert.equal(ok.acknowledged, true);
  assert.equal(ok.obfuscatedAccountId, "u");
});
