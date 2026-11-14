import { test } from "node:test";
import assert from "node:assert/strict";
import { cleanReport, isBlockedPair, statusAfter, MAX_DETAILS, REPORT_REASONS } from "./reportRules";

test("a listing report needs a listing and a known reason", () => {
  assert.equal(typeof cleanReport({ targetType: "listing", reason: "SCAM" }), "string");
  assert.equal(typeof cleanReport({ targetType: "listing", listingId: "abc", reason: "NOPE" }), "string");
  assert.equal(typeof cleanReport({ targetType: "thing", listingId: "abc", reason: "SCAM" }), "string");
  assert.equal(typeof cleanReport(null), "string");
  const ok = cleanReport({ targetType: "listing", listingId: "abc", reason: "SCAM", details: "  deposit asked  ", alsoBlock: true });
  assert.deepEqual(ok, {
    targetType: "listing", listingId: "abc", targetUid: null, bookingId: null, reason: "SCAM", details: "deposit asked", alsoBlock: true,
  });
});

test("a person report needs a uid; ids with path characters are refused", () => {
  assert.equal(typeof cleanReport({ targetType: "user", reason: "HARASSMENT" }), "string");
  assert.equal(typeof cleanReport({ targetType: "user", targetUid: "a/b", reason: "HARASSMENT" }), "string");
  const ok = cleanReport({ targetType: "user", targetUid: "u1", bookingId: "b/1", reason: "HARASSMENT" });
  assert.notEqual(typeof ok, "string");
  if (typeof ok !== "string") {
    assert.equal(ok.bookingId, null); // a malformed optional id is dropped, not fatal
    assert.equal(ok.alsoBlock, false);
  }
});

test("details are capped", () => {
  assert.equal(typeof cleanReport({ targetType: "user", targetUid: "u1", reason: "OTHER", details: "x".repeat(MAX_DETAILS + 1) }), "string");
  assert.notEqual(typeof cleanReport({ targetType: "user", targetUid: "u1", reason: "OTHER", details: "x".repeat(MAX_DETAILS) }), "string");
});

test("the approved list has six reasons", () => {
  assert.equal(REPORT_REASONS.length, 6);
});

test("a block works in both directions", () => {
  assert.equal(isBlockedPair("a", ["b"], "b", []), true);
  assert.equal(isBlockedPair("a", undefined, "b", ["a"]), true);
  assert.equal(isBlockedPair("a", ["c"], "b", ["d", 5]), false);
  assert.equal(isBlockedPair("a", "b", "b", null), false);
});

test("admin actions map to a status", () => {
  assert.equal(statusAfter("dismiss"), "DISMISSED");
  assert.equal(statusAfter("hide_listing"), "ACTIONED");
  assert.equal(statusAfter("suspend_user"), "ACTIONED");
  assert.equal(statusAfter("reopen"), "NEW");
});
