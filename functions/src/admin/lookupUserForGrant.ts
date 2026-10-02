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

  // Accepts an email or an account code ("U-7K3Q9P").
  const input = request.data?.email?.trim() ?? "";
  const accountCode = /^U-[0-9A-Z]{6}$/i.test(input) ? input.toUpperCase() : null;
  const email = input.toLowerCase();
  if (!accountCode && (!email || !email.includes("@"))) {
    throw new HttpsError("invalid-argument", "Enter an email address or an account code like U-7K3Q9P.");
  }

  let user;
  try {
    if (accountCode) {
      const match = await getFirestore().collection("user_profiles")
        .where("displayCode", "==", accountCode).limit(1).get();
      if (match.empty) throw new Error("no match");
      user = await getAuth().getUser(match.docs[0].id);
    } else {
      user = await getAuth().getUserByEmail(email);
    }
  } catch {
    throw new HttpsError("not-found", `No account matches ${accountCode ?? email}.`);
  }

  const profileSnap = await getFirestore().collection("user_profiles").doc(user.uid).get();
  const profile = profileSnap.data() ?? {};

  return {
    uid: user.uid,
    displayCode: (profile.displayCode as string | undefined) ?? "",
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
