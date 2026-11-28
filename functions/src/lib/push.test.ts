import { test } from "node:test";
import assert from "node:assert/strict";
import { NOTIFICATION_TTL_MS, deviceTokens, fcmMessage, isDeadTokenError, notificationDoc, prunedTokenList } from "./push";

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
  assert.deepEqual(m.android, { priority: "high", ttl: NOTIFICATION_TTL_MS });
  assert.equal(m.apns.payload.aps["thread-id"], "GENERAL");
  assert.equal(m.apns.headers["apns-collapse-id"], "n1");
  assert.ok(Number(m.apns.headers["apns-expiration"]) > Date.now() / 1000);
  assert.deepEqual(m.apns.payload.aps.alert, { title: "Title", body: "Body" });
  assert.equal(m.apns.headers["apns-priority"], "10");
  assert.equal(fcmMessage("t", "a", "b", {}).data.notificationId, undefined);
});

test("deviceTokens merges the device list with the legacy field, without duplicates", () => {
  assert.deepEqual(deviceTokens({ fcmTokens: ["a", "b"], fcmToken: "b" }), ["a", "b"]);
  assert.deepEqual(deviceTokens({ fcmToken: "x" }), ["x"]);
  assert.deepEqual(deviceTokens({ fcmTokens: ["a", 3, "", null] }), ["a"]);
  assert.deepEqual(deviceTokens(undefined), []);
  assert.equal(deviceTokens({ fcmTokens: Array.from({ length: 15 }, (_, i) => `t${i}`) }).length, 10);
});

test("a categorised push is threaded by its category on iOS", () => {
  assert.equal(fcmMessage("t", "a", "b", { category: "BOOKING_UPDATE" }).apns.payload.aps["thread-id"], "BOOKING_UPDATE");
});

test("dead token errors: unregistered / invalid tokens, and invalid-argument only when it names the token", () => {
  assert.equal(isDeadTokenError({ code: "messaging/registration-token-not-registered" }), true);
  assert.equal(isDeadTokenError({ code: "messaging/invalid-registration-token" }), true);
  assert.equal(isDeadTokenError({ code: "messaging/invalid-argument", message: "The registration token is not a valid FCM registration token" }), true);
  assert.equal(isDeadTokenError({ code: "messaging/invalid-argument", message: "Invalid JSON payload received." }), false);
  assert.equal(isDeadTokenError({ code: "messaging/internal-error" }), false);
});

test("the stored token list drops dead tokens and keeps the newest 10", () => {
  const twelve = Array.from({ length: 12 }, (_, i) => `t${i}`);
  assert.deepEqual(prunedTokenList(twelve, ["t11"]), ["t1", "t2", "t3", "t4", "t5", "t6", "t7", "t8", "t9", "t10"]);
  assert.deepEqual(prunedTokenList(["a", "a", "b"], []), ["a", "b"]);
  assert.deepEqual(prunedTokenList(undefined, []), []);
});
