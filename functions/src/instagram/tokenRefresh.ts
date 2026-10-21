import { getFirestore } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { onSchedule } from "firebase-functions/v2/scheduler";
import "../lib/admin";
import { clearConfigCache, loadConfig } from "./config";
import { refreshToken } from "./graph";
import { alertAdmin } from "./notify";

const DAY = 86_400_000;
export const daysLeft = (expiresAt: number, now = Date.now()): number => Math.max(0, Math.floor((expiresAt - now) / DAY));

/** Weekly: long-lived Instagram tokens live 60 days and may be refreshed once they are a day old. */
export const igTokenRefresh = onSchedule({ schedule: "0 6 * * 1", timeoutSeconds: 60 }, async () => {
  const config = await loadConfig(true);
  if (!config.accessToken) return;
  try {
    const r = await refreshToken(config.accessToken);
    if (!r.token) throw new Error("no token in refresh response");
    await getFirestore().collection("app_config").doc("instagram").set({ accessToken: r.token, tokenExpiresAt: Date.now() + r.expiresInSec * 1000 }, { merge: true });
    clearConfigCache();
    logger.info(`igTokenRefresh: ok, ${Math.floor(r.expiresInSec / 86400)} days`);
  } catch (e) {
    logger.error("igTokenRefresh failed", { message: (e as Error).message });
    const left = config.tokenExpiresAt ? daysLeft(config.tokenExpiresAt) : null;
    if (left === null || left < 14) await alertAdmin(config, "Instagram token refresh failed", `${(e as Error).message} (${left ?? "?"} days left). Generate a new token and re-run igsetup.`);
  }
});
