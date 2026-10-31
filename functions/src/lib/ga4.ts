import { createHash } from "node:crypto";
import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import "./admin";

/**
 * Server-side GA4 events (Measurement Protocol) for things the app can't see reliably:
 * Play RTDN renewals/cancellations, package lapses, system-rejected bookings.
 *
 * Consent: only sent when the user opted in on their device (user_profiles.analyticsConsent
 * == "GRANTED", mirrored by the app) and the app reported its GA app-instance id.
 * Config lives in the server-only doc app_config/ga4 {apiSecret, firebaseAppId?, debug?}
 * rather than a defineSecret, so a missing secret never breaks the functions deploy —
 * events are simply skipped until the owner sets it (see docs/ANALYTICS.md).
 */

export const DEFAULT_FIREBASE_APP_ID = "1:646730915838:android:345a7e12d5994456c8eaaf";
const MP_URL = "https://www.google-analytics.com/mp/collect";
const MP_DEBUG_URL = "https://www.google-analytics.com/debug/mp/collect";
const CONFIG_TTL_MS = 10 * 60 * 1000;

export type Ga4Param = string | number | boolean | null | undefined | Array<Record<string, string | number>>;

export interface Ga4Config {
  apiSecret: string;
  firebaseAppId: string;
  debug: boolean;
}

export interface Ga4Target {
  appInstanceId: string;
  userId?: string;
}

/** Same derivation as AnalyticsTracker.transactionId on Android, so GA4 dedupes purchases. */
export function transactionIdFor(orderId: string): string {
  return createHash("sha256").update(orderId, "utf8").digest("hex").slice(0, 24);
}

const BLOCKED_KEY = /(e?mail|phone|full_?name|first_?name|last_?name|uid|password|token|address)/i;
const EMAIL_LIKE = /[^\s@]+@[^\s@]+\.[^\s@]+/;

/** Who to attribute an event to, or null when the person hasn't opted in. */
export function targetFromProfile(profile: Record<string, unknown> | undefined): Ga4Target | null {
  if (!profile || profile.analyticsConsent !== "GRANTED") return null;
  const appInstanceId = profile.gaAppInstanceId;
  if (typeof appInstanceId !== "string" || appInstanceId.length === 0) return null;
  const code = typeof profile.displayCode === "string" && profile.displayCode.length > 0 ? profile.displayCode : undefined;
  return { appInstanceId, userId: code };
}

export function buildGa4Payload(target: Ga4Target, name: string, params: Record<string, Ga4Param>): Record<string, unknown> {
  const clean: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(params)) {
    if (value === null || value === undefined) continue;
    if (key !== "item_id" && BLOCKED_KEY.test(key)) continue;
    if (typeof value === "string") {
      const t = value.trim();
      if (!t || EMAIL_LIKE.test(t)) continue;
      clean[key.slice(0, 40)] = t.slice(0, 100);
    } else if (typeof value === "boolean") {
      clean[key.slice(0, 40)] = value ? "true" : "false";
    } else {
      clean[key.slice(0, 40)] = value;
    }
  }
  clean.engagement_time_msec = 1;
  clean.source = "server";
  const payload: Record<string, unknown> = {
    app_instance_id: target.appInstanceId,
    non_personalized_ads: true,
    events: [{ name: name.slice(0, 40), params: clean }],
  };
  if (target.userId) payload.user_id = target.userId;
  return payload;
}

let cached: { config: Ga4Config | null; at: number } | null = null;

async function loadConfig(): Promise<Ga4Config | null> {
  if (cached && Date.now() - cached.at < CONFIG_TTL_MS) return cached.config;
  let config: Ga4Config | null = null;
  try {
    const snap = await getFirestore().collection("app_config").doc("ga4").get();
    const data = snap.data();
    if (data && typeof data.apiSecret === "string" && data.apiSecret.length > 0) {
      config = {
        apiSecret: data.apiSecret,
        firebaseAppId: typeof data.firebaseAppId === "string" && data.firebaseAppId ? data.firebaseAppId : DEFAULT_FIREBASE_APP_ID,
        debug: data.debug === true,
      };
    }
  } catch (e) {
    logger.warn("ga4: could not read app_config/ga4", e);
  }
  cached = { config, at: Date.now() };
  return config;
}

/** Fire-and-forget: never throws into the caller (billing/booking flows must not fail on analytics). */
export async function sendGa4Event(uid: string, name: string, params: Record<string, Ga4Param> = {}): Promise<void> {
  try {
    const config = await loadConfig();
    if (!config) return;
    const profile = (await getFirestore().collection("user_profiles").doc(uid).get()).data();
    const target = targetFromProfile(profile);
    if (!target) return;
    const url = `${config.debug ? MP_DEBUG_URL : MP_URL}?firebase_app_id=${encodeURIComponent(config.firebaseAppId)}` +
      `&api_secret=${encodeURIComponent(config.apiSecret)}`;
    const res = await fetch(url, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(buildGa4Payload(target, name, params)),
      // Never let analytics hold up a billing callable or an RTDN delivery.
      signal: AbortSignal.timeout(5000),
    });
    if (config.debug) {
      logger.info(`ga4 debug ${name}: ${res.status} ${await res.text()}`);
    } else if (!res.ok) {
      logger.warn(`ga4: ${name} returned HTTP ${res.status}`);
    }
  } catch (e) {
    logger.warn(`ga4: failed to send ${name}`, e);
  }
}
