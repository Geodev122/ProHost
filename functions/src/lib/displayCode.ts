import { randomInt } from "crypto";
import { getFirestore } from "firebase-admin/firestore";
import "./admin";

/**
 * Short, human-friendly public IDs ("U-7K3Q9P") shown to users instead of
 * Firebase/Firestore document ids. Firestore doc ids stay the internal keys;
 * these codes are display-only and server-assigned.
 *
 * Crockford base32 (no I, L, O, U) keeps codes unambiguous when read aloud or
 * typed; 6 characters give ~1 billion codes per prefix.
 */
export type DisplayCodeKind = "U" | "L" | "D" | "B";

const ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
const CODE_LENGTH = 6;
const MAX_ATTEMPTS = 8;
export const DISPLAY_CODES_COLLECTION = "display_codes";

function randomCode(kind: DisplayCodeKind): string {
  let body = "";
  for (let i = 0; i < CODE_LENGTH; i++) body += ALPHABET[randomInt(ALPHABET.length)];
  return `${kind}-${body}`;
}

/**
 * Reserves a new unique code for [targetPath] (e.g. "workspace_listings/SPC-…" or
 * "workspace_listings/SPC-…#SUB-…" for a division). create() fails if the code
 * already exists, so reservation is atomic without a transaction.
 */
export async function mintDisplayCode(kind: DisplayCodeKind, targetPath: string): Promise<string> {
  const db = getFirestore();
  for (let attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
    const code = randomCode(kind);
    try {
      await db.collection(DISPLAY_CODES_COLLECTION).doc(code).create({
        kind,
        targetPath,
        createdAt: Date.now(),
      });
      return code;
    } catch (err) {
      if ((err as { code?: number }).code === 6 /* ALREADY_EXISTS */) continue;
      throw err;
    }
  }
  throw new Error(`mintDisplayCode: no free ${kind} code after ${MAX_ATTEMPTS} attempts`);
}

/** True when [code] is a real, registered code belonging to [targetPath]. */
export async function isCodeOwnedBy(code: unknown, targetPath: string): Promise<boolean> {
  if (typeof code !== "string" || !/^[ULDB]-[0-9A-Z]{6}$/.test(code)) return false;
  const snap = await getFirestore().collection(DISPLAY_CODES_COLLECTION).doc(code).get();
  return snap.exists && snap.data()?.targetPath === targetPath;
}
