import * as functionsV1 from "firebase-functions/v1";
import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { cleanUpAccountData } from "../lib/accountCleanup";
import { recordAuditLog } from "../lib/auditLog";
import "../lib/admin";

/**
 * An Auth account deleted anywhere but the app (Firebase console, Admin SDK, a script)
 * used to leave its profile — name, email, phone — and listings behind. Same cleanup as
 * in-app deletion (lib/accountCleanup.ts); a no-op when deleteOwnAccount already ran,
 * since that removes the profile before the Auth user. (Auth delete triggers exist only
 * in the v1 API.)
 */
export const onAuthUserDeleted = functionsV1
  .region("europe-west1")
  .auth.user()
  .onDelete(async (user) => {
    const profile = await getFirestore().collection("user_profiles").doc(user.uid).get();
    if (!profile.exists) return;
    const { listings } = await cleanUpAccountData(user.uid, (user.email ?? "").toLowerCase());
    logger.info(`onAuthUserDeleted: cleaned up a deleted account (${listings} listing(s))`);
    await recordAuditLog({
      actionType: "ACCOUNT_DELETED_OUTSIDE_APP",
      details: `Auth account ${user.uid} was deleted outside the app; its profile and ${listings} listing(s) were removed.`,
      actorEmail: "system@prohost.app",
      severity: "SECURE",
    }).catch(() => undefined);
  });
