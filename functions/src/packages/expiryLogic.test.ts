import { test } from "node:test";
import * as assert from "node:assert/strict";
import { expiryDecision } from "./expiryLogic";

test("Play subscribers are never demoted when Play can't be read", () => {
  assert.equal(expiryDecision({ kind: "error", errorKind: "config" }), "skip");
  assert.equal(expiryDecision({ kind: "error", errorKind: "transient" }), "skip");
});

test("Play's answer decides for Play subscribers", () => {
  assert.equal(expiryDecision({ kind: "has_access" }), "keep");
  assert.equal(expiryDecision({ kind: "no_access" }), "demote");
  assert.equal(expiryDecision({ kind: "error", errorKind: "invalid" }), "demote");
});

test("non-Play entitlements lapse on their stored expiry", () => {
  assert.equal(expiryDecision({ kind: "not_play" }), "demote");
});
