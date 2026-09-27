import * as logger from "firebase-functions/logger";
import { HttpsError } from "firebase-functions/v2/https";
import { adminApp } from "./admin";

// Matches app/build.gradle.kts's applicationId.
const PACKAGE_NAME = "app.geonajjar.prohost";

interface DecodedIntegrityToken {
  tokenPayloadExternal?: {
    requestDetails?: { requestPackageName?: string; nonce?: string; timestampMillis?: string };
    appIntegrity?: {
      appRecognitionVerdict?: string;
      packageName?: string;
      certificateSha256Digest?: string[];
    };
    deviceIntegrity?: { deviceRecognitionVerdict?: string[] };
    accountDetails?: { appLicensingVerdict?: string };
    environmentDetails?: {
      playProtectVerdict?: string;
      appAccessRiskVerdict?: { appsDetected?: string[] };
    };
  };
}

async function decodeIntegrityToken(
  token: string,
  uid: string
): Promise<DecodedIntegrityToken | null> {
  try {
    const credential = adminApp.options.credential;
    const accessToken = await credential?.getAccessToken();
    if (!accessToken) {
      logger.warn("play_integrity_check_skipped", { uid, reason: "no_access_token" });
      return null;
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
      logger.warn("play_integrity_decode_failed", {
        uid,
        status: response.status,
        statusText: response.statusText,
      });
      return null;
    }

    return (await response.json()) as DecodedIntegrityToken;
  } catch (e) {
    logger.warn("play_integrity_check_error", {
      uid,
      error: e instanceof Error ? e.message : String(e),
    });
    return null;
  }
}

const MAX_TOKEN_AGE_MS = 10 * 60 * 1000;

/**
 * Rejects tokens requested for another app, too long ago, or for another user. The
 * app's nonce is base64url("<uid>:<random>") (PlayIntegrityManager.requestIntegrityToken),
 * so a token captured from one account can't be replayed for another.
 */
function assertRequestBinding(
  details: { requestPackageName?: string; nonce?: string; timestampMillis?: string } | undefined,
  uid: string
): void {
  const fail = (reason: string): never => {
    logger.warn("play_integrity_request_binding_failed", { uid, reason });
    throw new HttpsError("failed-precondition", "App integrity check failed. Please try again.");
  };
  if (details?.requestPackageName !== PACKAGE_NAME) fail("package_mismatch");
  const ts = Number(details?.timestampMillis);
  if (!Number.isFinite(ts) || Math.abs(Date.now() - ts) > MAX_TOKEN_AGE_MS) fail("stale_token");
  const nonceText = Buffer.from(details?.nonce ?? "", "base64url").toString("utf8");
  if (!nonceText.startsWith(`${uid}:`)) fail("nonce_user_mismatch");
}

/**
 * Enforces Play Integrity verdicts matching the enabled checks in Play Console:
 *
 *   ON  — App integrity checks:   appRecognitionVerdict must be PLAY_RECOGNIZED or UNEVALUATED.
 *                                  UNRECOGNIZED_VERSION (modified / unofficial binary) is blocked.
 *   ON  — Play licence checks:    appLicensingVerdict UNLICENSED is blocked.
 *                                  UNEVALUATED is allowed (first install, no Google account, etc.).
 *   ON  — Virtual integrity:      logged only; MEETS_VIRTUAL_INTEGRITY = Play Games for PC —
 *                                  not blocked because host owners on PC is a valid use case.
 *   OFF — Device integrity:       logged for observability, never enforced.
 *   OFF — Play Protect status:    logged for observability, never enforced.
 *   OFF — App access risk:        logged for observability, never enforced.
 *
 * Decode failures (network errors, missing credentials) are non-fatal so a transient
 * infrastructure issue never locks real users out. The absence of a token (e.g. on a
 * device with no Play Services) is also allowed — the caller decides whether a token
 * is required before calling this.
 *
 * Throws HttpsError("failed-precondition") when an enforced verdict fails.
 */
export async function enforcePlayIntegrity(token: string, uid: string): Promise<void> {
  const decoded = await decodeIntegrityToken(token, uid);
  if (!decoded) return; // decode failure is non-blocking

  const payload = decoded.tokenPayloadExternal;
  assertRequestBinding(payload?.requestDetails, uid);
  const appVerdict = payload?.appIntegrity?.appRecognitionVerdict;
  const deviceVerdicts = payload?.deviceIntegrity?.deviceRecognitionVerdict ?? [];
  const licensingVerdict = payload?.accountDetails?.appLicensingVerdict;
  const playProtectVerdict = payload?.environmentDetails?.playProtectVerdict;
  const appAccessRisk = payload?.environmentDetails?.appAccessRiskVerdict?.appsDetected ?? [];
  const isVirtualEnv = deviceVerdicts.includes("MEETS_VIRTUAL_INTEGRITY");

  logger.info("play_integrity_evaluation", {
    uid,
    appVerdict,
    deviceVerdicts,
    licensingVerdict,
    playProtectVerdict,
    appAccessRisk,
    isVirtualEnv,
  });

  // Enforce app integrity (Play Console: App integrity checks ON).
  // UNRECOGNIZED_VERSION = binary not in Play Store — modified or unofficial APK.
  // UNEVALUATED = Play couldn't evaluate (first install, no account) — allowed with a warning.
  if (appVerdict === "UNRECOGNIZED_VERSION") {
    logger.warn("play_integrity_app_verdict_blocked", { uid, appVerdict });
    throw new HttpsError(
      "failed-precondition",
      "App integrity check failed. Please install ProHost from the Google Play Store."
    );
  }
  if (appVerdict !== "PLAY_RECOGNIZED" && appVerdict !== "UNEVALUATED") {
    logger.warn("play_integrity_app_verdict_unknown", { uid, appVerdict });
    throw new HttpsError(
      "failed-precondition",
      "App integrity check failed. Please install ProHost from the Google Play Store."
    );
  }
  if (appVerdict === "UNEVALUATED") {
    logger.info("play_integrity_app_unevaluated", { uid, note: "allowed during transition" });
  }

  // Enforce Play licence (Play Console: Play licence checks ON).
  // UNLICENSED = not installed via Play Store for this account.
  // UNEVALUATED = couldn't check — allowed (common on fresh installs before Play syncs).
  if (licensingVerdict === "UNLICENSED") {
    logger.warn("play_integrity_licence_blocked", { uid, licensingVerdict });
    throw new HttpsError(
      "failed-precondition",
      "Licence check failed. Please install ProHost from the Google Play Store."
    );
  }

  // Log device integrity (Play Console: Device integrity checks OFF — not enforced).
  const meetsDeviceIntegrity =
    deviceVerdicts.includes("MEETS_DEVICE_INTEGRITY") ||
    deviceVerdicts.includes("MEETS_STRONG_INTEGRITY");
  if (!meetsDeviceIntegrity && !isVirtualEnv && deviceVerdicts.length > 0) {
    logger.info("play_integrity_device_not_enforced", {
      uid,
      deviceVerdicts,
      note: "device integrity is OFF in Play Console",
    });
  }

  // Log Play Games for PC / virtual environment (Play Console: Virtual integrity ON — logged only).
  if (isVirtualEnv) {
    logger.info("play_integrity_virtual_env", {
      uid,
      note: "request from Play Games for PC or virtual device — allowed",
    });
  }

  // Log Play Protect & app access risk (Play Console: both OFF — not enforced).
  if (playProtectVerdict && playProtectVerdict !== "NO_ISSUES") {
    logger.info("play_integrity_play_protect_info", {
      uid,
      playProtectVerdict,
      note: "Play Protect status is OFF in Play Console",
    });
  }
  if (appAccessRisk.length > 0) {
    logger.info("play_integrity_app_access_risk_info", {
      uid,
      appAccessRisk,
      note: "app access risk is OFF in Play Console",
    });
  }
}

