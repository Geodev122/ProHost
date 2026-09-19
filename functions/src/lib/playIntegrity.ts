import * as logger from "firebase-functions/logger";
import { adminApp } from "./admin";

// Matches app/build.gradle.kts's applicationId.
const PACKAGE_NAME = "app.geonajjar.prohost";

interface DecodedIntegrityToken {
  tokenPayloadExternal?: {
    appIntegrity?: { appRecognitionVerdict?: string; packageName?: string; certificateSha256Digest?: string[] };
    deviceIntegrity?: { deviceRecognitionVerdict?: string[] };
    accountDetails?: { appLicensingVerdict?: string };
    environmentDetails?: { playProtectVerdict?: string; appAccessRiskVerdict?: { appsDetected?: string[] } };
  };
}

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
    const deviceVerdicts = decoded.tokenPayloadExternal?.deviceIntegrity?.deviceRecognitionVerdict || [];
    const licensingVerdict = decoded.tokenPayloadExternal?.accountDetails?.appLicensingVerdict;
    const playProtectVerdict = decoded.tokenPayloadExternal?.environmentDetails?.playProtectVerdict;

    const meetsDeviceIntegrity = deviceVerdicts.includes("MEETS_DEVICE_INTEGRITY") || deviceVerdicts.includes("MEETS_STRONG_INTEGRITY");

    logger.info("play_integrity_evaluation", {
      uid,
      appVerdict,
      deviceVerdicts,
      licensingVerdict,
      playProtectVerdict,
      meetsDeviceIntegrity,
      isAuthenticPlayBinary: appVerdict === "PLAY_RECOGNIZED"
    });

    if (appVerdict !== "PLAY_RECOGNIZED" || !meetsDeviceIntegrity) {
      logger.warn("play_integrity_verdict_anomaly", {
        uid,
        appVerdict,
        deviceVerdicts,
        licensingVerdict,
        playProtectVerdict
      });
    }
  } catch (e) {
    logger.warn("play_integrity_check_error", { uid, error: e instanceof Error ? e.message : String(e) });
  }
}
