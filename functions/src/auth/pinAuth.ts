import { onCall, HttpsError } from "firebase-functions/v2/https";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { createHash, randomBytes } from "crypto";
import { adminApp } from "../lib/admin";

const getDb = () => getFirestore(adminApp);
const getFirebaseAuth = () => getAuth(adminApp);

function saltedHash(pin: string, salt: string): string {
  return createHash("sha256").update(`${pin}:${salt}`).digest("hex");
}

function generateSalt(): string {
  return randomBytes(16).toString("hex");
}

/**
 * Public callable: checks whether a phone number has a registered account
 * and whether a PIN has been set. The app uses this to decide which auth
 * step to show next (PIN entry for returning users, OTP for new signups).
 */
export const checkPhoneRegistered = onCall(async (request) => {
  const { phone } = request.data as { phone?: string };
  if (!phone || !/^\+[1-9]\d{5,14}$/.test(phone)) {
    throw new HttpsError("invalid-argument", "A valid E.164 phone number is required.");
  }

  // Always return ok:true to prevent phone-number enumeration. Old clients
  // expecting isRegistered still receive the field (as true), but no Firestore
  // or Auth lookup is made — registration state is inferred by the client from
  // the OTP verification step instead.
  return { ok: true, isRegistered: true };
});

/**
 * Public callable: verifies a 6-digit PIN against the stored hash and
 * returns a Firebase custom auth token. The client immediately calls
 * signInWithCustomToken() — no OTP SMS required for returning users.
 */
export const verifyPinAndIssueToken = onCall(async (request) => {
  const { phone, pin } = request.data as { phone?: string; pin?: string };

  if (!phone || !/^\+[1-9]\d{5,14}$/.test(phone)) {
    throw new HttpsError("invalid-argument", "A valid E.164 phone number is required.");
  }
  if (!pin || !/^\d{6}$/.test(pin)) {
    throw new HttpsError("invalid-argument", "PIN must be exactly 6 digits.");
  }

  let uid: string;
  try {
    const userRecord = await getFirebaseAuth().getUserByPhoneNumber(phone);
    uid = userRecord.uid;
  } catch {
    // Generic error: never reveal whether the phone is registered (prevents enumeration)
    throw new HttpsError("unauthenticated", "Incorrect PIN or unrecognized number.");
  }

  const userRef = getDb().collection("user_profiles").doc(uid);
  const profileDoc = await userRef.get();
  const data = profileDoc.data();

  if (!data?.pinHash || !data?.pinSalt) {
    throw new HttpsError(
      "failed-precondition",
      "No PIN is set for this account. Please sign in via SMS code first to set your PIN."
    );
  }

  if (data.isSuspended === true) {
    throw new HttpsError("permission-denied", "This account has been suspended. Please contact support.");
  }

  // Brute-force protection: check if the account is currently locked out.
  const now = Date.now();
  const pinLockedUntilMillis = data.pinLockedUntilMillis as number | null | undefined;
  if (pinLockedUntilMillis && pinLockedUntilMillis > now) {
    throw new HttpsError("resource-exhausted", "Too many failed attempts. Try again later.");
  }

  const expectedHash = saltedHash(pin, data.pinSalt as string);
  if (expectedHash !== data.pinHash) {
    // Increment failed attempt counter then re-read to decide whether to lock.
    await userRef.set({ pinFailedAttempts: FieldValue.increment(1) }, { merge: true });
    const updatedDoc = await userRef.get();
    const newCount = (updatedDoc.data()?.pinFailedAttempts as number) ?? 1;
    if (newCount >= 10) {
      await userRef.set({ pinLockedUntilMillis: now + 24 * 60 * 60 * 1000 }, { merge: true });
    } else if (newCount >= 5) {
      await userRef.set({ pinLockedUntilMillis: now + 15 * 60 * 1000 }, { merge: true });
    }
    throw new HttpsError("unauthenticated", "Incorrect PIN or unrecognized number.");
  }

  // Success: reset brute-force counters.
  await userRef.set({ pinFailedAttempts: 0, pinLockedUntilMillis: null }, { merge: true });

  const customToken = await getFirebaseAuth().createCustomToken(uid);
  return { token: customToken };
});

/**
 * Authenticated callable: sets or resets the signed-in caller's 6-digit PIN.
 * Requires a live Firebase Auth session (acquired after OTP sign-in during
 * initial signup, or after OTP verification during a forgot-PIN reset).
 */
export const setUserPin = onCall(async (request) => {
  if (!request.auth?.uid) {
    throw new HttpsError("unauthenticated", "You must be signed in to set a PIN.");
  }
  const { pin } = request.data as { pin?: string };
  if (!pin || !/^\d{6}$/.test(pin)) {
    throw new HttpsError("invalid-argument", "PIN must be exactly 6 digits.");
  }

  const uid = request.auth.uid;
  const salt = generateSalt();
  const hash = saltedHash(pin, salt);

  await getDb().collection("user_profiles").doc(uid).set({
    pinHash: hash,
    pinSalt: salt,
    pinSetAtMillis: Date.now(),
  }, { merge: true });

  return { success: true };
});
