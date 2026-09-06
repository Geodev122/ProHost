import { onDocumentCreated, onDocumentDeleted } from "firebase-functions/v2/firestore";
import { getFirestore, FieldValue } from "firebase-admin/firestore";

/**
 * Maintains `user_profiles/{ownerId}.activeListingCount` — a denormalized
 * counter, since Firestore security rules have no way to COUNT a query
 * (`workspace_listings where ownerId == X`) directly. This is what lets
 * firestore.rules' `withinListingLimit()` reject a create server-side instead
 * of trusting the client's own count (ProSpaceViewModel.createNewSpaceListing
 * did this check, but only client-side — nothing stopped a raw Firestore SDK
 * write from skipping it entirely).
 */
export const onWorkspaceListingCreated = onDocumentCreated(
  "workspace_listings/{spaceId}",
  async (event) => {
    const listing = event.data?.data();
    const ownerId = listing?.ownerId;
    if (!ownerId) return;
    await getFirestore().collection("user_profiles").doc(ownerId).set(
      { activeListingCount: FieldValue.increment(1) },
      { merge: true }
    );
  }
);

export const onWorkspaceListingDeleted = onDocumentDeleted(
  "workspace_listings/{spaceId}",
  async (event) => {
    const listing = event.data?.data();
    const ownerId = listing?.ownerId;
    if (!ownerId) return;
    // Plain FieldValue.increment(-1) would let this go negative for any
    // listing that existed before this tracker was deployed (never counted by
    // onWorkspaceListingCreated in the first place) — and a negative count
    // paradoxically satisfies withinListingLimit()'s `count < limit` check,
    // granting a LIMITED_3_TIER host *unlimited* headroom instead of none.
    // Clamp at 0 in a transaction instead of a raw increment.
    const db = getFirestore();
    const profileRef = db.collection("user_profiles").doc(ownerId);
    await db.runTransaction(async (tx) => {
      const snap = await tx.get(profileRef);
      const current = (snap.data()?.activeListingCount as number | undefined) ?? 0;
      tx.set(profileRef, { activeListingCount: Math.max(0, current - 1) }, { merge: true });
    });
  }
);
