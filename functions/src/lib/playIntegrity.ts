import * as logger from "firebase-functions/logger";
import { adminApp } from "./admin";

// Matches app/build.gradle.kts's applicationId.
const PACKAGE_NAME = "app.geonajjar.prohost";

interface DecodedIntegrityToken {
  tokenPayloadExternal?: {
    appIntegrity?: { appRecognitionVerdict?: string };
    deviceIntegrity?: { deviceRecognitionVerdict?: string[] };
  };
}

/**
 * Verifies a Play Integrity token server-side, log-only — this is a deliberate
 * first-rollout design (see PlayIntegrityManager.kt/assignInitialRole.ts): a
 * missing token, a failed API call, or an unexpected verdict is recorded via
 * structured Cloud Logging and this function returns normally either way. It
 * NEVER throws and NEVER blocks the caller — this is intentionally the same
 * "ship it observably first" pattern this project already used for the
 * account-suspension and booking-conflict-guard rollouts, since false
 * positives on a legitimate device (a sideloaded debug build, a rooted test
 * device, an emulator) would otherwise lock out real users with no way to
 * diagnose it remotely. Uses the Cloud Function's own runtime credentials
 * (Application Default Credentials, already configured via ../lib/admin's
 * bare initializeApp()) to authenticate to the Play Integrity API, rather
 * than adding a new npm dependency for one REST call.
 */
export async function checkPlayIntegrityLogOnly(token: string, uid: string): Promise<void> {
  try {
    const credential = adminApp.options.credential;
    const accessToken = await credential?.getAccessToken();
    if (!accessToken) {
      logger.warn("play_integrity_check_skipped", { uid, reason: "no_access_token" });
      return;
    }

    const response = await fetch(
      `https://playintegrity.googleapis.com/v1/${PACKAGE_NAME}:decodeIntegrityToken`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${accessToken.access_token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ integrity_token: token }),
      }
    );

    if (!response.ok) {
      logger.warn("play_integrity_check_failed", { uid, status: response.status, statusText: response.statusText });
      return;
    }

    const decoded = (await response.json()) as DecodedIntegrityToken;
    const appVerdict = decoded.tokenPayloadExternal?.appIntegrity?.appRecognitionVerdict;
    if (appVerdict !== "PLAY_RECOGNIZED") {
      logger.warn("play_integrity_verdict_anomaly", {
        uid,
        appVerdict,
        deviceIntegrity: decoded.tokenPayloadExternal?.deviceIntegrity,
      });
    }
  } catch (e) {
    logger.warn("play_integrity_check_error", { uid, error: e instanceof Error ? e.message : String(e) });
  }
}
