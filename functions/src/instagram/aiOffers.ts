import { getFirestore } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import "../lib/admin";
import { OfferBasePlan, OfferData, OfferPhase, appAdapter } from "./appAdapter";

/** Offers tagged `promo` in Play are code-only campaigns and never quoted to a stranger (same tag the app's PlayPromo uses). */
const PROMO_TAG = "promo";

/** ISO-8601 period (P1M, P1Y, P7D...) in months; 0 when unreadable. Same rule as the app side. */
function periodMonths(iso: string | null | undefined): number {
  const m = /^P(?:(\d+)Y)?(?:(\d+)M)?(?:(\d+)W)?(?:(\d+)D)?$/.exec(iso ?? "");
  if (!m) return 0;
  const [, y, mo, w, d] = m.map((x) => Number(x ?? 0));
  return y * 12 + mo + (w * 7 + d) / 30.4375;
}

const REFRESH_MS = 15 * 60_000;
const STALE_MS = 6 * 3_600_000;

const plural = (n: number, w: string) => `${n} ${w}${n === 1 ? "" : "s"}`;
const trim = (n: number) => (Math.round(n * 10) / 10).toString();

function phaseText(p: OfferPhase): string {
  const per = periodMonths(p.duration);
  const total = per * (p.recurrenceCount || 1);
  const span = total >= 12 && total % 12 === 0 ? plural(total / 12, "year") : plural(Number(trim(total)) || 1, "month");
  const v = p.pricing.US && p.pricing.US !== "n/a" ? p.pricing.US : p.pricing.LB;
  if (!v || v === "n/a") return "";
  if (v === "free") return `${span} free`;
  if (/^-\d+%$/.test(v)) return `${v.slice(1)} off for ${span}`;
  return `${v} for ${span}`;
}

/** Live offers a NEW subscriber can get, in plain words, straight from Play's data (no invented numbers). Pure. */
export function formatOffers(basePlans: OfferBasePlan[]): string {
  const lines: string[] = [];
  for (const bp of basePlans) {
    if (bp.state !== "ACTIVE") continue;
    const months = periodMonths(bp.period);
    const kind = months >= 12 ? "Yearly" : months >= 1 ? "Monthly" : "";
    if (!kind) continue;
    const list = bp.prices.US && bp.prices.US !== "n/a" ? bp.prices.US : bp.prices.LB;
    const every = kind === "Yearly" ? "year" : "month";
    const offers = bp.offers.filter((o: OfferData) =>
      o.state === "ACTIVE" && !o.tags.includes(PROMO_TAG) && (o.availableIn.US || o.availableIn.LB || o.otherRegions) && /new customers/.test(o.eligibility));
    const best = offers
      .map((o) => ({ o, text: o.phases.map(phaseText).filter(Boolean).join(", then ") }))
      .filter((x) => x.text);
    const base = list && list !== "n/a" ? `regular price ${list} per ${every}` : `regular price shown in the app`;
    if (!best.length) lines.push(`- ${kind} plan: no special offer right now, ${base}.`);
    else for (const x of best) lines.push(`- ${kind} plan offer: ${x.text}, then ${base}.`);
  }
  return lines.join("\n");
}

let memo: { text: string; at: number } | null = null;

/** Cached text of the live offers; refreshed from Google Play at most every 15 minutes. Empty string = unknown. */
export async function liveOffersText(now: number, read: () => Promise<{ basePlans: OfferBasePlan[] }> = () => appAdapter().readOffers()): Promise<string> {
  if (memo && now - memo.at < REFRESH_MS) return memo.text;
  const ref = getFirestore().collection("app_config").doc("instagram_offers");
  const cached = (await ref.get().catch(() => null))?.data();
  if (cached && now - Number(cached.at) < REFRESH_MS) { memo = { text: String(cached.text ?? ""), at: Number(cached.at) }; return memo.text; }
  try {
    const text = formatOffers((await read()).basePlans);
    await ref.set({ text, at: now }).catch(() => undefined);
    memo = { text, at: now };
    return text;
  } catch (e) {
    logger.warn("ig offers refresh failed", { message: (e as Error).message.slice(0, 200) });
    // keep serving the last known offers for a few hours, never older
    if (cached && now - Number(cached.at) < STALE_MS) return String(cached.text ?? "");
    return "";
  }
}

export const OFFERS_UNKNOWN = "LIVE OFFERS: unavailable right now. Do not mention any price, discount or free period; say the current offer is shown in the app before paying.";

export function offersContext(text: string): string {
  return text
    ? `LIVE PREMIUM OFFERS (from Google Play, exact and current; describe ONLY these, never add numbers):\n${text}\nPremium is one subscription with a monthly and a yearly plan; the app shows the exact price before paying.`
    : OFFERS_UNKNOWN;
}
