import { getFirestore } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { onSchedule } from "firebase-functions/v2/scheduler";
import "../lib/admin";
import { clearConfigCache, loadAllConfigs } from "./config";
import { brandRef, brandSecretsRef, isLegacyBrand } from "./store";
import { refreshToken } from "./graph";
import { alertAdmin } from "./notify";

const DAY = 86_400_000;
export const daysLeft = (expiresAt: number, now = Date.now()): number => Math.max(0, Math.floor((expiresAt - now) / DAY));

/** Weekly: long-lived Instagram tokens live 60 days and may be refreshed once they are a day old. */
export const igTokenRefresh = onSchedule({ schedule: "0 6 * * 1", timeoutSeconds: 120 }, async () => {
  for (const config of await loadAllConfigs(true)) {
    if (!config.accessToken) continue;
    try {
      const r = await refreshToken(config.accessToken);
      if (!r.token) throw new Error("no token in refresh response");
      const expiresAt = Date.now() + r.expiresInSec * 1000;
      if (isLegacyBrand(config.brandId)) {
        await getFirestore().collection("app_config").doc("instagram").set({ accessToken: r.token, tokenExpiresAt: expiresAt }, { merge: true });
      } else {
        await brandSecretsRef(config.brandId).set({ instagram: { accessToken: r.token } }, { merge: true });
        await brandRef(config.brandId).set({ platforms: { instagram: { tokenExpiresAt: expiresAt } } }, { merge: true });
      }
      clearConfigCache(config.brandId);
      logger.info(`igTokenRefresh: ${config.brandId} ok, ${Math.floor(r.expiresInSec / 86400)} days`);
    } catch (e) {
      logger.error("igTokenRefresh failed", { brand: config.brandId, message: (e as Error).message });
      const left = config.tokenExpiresAt ? daysLeft(config.tokenExpiresAt) : null;
      if (left === null || left < 14) await alertAdmin(config, "Instagram token refresh failed", `${(e as Error).message} (${left ?? "?"} days left). Generate a new token and paste it in the console's Secrets tab.`);
    }
  }
});
