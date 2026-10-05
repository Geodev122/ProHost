import { HttpsError } from "firebase-functions/v2/https";
import { getFirestore } from "firebase-admin/firestore";
import { getDownloadURL, getStorage } from "firebase-admin/storage";
import { onCall } from "../lib/callable";
import "../lib/admin";

/**
 * Admin › Listings › "View Verification Document": a listing stores only a private
 * `gs://` reference to the host's verification document (never a download URL — listings
 * are readable by every signed-in user, and download tokens bypass Storage rules). This
 * returns a download link to the admin alone; it is never written anywhere.
 */
export const adminVerificationDocUrl = onCall<{ spaceId?: string }>(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in required.");
  if (request.auth.token.role !== "ADMIN") throw new HttpsError("permission-denied", "Admins only.");
  const spaceId = request.data?.spaceId;
  if (typeof spaceId !== "string" || spaceId.length === 0 || spaceId.length > 200) {
    throw new HttpsError("invalid-argument", "spaceId is required.");
  }
  const listing = (await getFirestore().collection("workspace_listings").doc(spaceId).get()).data();
  const ref = listing?.verificationDocUrl as string | undefined;
  if (!ref) throw new HttpsError("not-found", "This listing has no verification document.");
  if (!ref.startsWith("gs://")) return { url: ref }; // legacy value, migrated by migrateLegacyListingFields
  const withoutScheme = ref.slice("gs://".length);
  const slash = withoutScheme.indexOf("/");
  const bucket = withoutScheme.slice(0, slash);
  const path = withoutScheme.slice(slash + 1);
  const file = getStorage().bucket(bucket).file(path);
  const [exists] = await file.exists();
  if (!exists) throw new HttpsError("not-found", "The verification document file is missing.");
  return { url: await getDownloadURL(file) };
});
