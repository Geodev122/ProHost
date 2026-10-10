// End-to-end backend flows against the Firebase emulators (functions + firestore + auth):
// the real deployed trigger and callable code (functions/lib) reacting to the same document
// writes the app makes. Rules are covered separately (tests/rules); here writes go through the
// Admin SDK and we check what the server does next: codes, pushes, occupancy, the conflict
// guard, edits, Change price, per-attendee re-pricing, withdrawals and cancellations.
//
//   cd tests/e2e && npm test
import { test, before } from "node:test";
import assert from "node:assert/strict";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const { initializeApp } = require("../../functions/node_modules/firebase-admin/app");
const { getFirestore } = require("../../functions/node_modules/firebase-admin/firestore");
const { getAuth } = require("../../functions/node_modules/firebase-admin/auth");

const PROJECT = "demo-prohost";
initializeApp({ projectId: PROJECT });
const db = getFirestore();
const auth = getAuth();
const FUNCTIONS = `http://127.0.0.1:5001/${PROJECT}/europe-west1`;

const HOST = "e2e-host";
const SPEC1 = "e2e-spec1";
const SPEC2 = "e2e-spec2";
const SPACE = "E2E-SPACE";

// Dates a week or more ahead, so nothing has "ended": Monday/Wednesday of two later weeks.
function iso(d) { return d.toISOString().slice(0, 10); }
const today = new Date(`${iso(new Date())}T00:00:00Z`);
const mon1 = new Date(today);
mon1.setUTCDate(today.getUTCDate() + ((8 - today.getUTCDay()) % 7 || 7) + 7);
const addDays = (d, n) => { const x = new Date(d); x.setUTCDate(x.getUTCDate() + n); return iso(x); };
const WEEK1 = [iso(mon1), addDays(mon1, 2)];
const WEEK2 = [addDays(mon1, 7), addDays(mon1, 9)];

async function waitFor(what, fn, timeoutMs = 45000) {
  const end = Date.now() + timeoutMs;
  let last;
  while (Date.now() < end) {
    last = await fn();
    if (last) return last;
    await new Promise((r) => setTimeout(r, 400));
  }
  assert.fail(`timed out waiting for ${what}`);
}

async function notifications(uid) {
  const s = await db.collection("user_profiles").doc(uid).collection("notifications").get();
  return s.docs.map((d) => d.data());
}
const hasNotification = (uid, title, category) => async () =>
  (await notifications(uid)).find((n) => n.title.includes(title) && (!category || n.category === category));

const booking = (id, practitionerId, overrides = {}) => ({
  id, spaceId: SPACE, spaceTitle: "E2E Clinic", ownerId: HOST, ownerName: "Host",
  practitionerId, practitionerName: practitionerId, subdivisionId: "SUB-1", subdivisionName: "Room A",
  formula: { type: "SHIFT", rateUsd: 20, startHour: "08:00", endHour: "12:00", daysOfWeek: ["Mon", "Wed"] },
  selectedDays: ["Mon", "Wed"], selectedCalendarDates: WEEK1, startDate: WEEK1[0], durationMonths: 1,
  totalAmountUsd: 40, status: "PENDING", createdAt: Date.now(),
  ...overrides,
});
const get = async (id) => (await db.collection("booking_requests").doc(id).get()).data();

async function idTokenFor(uid, role) {
  const email = `${uid}@e2e.test`;
  await auth.createUser({ uid, email, password: "e2e-password-1" }).catch(() => undefined);
  await auth.setCustomUserClaims(uid, { role });
  const res = await fetch(
    `http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=e2e`,
    { method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password: "e2e-password-1", returnSecureToken: true }) }
  );
  return (await res.json()).idToken;
}

async function callable(name, token, data) {
  const res = await fetch(`${FUNCTIONS}/${name}`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({ data }),
  });
  const body = await res.json();
  return body;
}

before(async () => {
  const profile = (role, name) => ({ role, fullName: name, email: `${name}@e2e.test`, phone: "" });
  await db.collection("user_profiles").doc(HOST).set(profile("PRO_HOST", "host"));
  await db.collection("user_profiles").doc(SPEC1).set(profile("SPECIALIST", "spec1"));
  await db.collection("user_profiles").doc(SPEC2).set(profile("SPECIALIST", "spec2"));
  await db.collection("workspace_listings").doc(SPACE).set({
    ownerId: HOST, title: "E2E Clinic", status: "ACTIVE",
    pricing: { strategyType: "MONTHLY", monthly: { rateUsd: 500 } },
    subdivisions: [
      {
        id: "SUB-1", name: "Room A", type: "ROOMS", displayCode: "D-E2E001", pricingMode: "STRATEGY_BASED",
        pricing: {
          strategyType: "SHIFT_BASED",
          shiftBased: {
            shifts: [{ name: "MORNING", startHour: 8, endHour: 12, isUnavailable: false, price: 20 }],
            distribution: { Mon: ["MORNING"], Wed: ["MORNING"] },
          },
        },
      },
      {
        id: "SUB-2", name: "Hall", type: "CONFERENCE_ROOM", displayCode: "D-E2E002", pricingMode: "PER_ATTENDEE",
        capacity: 20,
        pricing: {
          strategyType: "SHIFT_BASED",
          shiftBased: {
            shifts: [{ name: "MORNING", startHour: 8, endHour: 12, isUnavailable: false, price: 1 }],
            distribution: { Mon: ["MORNING"] },
          },
        },
        attendeeTiers: [{ id: "T1", name: "Standard", pricePerAttendeeUsd: 10, minAttendees: 1, maxAttendees: 20, isEnabled: true }],
      },
    ],
  });
});

test("a new request gets a B- code and reaches the host", async () => {
  await db.collection("booking_requests").doc("B1").set(booking("B1", SPEC1));
  await waitFor("display code", async () => /^B-/.test((await get("B1"))?.displayCode ?? ""));
  await waitFor("host push", hasNotification(HOST, "New Booking Request", "BOOKING_REQUEST"));
});

test("accepting publishes occupancy and tells the specialist", async () => {
  await db.collection("booking_requests").doc("B1").update({ status: "ACCEPTED", reviewedAt: Date.now() });
  const occ = await waitFor("occupancy", async () => (await db.collection("booking_occupancy").doc("B1").get()).data());
  assert.equal(occ.subdivisionId ?? "SUB-1", "SUB-1");
  assert.equal(occ.practitionerId, undefined, "occupancy is person-free");
  await waitFor("specialist push", hasNotification(SPEC1, "Accepted", "BOOKING_UPDATE"));
});

test("the conflict guard reverts an overlapping accept", async () => {
  await db.collection("booking_requests").doc("B2").set(booking("B2", SPEC2));
  await waitFor("B2 host push", async () => (await notifications(HOST)).filter((n) => n.bookingId === "B2").length > 0);
  await db.collection("booking_requests").doc("B2").update({ status: "ACCEPTED", reviewedAt: Date.now() });
  await waitFor("revert to PENDING", async () => (await get("B2")).status === "PENDING");
  await waitFor("host revert push", hasNotification(HOST, "Accept Reverted"));
  await waitFor("occupancy removed", async () => !(await db.collection("booking_occupancy").doc("B2").get()).exists);
});

test("an accepted edit releases the original in the same step", async () => {
  await db.collection("booking_requests").doc("B3").set(booking("B3", SPEC1, {
    replacesBookingId: "B1", selectedCalendarDates: WEEK2, startDate: WEEK2[0],
  }));
  await waitFor("change request push", hasNotification(HOST, "Booking Change Request"));
  await db.collection("booking_requests").doc("B3").update({ status: "ACCEPTED", reviewedAt: Date.now() });
  const b1 = await waitFor("original released", async () => { const b = await get("B1"); return b.status === "CANCELLED" && b; });
  assert.equal(b1.supersededBy, "B3");
  assert.equal((await get("B3")).status, "ACCEPTED");
  await waitFor("occupancy swapped", async () =>
    !(await db.collection("booking_occupancy").doc("B1").get()).exists &&
    (await db.collection("booking_occupancy").doc("B3").get()).exists);
});

test("Change price: only the owner, and each booking gets its chosen treatment", async () => {
  const specToken = await idTokenFor(SPEC1, "SPECIALIST");
  const denied = await callable("changeSlotPrice", specToken, {
    spaceId: SPACE, ref: { scopeId: "SUB-1", kind: "SHIFT", key: "MORNING" }, newPrice: 25, decisions: {},
  });
  assert.equal(denied.error?.status, "PERMISSION_DENIED");

  const hostToken = await idTokenFor(HOST, "PRO_HOST");
  const res = await callable("changeSlotPrice", hostToken, {
    spaceId: SPACE, ref: { scopeId: "SUB-1", kind: "SHIFT", key: "MORNING" }, newPrice: 25,
    decisions: { B3: "NOW", B2: "KEEP" },
  });
  assert.ok(res.result, JSON.stringify(res));
  assert.equal(res.result.affected, 2);
  assert.equal(res.result.now, 1);
  assert.equal(res.result.keep, 1);

  const b3 = await get("B3");
  assert.equal(b3.totalAmountUsd, 50, "2 future mornings × +5");
  assert.equal(b3.lastPriceChange.mode, "NOW");
  const b2 = await get("B2");
  assert.equal(b2.totalAmountUsd, 40, "KEEP leaves the total alone");
  assert.equal(b2.lastPriceChange.mode, "KEEP");

  const listing = (await db.collection("workspace_listings").doc(SPACE).get()).data();
  const sub1 = listing.subdivisions.find((s) => s.id === "SUB-1");
  assert.equal(sub1.pricing.shiftBased.shifts[0].price, 25);
  assert.equal(sub1.displayCode, "D-E2E001", "room code kept");
  assert.equal(listing.subdivisions.find((s) => s.id === "SUB-2").attendeeTiers[0].pricePerAttendeeUsd, 10);

  await waitFor("tenant price push", hasNotification(SPEC1, "Price update", "PRICE_CHANGE"));
  await waitFor("requester price push", hasNotification(SPEC2, "Price update", "PRICE_CHANGE"));

  const same = await callable("changeSlotPrice", hostToken, {
    spaceId: SPACE, ref: { scopeId: "SUB-1", kind: "SHIFT", key: "MORNING" }, newPrice: 25, decisions: {},
  });
  assert.equal(same.error?.status, "INVALID_ARGUMENT", "same price is refused");

  const tier = await callable("changeSlotPrice", hostToken, {
    spaceId: SPACE, ref: { scopeId: "SUB-2", kind: "TIER", key: "T1" }, newPrice: 12, decisions: {},
  });
  assert.ok(tier.result, JSON.stringify(tier));
  const slotOnTierRoom = await callable("changeSlotPrice", hostToken, {
    spaceId: SPACE, ref: { scopeId: "SUB-2", kind: "SHIFT", key: "MORNING" }, newPrice: 9, decisions: {},
  });
  assert.equal(slotOnTierRoom.error?.status, "FAILED_PRECONDITION", "per-attendee rooms change tiers, not markers");
});

test("per-attendee requests are re-priced: a wrong total is rejected by the system", async () => {
  const hall = (id, total) => booking(id, SPEC2, {
    subdivisionId: "SUB-2", subdivisionName: "Hall", selectedCalendarDates: [WEEK1[0]], startDate: WEEK1[0],
    selectedDays: ["Mon"], attendeeCount: 3, selectedAttendeePackageId: "T1", attendeePackageName: "Standard",
    attendeePackagePriceUsd: total / 3, totalAmountUsd: total,
  });
  await db.collection("booking_requests").doc("H1").set(hall("H1", 15));
  const h1 = await waitFor("auto-reject", async () => { const b = await get("H1"); return b.status === "REJECTED" && b; });
  assert.equal(h1.rejectedBySystem, true);
  await waitFor("not-sent push", hasNotification(SPEC2, "Not Sent"));
  assert.equal((await notifications(HOST)).filter((n) => n.bookingId === "H1").length, 0, "host never sees it");

  await db.collection("booking_requests").doc("H2").set(hall("H2", 36));
  await waitFor("valid request reaches host", async () => (await notifications(HOST)).find((n) => n.bookingId === "H2"));
  assert.equal((await get("H2")).status, "PENDING");
});

test("withdrawing a request and cancelling a tenancy notify the other side", async () => {
  await db.collection("booking_requests").doc("B2").update({
    status: "CANCELLED", reviewedAt: Date.now(), cancelledByRole: "SPECIALIST", cancellationReasonCode: "OTHER",
  });
  await waitFor("withdrawn push", hasNotification(HOST, "Request withdrawn"));

  await db.collection("booking_requests").doc("B3").update({
    status: "CANCELLED", reviewedAt: Date.now(), cancelledByRole: "PRO_HOST", cancellationReasonCode: "OTHER",
  });
  await waitFor("tenant cancel push", hasNotification(SPEC1, "Cancelled"));
  await waitFor("occupancy cleared", async () => !(await db.collection("booking_occupancy").doc("B3").get()).exists);
});
