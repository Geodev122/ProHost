import { onDocumentUpdated } from "firebase-functions/v2/firestore";
import { getFirestore } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";

/**
 * Denormalizes AppUser.savedSpaceIds (the heart on Explore, the listing page and Saved) into per-listing counters on
 * workspace_listings, the numbers a Pro Host sees in My Listings and Owner Analytics:
 * - favoriteCount: people who currently have the listing saved (never counts the owner's own save);
 * - favoritesByMonth / unsavesByMonth: saves and un-saves per month ("yyyymm", last MONTHS_KEPT months), for
 *   "+N this month" and trends.
 * Each change runs in a transaction that also records the event id on the listing (lastFavoriteEvents), so a
 * redelivered trigger (retry: true) never counts twice. A missing listing is skipped (never recreated as a ghost doc).
 * The pure parts are unit-tested in favoritesSync.test.ts; Maintenance `favrecount` rebuilds favoriteCount from scratch.
 */

type Data = Record<string, unknown>;
export const MONTHS_KEPT = 13;
export const SAVED_IDS_CAP = 300;
const EVENTS_KEPT = 50;

/** "202610" for an epoch ms (UTC). */
export function monthKey(ms: number): string {
  const d = new Date(ms);
  return `${d.getUTCFullYear()}${String(d.getUTCMonth() + 1).padStart(2, "0")}`;
}

const ids = (v: unknown): string[] =>
  (Array.isArray(v) ? v : []).filter((x): x is string => typeof x === "string" && x.length > 0).slice(0, SAVED_IDS_CAP);

/** Listings saved and un-saved between two profile versions (set difference; re-saving the same id changes nothing). */
export function favoriteDeltas(before: unknown, after: unknown): { added: string[]; removed: string[] } {
  const b = new Set(ids(before));
  const a = new Set(ids(after));
  return { added: [...a].filter((id) => !b.has(id)), removed: [...b].filter((id) => !a.has(id)) };
}

const monthMap = (v: unknown): Record<string, number> => {
  const out: Record<string, number> = {};
  if (v && typeof v === "object") {
    for (const [k, n] of Object.entries(v as Data)) if (/^\d{6}$/.test(k) && typeof n === "number" && n > 0) out[k] = n;
  }
  return out;
};
const bumpMonth = (m: Record<string, number>, key: string): Record<string, number> => {
  const next = { ...m, [key]: (m[key] ?? 0) + 1 };
  return Object.fromEntries(Object.keys(next).sort().slice(-MONTHS_KEPT).map((k) => [k, next[k]]));
};

/**
 * The listing fields to write for one save (by = 1) or un-save (by = -1) by `uid`, or null when nothing changes:
 * the listing is gone, the person is its owner, or this event was already applied.
 */
export function applyFavorite(listing: Data | undefined, uid: string, by: 1 | -1, now: number, eventKey: string): Data | null {
  if (!listing) return null;
  if (listing.ownerId === uid) return null;
  const seen = Array.isArray(listing.lastFavoriteEvents) ? (listing.lastFavoriteEvents as unknown[]) : [];
  if (seen.includes(eventKey)) return null;
  const count = typeof listing.favoriteCount === "number" ? listing.favoriteCount : 0;
  const month = monthKey(now);
  const out: Data = {
    favoriteCount: Math.max(0, count + by),
    lastFavoriteEvents: [...seen.filter((x): x is string => typeof x === "string"), eventKey].slice(-EVENTS_KEPT),
  };
  if (by > 0) out.favoritesByMonth = bumpMonth(monthMap(listing.favoritesByMonth), month);
  else out.unsavesByMonth = bumpMonth(monthMap(listing.unsavesByMonth), month);
  return out;
}

/** People who have the listing saved, not counting its owner (Maintenance `favrecount`). */
export function recountFavorites(savedBy: string[], ownerId: unknown): number {
  return new Set(savedBy.filter((uid) => uid !== ownerId)).size;
}

export const onUserFavoritesChanged = onDocumentUpdated(
  { document: "user_profiles/{uid}", retry: true },
  async (event) => {
    const before = event.data?.before.data();
    const after = event.data?.after.data();
    if (!before || !after) return;
    const { added, removed } = favoriteDeltas(before.savedSpaceIds, after.savedSpaceIds);
    if (added.length === 0 && removed.length === 0) return;

    const db = getFirestore();
    const uid = event.params.uid;
    const now = Date.parse(event.time) || Date.now();
    const apply = (spaceId: string, by: 1 | -1) =>
      db.runTransaction(async (tx) => {
        const ref = db.collection("workspace_listings").doc(spaceId);
        const snap = await tx.get(ref);
        const fields = applyFavorite(snap.exists ? snap.data() : undefined, uid, by, now, `${event.id}:${by}`);
        if (fields) tx.update(ref, fields);
      });
    try {
      for (const id of added) await apply(id, 1);
      for (const id of removed) await apply(id, -1);
    } catch (e) {
      logger.error("favorites_sync_failed", { uid, added: added.length, removed: removed.length, error: e instanceof Error ? e.message : String(e) });
      throw e; // retried; events already applied are skipped by lastFavoriteEvents
    }
  }
);
