import { test } from "node:test";
import assert from "node:assert/strict";
import { NOTIFICATION_TTL_MS, fcmMessage, notificationDoc } from "./push";

test("in-app notifications keep the push routing and expire after 48 h", () => {
  const doc = notificationDoc("Booking accepted", "See you there", { category: "BOOKING_UPDATE", targetTab: "pro_rentals", bookingId: "b1" }, 1000);
  assert.equal(doc.category, "BOOKING_UPDATE");
  assert.equal(doc.targetTab, "pro_rentals");
  assert.equal(doc.bookingId, "b1");
  assert.equal(doc.spaceId, null);
  assert.equal(doc.read, false);
  assert.equal(doc.expireAt - doc.createdAt, NOTIFICATION_TTL_MS);
  assert.equal(doc.ttlAt.toMillis(), doc.expireAt, "the TTL timestamp equals the millis expiry");
  assert.equal(NOTIFICATION_TTL_MS, 48 * 60 * 60 * 1000);
});

test("a push without a category is still stored", () => {
  assert.equal(notificationDoc("t", "b", {}, 0).category, "GENERAL");
});

test("fcmMessage keeps the Android data-only payload and adds the iOS alert", () => {
  const m = fcmMessage("tok", "Title", "Body", { targetTab: "pro_rentals", bookingId: "b1" }, "n1");
  assert.equal(m.token, "tok");
  assert.deepEqual(m.data, { title: "Title", body: "Body", targetTab: "pro_rentals", bookingId: "b1", notificationId: "n1" });
  assert.deepEqual(m.android, { priority: "high" });
  assert.deepEqual(m.apns.payload.aps.alert, { title: "Title", body: "Body" });
  assert.equal(m.apns.headers["apns-priority"], "10");
  assert.equal(fcmMessage("t", "a", "b", {}).data.notificationId, undefined);
});
