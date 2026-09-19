import { onCall, HttpsError } from "firebase-functions/v2/https";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
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

  try {
    const userRecord = await getFirebaseAuth().getUserByPhoneNumber(phone);
    const profileDoc = await getDb().collection("user_profiles").doc(userRecord.uid).get();
    const hasPinSet = !!(profileDoc.data()?.pinHash);
    return { isRegistered: true, hasPinSet };
  } catch (e: unknown) {
    if ((e as { code?: string })?.code === "auth/user-not-found") {
      return { isRegistered: false, hasPinSet: false };
    }
    throw new HttpsError("internal", "Could not check registration status. Please try again.");
  }
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

  const profileDoc = await getDb().collection("user_profiles").doc(uid).get();
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

  const expectedHash = saltedHash(pin, data.pinSalt as string);
  if (expectedHash !== data.pinHash) {
    throw new HttpsError("unauthenticated", "Incorrect PIN or unrecognized number.");
  }

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

  await getDb().collection("user_profiles").doc(uid).update({
    pinHash: hash,
    pinSalt: salt,
    pinSetAtMillis: Date.now(),
  });

  return { success: true };
});
