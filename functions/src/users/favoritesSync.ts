import { onDocumentUpdated } from "firebase-functions/v2/firestore";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";

/**
 * Denormalizes AppUser.savedSpaceIds (the specialist-facing heart-icon toggle,
 * ProHostRepository.toggleSavedSpace — a self-writable field, no rules change
 * needed there) into a real per-listing favoriteCount on workspace_listings.
 * Before this trigger, favoriting had zero aggregate/event data behind it —
 * OwnerAnalyticsScreen had nothing to show a host for "how many specialists
 * saved my listing." Mirrors the trigger-based-denormalization pattern already
 * used by listingCountTracker.ts and bookingConflictGuard.ts, not a new
 * architecture.
 */
export const onUserFavoritesChanged = onDocumentUpdated(
  "user_profiles/{uid}",
  async (event) => {
    const before = event.data?.before.data();
    const after = event.data?.after.data();
    if (!before || !after) return;

    const beforeIds = new Set<string>(before.savedSpaceIds ?? []);
    const afterIds = new Set<string>(after.savedSpaceIds ?? []);
    if (beforeIds.size === afterIds.size && [...beforeIds].every((id) => afterIds.has(id))) {
      return;
    }

    const added = [...afterIds].filter((id) => !beforeIds.has(id));
    const removed = [...beforeIds].filter((id) => !afterIds.has(id));
    if (added.length === 0 && removed.length === 0) return;

    const db = getFirestore();
    const batch = db.batch();
    for (const spaceId of added) {
      batch.set(
        db.collection("workspace_listings").doc(spaceId),
        { favoriteCount: FieldValue.increment(1) },
        { merge: true }
      );
    }
    for (const spaceId of removed) {
      batch.set(
        db.collection("workspace_listings").doc(spaceId),
        { favoriteCount: FieldValue.increment(-1) },
        { merge: true }
      );
    }

    try {
      await batch.commit();
    } catch (e) {
      logger.error("favorites_sync_batch_failed", {
        uid: event.params.uid,
        added,
        removed,
        error: e instanceof Error ? e.message : String(e),
      });
      return;
    }

    // FieldValue.increment(-1) on a listing that was deleted, or whose count was
    // already 0 from data predating this trigger, can drive it negative — clamp
    // it back up rather than leave a nonsensical count displayed to the host.
    for (const spaceId of removed) {
      const ref = db.collection("workspace_listings").doc(spaceId);
      const snap = await ref.get();
      const count = snap.data()?.favoriteCount;
      if (typeof count === "number" && count < 0) {
        await ref.set({ favoriteCount: 0 }, { merge: true });
      }
    }
  }
);
