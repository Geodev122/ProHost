import * as logger from "firebase-functions/logger";
import { adminApp } from "./admin";
import { recordAuditLog } from "./auditLog";

// Matches app/build.gradle.kts's applicationId.
const PACKAGE_NAME = "app.geonajjar.prohost";

interface DecodedIntegrityToken {
  tokenPayloadExternal?: {
    requestDetails?: {
      requestPackageName?: string;
      timestampMillis?: string;
    };
    appIntegrity?: {
      appRecognitionVerdict?: string;
      packageName?: string;
      certificateSha256Digest?: string[];
    };
    deviceIntegrity?: {
      deviceRecognitionVerdict?: string[];
    };
    environmentDetails?: {
      playProtectVerdict?: string;
      appAccessRiskVerdict?: string;
    };
    accountDetails?: {
      appLicensingVerdict?: string;
    };
  };
}

/**
 * Verifies a Play Integrity token server-side, log-only — this is a deliberate
 * first-rollout design (see PlayIntegrityManager.kt/assignInitialRole.ts): a
 * missing token, a failed API call, or an unexpected verdict is recorded via
 * structured Cloud Logging and Audit Trail, and this function returns normally either way.
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
    const payload = decoded.tokenPayloadExternal;
    const appVerdict = payload?.appIntegrity?.appRecognitionVerdict;
    const deviceVerdicts = payload?.deviceIntegrity?.deviceRecognitionVerdict ?? [];
    const playProtectVerdict = payload?.environmentDetails?.playProtectVerdict;
    const licensingVerdict = payload?.accountDetails?.appLicensingVerdict;

    const isRecognized = appVerdict === "PLAY_RECOGNIZED";
    const meetsIntegrity = deviceVerdicts.length > 0;

    if (!isRecognized || !meetsIntegrity) {
      logger.warn("play_integrity_verdict_anomaly", {
        uid,
        appVerdict,
        deviceVerdicts,
        playProtectVerdict,
        licensingVerdict,
      });

      await recordAuditLog({
        actionType: "PLAY_INTEGRITY_ANOMALY",
        details: `Play Integrity check for user ${uid}: appVerdict=${appVerdict ?? "UNKNOWN"}, deviceVerdicts=[${deviceVerdicts.join(", ")}], playProtect=${playProtectVerdict ?? "UNSPECIFIED"}`,
        actorEmail: "security@prohost.app",
        severity: "WARN",
      });
    } else {
      logger.info("play_integrity_verdict_passed", {
        uid,
        appVerdict,
        deviceVerdicts,
        playProtectVerdict,
      });
    }
  } catch (e) {
    logger.warn("play_integrity_check_error", { uid, error: e instanceof Error ? e.message : String(e) });
  }
}
