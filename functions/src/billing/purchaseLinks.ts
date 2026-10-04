/**
 * Links Play purchase tokens to ProHost accounts for purchases that carry no
 * obfuscatedExternalAccountId — i.e. ones started outside the app's purchase sheet,
 * such as a promo code redeemed in the Play Store or a resubscribe from Play.
 *
 * The first signed-in account whose device holds the token claims it (via
 * verifyAndRestorePurchase); every later RTDN for that token, or for an upgrade whose
 * linkedPurchaseToken points at it, resolves to the same account. Server-only
 * collection: play_purchase_links/{sha256(token)} (firestore.rules denies clients).
 */
import { createHash } from "node:crypto";
import { getFirestore } from "firebase-admin/firestore";
import "../lib/admin";

const COLLECTION = "play_purchase_links";

export function linkKey(purchaseToken: string): string {
  return createHash("sha256").update(purchaseToken, "utf8").digest("hex");
}

/** The account a purchase belongs to: Play's account id first, else a stored link. */
export async function resolvePurchaseUid(
  obfuscatedExternalAccountId: string | null | undefined,
  purchaseToken: string,
  linkedPurchaseToken?: string | null
): Promise<string | null> {
  if (obfuscatedExternalAccountId) return obfuscatedExternalAccountId;
  const db = getFirestore();
  for (const token of [purchaseToken, linkedPurchaseToken]) {
    if (!token) continue;
    const snap = await db.collection(COLLECTION).doc(linkKey(token)).get();
    const uid = snap.data()?.uid;
    if (typeof uid === "string" && uid.length > 0) return uid;
  }
  return null;
}

export type ClaimOutcome = "claimed" | "already_yours" | "owned_by_other";

/**
 * Atomically claims an unattributed purchase token for [uid]. Never re-assigns a token
 * another account already claimed. Also carries over a claim from [linkedPurchaseToken]
 * (an upgrade/resubscribe of a purchase this account already owns).
 */
export async function claimPurchaseToken(
  uid: string,
  purchaseToken: string,
  productId: string,
  linkedPurchaseToken?: string | null
): Promise<ClaimOutcome> {
  const db = getFirestore();
  const ref = db.collection(COLLECTION).doc(linkKey(purchaseToken));
  const outcome = await db.runTransaction(async (tx) => {
    const existing = (await tx.get(ref)).data()?.uid;
    if (existing === uid) return "already_yours" as const;
    if (typeof existing === "string" && existing.length > 0) return "owned_by_other" as const;
    if (linkedPurchaseToken) {
      const prior = (await tx.get(db.collection(COLLECTION).doc(linkKey(linkedPurchaseToken)))).data()?.uid;
      if (typeof prior === "string" && prior.length > 0 && prior !== uid) return "owned_by_other" as const;
    }
    tx.set(ref, { uid, productId, linkedAt: Date.now() });
    return "claimed" as const;
  });
  if (outcome === "claimed") {
    // Close any RTDN records that were waiting for this purchase's owner.
    const pending = await db.collection("play_billing_unresolved")
      .where("purchaseToken", "==", purchaseToken).where("resolved", "==", false).get();
    await Promise.all(pending.docs.map((d) => d.ref.update({ resolved: true, resolvedUid: uid, resolvedAt: Date.now() })));
  }
  return outcome;
}
