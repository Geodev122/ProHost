/**
 * Saves are per room (owner, 23 Nov 2026): a `savedSpaceIds` entry is "<listingId>#<roomId>" for a room and "<listingId>"
 * for a whole place. Mirror of shared `SavedKeys.kt` (keep identical).
 */
export const SAVED_KEY_SEPARATOR = "#";

export function savedListingOf(key: string): string {
  const i = key.indexOf(SAVED_KEY_SEPARATOR);
  return i < 0 ? key : key.slice(0, i);
}

export function savedRoomOf(key: string): string | null {
  const i = key.indexOf(SAVED_KEY_SEPARATOR);
  const room = i < 0 ? "" : key.slice(i + 1);
  return room ? room : null;
}
