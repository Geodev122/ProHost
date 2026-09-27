import { HttpsError } from "firebase-functions/v2/https";
import { onCall } from "../lib/callable";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import "../lib/admin";

interface LookupUserForGrantData {
  email?: string;
}

/**
 * Admin-only: resolves an email to the Auth account + profile so the admin can
 * confirm identity (name, UID, current role/package) before grantPackageToUser.
 */
export const lookupUserForGrant = onCall<LookupUserForGrantData>(async (request) => {
  const auth = request.auth;
  if (!auth) {
    throw new HttpsError("unauthenticated", "Sign in required.");
  }
  if (auth.token.role !== "ADMIN") {
    throw new HttpsError("permission-denied", "Only an Admin can look up users.");
  }

  const email = request.data?.email?.trim().toLowerCase();
  if (!email || !email.includes("@")) {
    throw new HttpsError("invalid-argument", "A valid email is required.");
  }

  let user;
  try {
    user = await getAuth().getUserByEmail(email);
  } catch {
    throw new HttpsError("not-found", `No account is registered with ${email}.`);
  }

  const profileSnap = await getFirestore().collection("user_profiles").doc(user.uid).get();
  const profile = profileSnap.data() ?? {};

  return {
    uid: user.uid,
    email: user.email ?? email,
    fullName: (profile.fullName as string | undefined) || user.displayName || "",
    role: (user.customClaims?.role as string | undefined) ?? (profile.role as string | undefined) ?? "SPECIALIST",
    hasProfile: profileSnap.exists,
    isSuspended: profile.isSuspended === true,
    isDisabled: user.disabled,
    ownerPackageId: (profile.ownerPackageId as string | null | undefined) ?? null,
    ownerPackageExpiryMillis: (profile.ownerPackageExpiryMillis as number | null | undefined) ?? null,
    activeListingCount: (profile.activeListingCount as number | undefined) ?? 0,
  };
});
