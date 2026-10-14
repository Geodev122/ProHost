/**
 * The marketed promo-code campaign (e.g. PROHOST2026, first month free) shown by the app's Premium
 * screen as a countdown card. Play has no API to create promo codes or count their redemptions,
 * so ProHost keeps:
 *  - `promo_campaigns/current` { active, code, title, benefit, endsAt, maxRedemptions, redeemed,
 *    basePlanKind }: set by the Maintenance task `promocampaign`, readable by signed-in users
 *    (firestore.rules), server-written only;
 *  - the redemption count: every NEW subscription is checked once with Play's v1 purchase (its
 *    promotionCode / promotionType) and, when it used the campaign code, `redeemed` goes up by one.
 * The app hides the card when the campaign ends, is switched off, or `redeemed` reaches
 * `maxRedemptions` — Play's own custom-code limit can't go below 2,000.
 */
import { logger } from "firebase-functions/v2";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { PACKAGE_NAME, getPlayPublisher } from "./billingHelpers";
import { linkKey } from "./purchaseLinks";

export const PROMO_COLLECTION = "promo_campaigns";
export const PROMO_DOC_ID = "current";

/** Pure: did this purchase redeem the campaign's code? Codes are case-insensitive in Play. */
export function usedCampaignCode(campaignCode: unknown, promotionCode: unknown, promotionType: unknown): boolean {
  if (typeof campaignCode !== "string" || !campaignCode.trim()) return false;
  if (typeof promotionCode !== "string" || !promotionCode.trim()) return false;
  // promotionType 1 = vanity/custom code (0 = one-time code); either may carry the code text.
  if (promotionType !== undefined && promotionType !== null && promotionType !== 0 && promotionType !== 1) return false;
  return promotionCode.trim().toUpperCase() === campaignCode.trim().toUpperCase();
}

/**
 * Checks a newly recorded subscription once (subscriptions/{id}.promoCheckedAt) and counts it
 * towards the campaign when it used the campaign code. Never throws: billing must not fail on it.
 */
export async function countPromoRedemption(purchaseToken: string, productId: string): Promise<void> {
  try {
    const db = getFirestore();
    const campaignRef = db.collection(PROMO_COLLECTION).doc(PROMO_DOC_ID);
    const campaign = (await campaignRef.get()).data();
    if (!campaign?.code) return;
    const publisher = await getPlayPublisher();
    const v1 = (await publisher.purchases.subscriptions.get({ packageName: PACKAGE_NAME, subscriptionId: productId, token: purchaseToken })).data;
    const subRef = db.collection("subscriptions").doc(linkKey(purchaseToken));
    await db.runTransaction(async (tx) => {
      const sub = await tx.get(subRef);
      if (!sub.exists || sub.get("promoCheckedAt")) return;
      const used = usedCampaignCode(campaign.code, v1.promotionCode, v1.promotionType);
      tx.update(subRef, { promoCheckedAt: Date.now(), promotionCode: v1.promotionCode ?? null, promotionType: v1.promotionType ?? null });
      if (used) tx.update(campaignRef, { redeemed: FieldValue.increment(1), lastRedeemedAt: Date.now() });
    });
  } catch (e) {
    logger.warn("countPromoRedemption failed", { error: String(e).slice(0, 200) });
  }
}
