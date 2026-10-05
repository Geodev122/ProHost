import { test } from "node:test";
import * as assert from "node:assert/strict";
import { PLAY_ACK_WINDOW_MS, healthFromProbe, hoursUntilRefund, rescueHint } from "./billingRescueLogic";

test("the health probe treats Play's 'unknown token' answer as working credentials", () => {
  assert.equal(healthFromProbe("invalid"), "ok");
  assert.equal(healthFromProbe(null), "ok");
  assert.equal(healthFromProbe("config"), "config");
  assert.equal(healthFromProbe("transient"), "transient");
});

test("hours left before Play's 3-day refund", () => {
  const t0 = 1_000_000_000_000;
  assert.equal(hoursUntilRefund(t0, t0), 72);
  assert.equal(hoursUntilRefund(t0, t0 + 2 * 3_600_000 + 1), 69);
  assert.equal(hoursUntilRefund(t0, t0 + PLAY_ACK_WINDOW_MS + 1), 0);
});

test("rescue hints follow the stored error", () => {
  assert.match(rescueHint("config: Play Developer API denied access (403)", false), /Play Console access/);
  assert.match(rescueHint("owned_by_other: bought while another account", true), /another ProHost account/);
  assert.match(rescueHint("transient: 503", false), /unreachable/);
  assert.match(rescueHint("acknowledge failed", false), /acknowledge/);
  assert.match(rescueHint("unsupported_product: package_growth_mrr is a retired plan", true), /Retired plan/);
});
