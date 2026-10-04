import { HttpsError } from "firebase-functions/v2/https";
import { onCall } from "../lib/callable";
import { getFirestore } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { DEFAULT_FIREBASE_APP_ID } from "../lib/ga4";
import "../lib/admin";

/**
 * Admin Callable: configureGa4ApiSecret({ apiSecret, firebaseAppId, debug })
 * Writes or updates the server-only document `app_config/ga4` in Firestore via Admin SDK.
 */
export const configureGa4ApiSecret = onCall(async (request) => {
  if (!request.auth?.uid) {
    throw new HttpsError("unauthenticated", "Admin authentication required.");
  }
  const db = getFirestore();
  const callerProfile = await db.collection("user_profiles").doc(request.auth.uid).get();
  if (callerProfile.data()?.role !== "ADMIN" && request.auth.token.role !== "ADMIN") {
    throw new HttpsError("permission-denied", "Only administrators can configure GA4 settings.");
  }

  const apiSecret = (request.data?.apiSecret as string | undefined)?.trim() || "GLocAt_CTbGiQ3HRc4I2mQ";
  const firebaseAppId = (request.data?.firebaseAppId as string | undefined)?.trim() || DEFAULT_FIREBASE_APP_ID;
  const debug = Boolean(request.data?.debug ?? false);

  await db.collection("app_config").doc("ga4").set(
    {
      apiSecret,
      firebaseAppId,
      debug,
      updatedAt: Date.now(),
      updatedBy: request.auth.uid,
    },
    { merge: true }
  );

  logger.info("ga4_config_updated", { uid: request.auth.uid, firebaseAppId });
  return { ok: true, message: "GA4 Measurement Protocol API Secret configured successfully." };
});
