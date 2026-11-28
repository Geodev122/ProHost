import { test } from "node:test";
import assert from "node:assert/strict";
import { isStalePending } from "./expireStaleRequests";

const TODAY = "2026-12-01";

test("a pending dated request whose first day is past is stale", () => {
  assert.equal(isStalePending({ status: "PENDING", bookedSlots: [{ date: "2026-11-30" }, { date: "2026-12-05" }] }, TODAY), true);
});
test("today and future dates are not stale", () => {
  assert.equal(isStalePending({ status: "PENDING", bookedSlots: [{ date: "2026-12-01" }] }, TODAY), false);
  assert.equal(isStalePending({ status: "PENDING", startDate: "2026-12-10", durationMonths: 1 }, TODAY), false);
});
test("a monthly request starting in the past is stale; accepted ones and undated legacy requests never are", () => {
  assert.equal(isStalePending({ status: "PENDING", startDate: "2026-11-15", durationMonths: 3 }, TODAY), true);
  assert.equal(isStalePending({ status: "ACCEPTED", startDate: "2026-11-15", durationMonths: 3 }, TODAY), false);
  assert.equal(isStalePending({ status: "PENDING", startDate: "Next Monday" }, TODAY), false);
});
