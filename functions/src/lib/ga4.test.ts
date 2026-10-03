import { test } from "node:test";
import * as assert from "node:assert/strict";
import { buildGa4Payload, targetFromProfile, transactionIdFor } from "./ga4";

test("no target without opt-in or app instance id", () => {
  assert.equal(targetFromProfile(undefined), null);
  assert.equal(targetFromProfile({ analyticsConsent: "DENIED", gaAppInstanceId: "abc" }), null);
  assert.equal(targetFromProfile({ analyticsConsent: "GRANTED" }), null);
  assert.equal(targetFromProfile({ analyticsConsent: "GRANTED", gaAppInstanceId: "" }), null);
});

test("target uses the display code, never the uid", () => {
  const t = targetFromProfile({ analyticsConsent: "GRANTED", gaAppInstanceId: "inst", displayCode: "U-ABC123", id: "firebase-uid" });
  assert.deepEqual(t, { appInstanceId: "inst", userId: "U-ABC123" });
});

test("payload drops PII and trims values", () => {
  const p = buildGa4Payload({ appInstanceId: "inst" }, "subscription_renewed", {
    item_id: "pro_monthly",
    email: "a@b.co",
    note: "someone@example.com",
    reason: "x".repeat(150),
    value: 9.99,
    is_trial: false,
    skipped: undefined,
  }) as { app_instance_id: string; user_id?: string; events: Array<{ name: string; params: Record<string, unknown> }> };
  assert.equal(p.app_instance_id, "inst");
  assert.equal(p.user_id, undefined);
  const params = p.events[0].params;
  assert.equal(params.item_id, "pro_monthly");
  assert.equal(params.email, undefined);
  assert.equal(params.note, undefined);
  assert.equal((params.reason as string).length, 100);
  assert.equal(params.value, 9.99);
  assert.equal(params.is_trial, "false");
  assert.equal("skipped" in params, false);
  assert.equal(params.source, "server");
});

test("transaction id is a stable 24-char sha256 prefix", () => {
  const id = transactionIdFor("GPA.1234-5678-9012-34567");
  assert.equal(id.length, 24);
  assert.match(id, /^[0-9a-f]{24}$/);
  assert.equal(id, transactionIdFor("GPA.1234-5678-9012-34567"));
});
