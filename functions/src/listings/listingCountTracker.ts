import { onDocumentCreated, onDocumentDeleted, onDocumentUpdated } from "firebase-functions/v2/firestore";
import { getFirestore, FieldValue } from "firebase-admin/firestore";

/**
 * Maintains `user_profiles/{ownerId}.activeListingCount` — a denormalized
 * count of the host's ACTIVE listings (Drafts and Paused listings excluded),
 * shown in the Owner Hub and admin views. Informational only: subscriptions
 * carry no listing limit. Status is
 * missing on documents written before this field existed, which reads as
 * ACTIVE (its own default) rather than as "not counted".
 */
/**
 * Counter/flag writes use update(), never set(merge) — a set on a profile or listing that
 * was just deleted (account deletion removes listings, then the profile) would recreate a
 * ghost document. Not-found is the expected outcome then, not an error.
 */
function ignoreNotFound(e: unknown): void {
  const code = (e as { code?: number | string }).code;
  if (code === 5 || code === "not-found") return;
  throw e;
}

function isActiveStatus(status: unknown): boolean {
  return status === undefined || status === null || status === "ACTIVE";
}

export const onWorkspaceListingCreated = onDocumentCreated(
  "workspace_listings/{spaceId}",
  async (event) => {
    const listing = event.data?.data();
    const ownerId = listing?.ownerId;
    if (!ownerId) return;
    // Stamped unconditionally (even for a Draft) — the client never writes this
    // field (DataModels.kt's SpaceListing.toFirestoreMap deliberately omits it,
    // same convention as isOwnerSuspended), so it can't be backdated/spoofed.
    // Feeds the Admin Console's "listings published by date" chart.
    await event.data?.ref.update({ createdAtMillis: Date.now() }).catch(ignoreNotFound);
    if (!isActiveStatus(listing?.status)) return;
    await getFirestore().collection("user_profiles").doc(ownerId)
      .update({ activeListingCount: FieldValue.increment(1) }).catch(ignoreNotFound);
  }
);

export const onWorkspaceListingDeleted = onDocumentDeleted(
  "workspace_listings/{spaceId}",
  async (event) => {
    const listing = event.data?.data();
    const ownerId = listing?.ownerId;
    if (!ownerId) return;
    // A Draft or Paused listing was never counted in the first place (see
    // isActiveStatus above) — decrementing here would just make the counter
    // wrong for the host's other, still-active listings.
    if (!isActiveStatus(listing?.status)) return;
    // Plain FieldValue.increment(-1) would let this go negative for any
    // listing that existed before this tracker was deployed (never counted by
    // onWorkspaceListingCreated in the first place). Clamp at 0 in a
    // transaction instead of a raw increment.
    const db = getFirestore();
    const profileRef = db.collection("user_profiles").doc(ownerId);
    await db.runTransaction(async (tx) => {
      const snap = await tx.get(profileRef);
      // A deleted account's listings are removed after its profile: never recreate it.
      if (!snap.exists) return;
      const current = (snap.data()?.activeListingCount as number | undefined) ?? 0;
      tx.update(profileRef, { activeListingCount: Math.max(0, current - 1) });
    });
  }
);

/**
 * Tracks status transitions (Draft/Paused <-> Active) so activeListingCount
 * stays in sync when a host publishes a draft or pauses/resumes a listing —
 * without this, a Draft never counted at create time would also never get
 * counted once published, and a Paused listing would stay counted as active
 * forever. A create landing directly as ACTIVE is
 * already handled by onWorkspaceListingCreated above, not here — on a create
 * event there is no "before" document, so this trigger simply doesn't fire.
 */
export const onWorkspaceListingStatusChanged = onDocumentUpdated(
  "workspace_listings/{spaceId}",
  async (event) => {
    const before = event.data?.before?.data();
    const after = event.data?.after?.data();
    const ownerId = after?.ownerId ?? before?.ownerId;
    if (!ownerId) return;

    const wasActive = isActiveStatus(before?.status);
    const isActive = isActiveStatus(after?.status);

    if (wasActive === isActive) return;

    const db = getFirestore();
    const profileRef = db.collection("user_profiles").doc(ownerId);
    if (isActive) {
      await profileRef.update({ activeListingCount: FieldValue.increment(1) }).catch(ignoreNotFound);
    } else {
      await db.runTransaction(async (tx) => {
        const snap = await tx.get(profileRef);
        // A deleted account's listings are removed after its profile: never recreate it.
        if (!snap.exists) return;
        const current = (snap.data()?.activeListingCount as number | undefined) ?? 0;
        tx.update(profileRef, { activeListingCount: Math.max(0, current - 1) });
      });
    }
  }
);
