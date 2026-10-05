// Firestore security rules tests (run in the emulator: `npm test` in tests/rules).
// Covers the production-readiness findings: booking creation/transitions, profile
// server-owned fields and contact details, listing server fields and counters.
import { readFileSync } from "node:fs";
import { after, before, beforeEach, describe, test } from "node:test";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc, updateDoc } from "firebase/firestore";

let env;

const HOST = "host1";
const SPEC = "spec1";
const SPEC2 = "spec2";

const hostCtx = () => env.authenticatedContext(HOST, { role: "PRO_HOST", email: "h@x.com" }).firestore();
const specCtx = () =>
  env.authenticatedContext(SPEC, { role: "SPECIALIST", email: "s@x.com", phone_number: "+96170000001" }).firestore();
const spec2Ctx = () => env.authenticatedContext(SPEC2, { role: "SPECIALIST", email: "s2@x.com" }).firestore();

function booking(overrides = {}) {
  return {
    spaceId: "L1",
    ownerId: HOST,
    practitionerId: SPEC,
    totalAmountUsd: 120,
    status: "PENDING",
    reviewedAt: null,
    agreementUrl: null,
    rejectionReason: null,
    cancellationReasonCode: null,
    cancellationNote: null,
    cancelledByRole: null,
    paymentAcknowledgedByHost: false,
    paymentAcknowledgedBySpecialist: false,
    replacesBookingId: null,
    createdAt: 1,
    ...overrides,
  };
}

before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-prohost",
    firestore: {
      rules: readFileSync(new URL("../../firestore.rules", import.meta.url), "utf8"),
      host: "127.0.0.1",
      port: 8080,
    },
  });
});

after(async () => {
  await env?.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "user_profiles", HOST), { role: "PRO_HOST", ownerPackageId: "pro-montly", email: "h@x.com", phone: "" });
    await setDoc(doc(db, "user_profiles", SPEC), { role: "SPECIALIST", email: "s@x.com", phone: "" });
    await setDoc(doc(db, "user_profiles", SPEC2), { role: "SPECIALIST", email: "s2@x.com", phone: "", isSuspended: true });
    await setDoc(doc(db, "workspace_listings", "L1"), { ownerId: HOST, status: "ACTIVE", title: "Room", avatarEngagementViews: 3 });
    await setDoc(doc(db, "workspace_listings", "L2"), { ownerId: HOST, status: "DRAFT", title: "Draft" });
    await setDoc(doc(db, "booking_requests", "B_PENDING"), booking());
    await setDoc(doc(db, "booking_requests", "B_ACCEPTED"), booking({ status: "ACCEPTED" }));
    await setDoc(doc(db, "booking_requests", "B_OTHER"), booking({ practitionerId: SPEC2, status: "ACCEPTED" }));
  });
});

describe("booking create", () => {
  test("a PENDING request on an active listing is allowed", async () => {
    await assertSucceeds(setDoc(doc(specCtx(), "booking_requests", "new1"), booking()));
  });
  test("a request created already ACCEPTED is denied", async () => {
    await assertFails(setDoc(doc(specCtx(), "booking_requests", "new2"), booking({ status: "ACCEPTED" })));
  });
  test("review fields can't be pre-filled", async () => {
    await assertFails(setDoc(doc(specCtx(), "booking_requests", "new3"), booking({ agreementUrl: "https://x" })));
    await assertFails(setDoc(doc(specCtx(), "booking_requests", "new4"), booking({ paymentAcknowledgedByHost: true })));
  });
  test("a draft listing can't be booked", async () => {
    await assertFails(setDoc(doc(specCtx(), "booking_requests", "new5"), booking({ spaceId: "L2" })));
  });
  test("an edit may replace only the caller's own booking", async () => {
    await assertSucceeds(setDoc(doc(specCtx(), "booking_requests", "new6"), booking({ replacesBookingId: "B_ACCEPTED" })));
    await assertFails(setDoc(doc(specCtx(), "booking_requests", "new7"), booking({ replacesBookingId: "B_OTHER" })));
  });
  test("a Pro Host can't book", async () => {
    await assertFails(setDoc(doc(hostCtx(), "booking_requests", "new8"), booking({ practitionerId: HOST })));
  });
});

describe("booking transitions", () => {
  test("practitioner withdraws a pending request as SPECIALIST", async () => {
    await assertSucceeds(updateDoc(doc(specCtx(), "booking_requests", "B_PENDING"),
      { status: "CANCELLED", reviewedAt: 2, cancelledByRole: "SPECIALIST" }));
  });
  test("practitioner cancel without a role, or claiming ADMIN, is denied", async () => {
    await assertFails(updateDoc(doc(specCtx(), "booking_requests", "B_PENDING"), { status: "CANCELLED", reviewedAt: 2 }));
    await assertFails(updateDoc(doc(specCtx(), "booking_requests", "B_PENDING"),
      { status: "CANCELLED", reviewedAt: 2, cancelledByRole: "ADMIN" }));
  });
  test("practitioner can't accept their own request", async () => {
    await assertFails(updateDoc(doc(specCtx(), "booking_requests", "B_PENDING"), { status: "ACCEPTED", reviewedAt: 2 }));
  });
  test("host accepts or rejects a pending request", async () => {
    await assertSucceeds(updateDoc(doc(hostCtx(), "booking_requests", "B_PENDING"),
      { status: "ACCEPTED", reviewedAt: 2, agreementUrl: "https://lease" }));
  });
  test("host rejects a pending request", async () => {
    await assertSucceeds(updateDoc(doc(hostCtx(), "booking_requests", "B_PENDING"),
      { status: "REJECTED", reviewedAt: 2, rejectionReason: "busy" }));
  });
  test("host can't re-open a cancelled or move a rejected booking to accepted", async () => {
    await env.withSecurityRulesDisabled((ctx) =>
      setDoc(doc(ctx.firestore(), "booking_requests", "B_REJ"), booking({ status: "REJECTED" })));
    await assertFails(updateDoc(doc(hostCtx(), "booking_requests", "B_REJ"), { status: "ACCEPTED", reviewedAt: 3 }));
  });
  test("host cancels an accepted booking as PRO_HOST, or supersedes it for an edit", async () => {
    await assertSucceeds(updateDoc(doc(hostCtx(), "booking_requests", "B_ACCEPTED"),
      { status: "CANCELLED", reviewedAt: 3, cancelledByRole: "PRO_HOST", cancellationReasonCode: "OTHER" }));
    await assertSucceeds(updateDoc(doc(hostCtx(), "booking_requests", "B_OTHER"),
      { status: "CANCELLED", reviewedAt: 3, rejectionReason: "Superseded by an accepted edit (B-ABC123)" }));
  });
  test("host can't change the price or the practitioner", async () => {
    await assertFails(updateDoc(doc(hostCtx(), "booking_requests", "B_PENDING"), { totalAmountUsd: 1 }));
    await assertFails(updateDoc(doc(hostCtx(), "booking_requests", "B_PENDING"), { practitionerId: SPEC2 }));
  });
  test("both sides can acknowledge payment", async () => {
    await assertSucceeds(updateDoc(doc(hostCtx(), "booking_requests", "B_ACCEPTED"), { paymentAcknowledgedByHost: true }));
    await assertSucceeds(updateDoc(doc(specCtx(), "booking_requests", "B_ACCEPTED"), { paymentAcknowledgedBySpecialist: true }));
  });
});

describe("user_profiles", () => {
  test("create can't claim server-owned state", async () => {
    const db = env.authenticatedContext("newbie", { email: "n@x.com" }).firestore();
    await assertFails(setDoc(doc(db, "user_profiles", "newbie"), { role: "SPECIALIST", isVerified: false, emailVerified: true }));
    await assertFails(setDoc(doc(db, "user_profiles", "newbie"), { role: "SPECIALIST", isVerified: false, displayCode: "U-AAAAAA" }));
    await assertSucceeds(setDoc(doc(db, "user_profiles", "newbie"), { role: "SPECIALIST", isVerified: false, fullName: "N" }));
  });
  test("the WhatsApp contact number stays editable", async () => {
    await assertSucceeds(updateDoc(doc(specCtx(), "user_profiles", SPEC), { phone: "+96179999999" }));
  });
  test("email must be the signed-in email", async () => {
    await assertFails(updateDoc(doc(specCtx(), "user_profiles", SPEC), { email: "other@x.com" }));
    await assertSucceeds(updateDoc(doc(specCtx(), "user_profiles", SPEC), { fullName: "Sam", email: "s@x.com" }));
  });
  test("resend throttle and role stay server-only", async () => {
    await assertFails(updateDoc(doc(specCtx(), "user_profiles", SPEC), { emailVerificationResendCount: 0 }));
    await assertFails(updateDoc(doc(specCtx(), "user_profiles", SPEC), { role: "PRO_HOST" }));
  });
  test("a suspended user can't edit their profile", async () => {
    await assertFails(updateDoc(doc(spec2Ctx(), "user_profiles", SPEC2), { fullName: "X" }));
  });
  test("profiles are private", async () => {
    await assertFails(getDoc(doc(specCtx(), "user_profiles", HOST)));
  });
});

describe("workspace_listings", () => {
  test("engagement counters only go up by one", async () => {
    await assertSucceeds(updateDoc(doc(specCtx(), "workspace_listings", "L1"), { avatarEngagementViews: 4 }));
    await assertFails(updateDoc(doc(specCtx(), "workspace_listings", "L1"), { avatarEngagementViews: 500 }));
  });
  test("owner can edit content but not server fields", async () => {
    await assertSucceeds(updateDoc(doc(hostCtx(), "workspace_listings", "L1"), { title: "Better room" }));
    await assertFails(updateDoc(doc(hostCtx(), "workspace_listings", "L1"), { verificationRequestedAt: 5 }));
    await assertFails(updateDoc(doc(hostCtx(), "workspace_listings", "L1"), { isVerified: true }));
    await assertFails(updateDoc(doc(specCtx(), "workspace_listings", "L1"), { title: "Hijacked" }));
  });
  test("server-only collections are closed", async () => {
    await assertFails(getDoc(doc(specCtx(), "display_codes", "U-AAAAAA")));
    await assertFails(setDoc(doc(specCtx(), "mail", "m1"), { to: "a@b.c" }));
  });
});

describe("in-app notifications", () => {
  beforeEach(async () => {
    await env.withSecurityRulesDisabled((ctx) =>
      setDoc(doc(ctx.firestore(), "user_profiles", SPEC, "notifications", "n1"),
        { title: "Accepted", body: "See you", read: false, createdAt: 1, expireAt: 2 }));
  });
  test("the owner reads and marks read", async () => {
    await assertSucceeds(getDoc(doc(specCtx(), "user_profiles", SPEC, "notifications", "n1")));
    await assertSucceeds(updateDoc(doc(specCtx(), "user_profiles", SPEC, "notifications", "n1"), { read: true, readAt: 3 }));
  });
  test("nobody rewrites, creates or reads someone else's", async () => {
    await assertFails(updateDoc(doc(specCtx(), "user_profiles", SPEC, "notifications", "n1"), { title: "Fake" }));
    await assertFails(setDoc(doc(specCtx(), "user_profiles", SPEC, "notifications", "n2"), { title: "Fake", read: false }));
    await assertFails(getDoc(doc(hostCtx(), "user_profiles", SPEC, "notifications", "n1")));
  });
});
