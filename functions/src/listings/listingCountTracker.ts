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
    await getFirestore().collection("user_profiles").doc(ownerId).set(
      { activeListingCount: FieldValue.increment(-1) },
      { merge: true }
    );
  }
);
