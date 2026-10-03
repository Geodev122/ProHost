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
    // update() (not set+merge): a saved id can point at a listing that was deleted, and
    // set+merge would recreate it as a ghost document holding only favoriteCount.
    // A missing listing is simply skipped.
    const bump = async (spaceId: string, by: number) => {
      try {
        await db.collection("workspace_listings").doc(spaceId).update({ favoriteCount: FieldValue.increment(by) });
      } catch (e) {
        if ((e as { code?: number | string }).code === 5 || (e as { code?: string }).code === "not-found") return;
        throw e;
      }
    };
    try {
      await Promise.all([
        ...added.map((id) => bump(id, 1)),
        ...removed.map((id) => bump(id, -1)),
      ]);
    } catch (e) {
      logger.error("favorites_sync_failed", {
        uid: event.params.uid,
        added,
        removed,
        error: e instanceof Error ? e.message : String(e),
      });
      return;
    }

    for (const spaceId of removed) {
      const ref = db.collection("workspace_listings").doc(spaceId);
      const snap = await ref.get();
      const count = snap.data()?.favoriteCount;
      if (snap.exists && typeof count === "number" && count < 0) {
        await ref.update({ favoriteCount: 0 });
      }
    }
  }
);
