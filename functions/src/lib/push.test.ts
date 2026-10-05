import { test } from "node:test";
import assert from "node:assert/strict";
import { NOTIFICATION_TTL_MS, notificationDoc } from "./push";

test("in-app notifications keep the push routing and expire after 48 h", () => {
  const doc = notificationDoc("Booking accepted", "See you there", { category: "BOOKING_UPDATE", targetTab: "pro_rentals", bookingId: "b1" }, 1000);
  assert.equal(doc.category, "BOOKING_UPDATE");
  assert.equal(doc.targetTab, "pro_rentals");
  assert.equal(doc.bookingId, "b1");
  assert.equal(doc.spaceId, null);
  assert.equal(doc.read, false);
  assert.equal(doc.expireAt - doc.createdAt, NOTIFICATION_TTL_MS);
  assert.equal(NOTIFICATION_TTL_MS, 48 * 60 * 60 * 1000);
});

test("a push without a category is still stored", () => {
  assert.equal(notificationDoc("t", "b", {}, 0).category, "GENERAL");
});
