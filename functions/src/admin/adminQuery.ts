/**
 * Admin smart-search query classification (pure, unit-tested). The admin types a name,
 * an email, a display code (U-/L-/D-/B- + 6 Crockford chars) or a Firebase UID / doc id;
 * adminDirectory.ts turns the classification into the matching Firestore lookups.
 */
export type CodePrefix = "U" | "L" | "D" | "B";

export type AdminQuery =
  | { type: "empty" }
  | { type: "code"; prefix: CodePrefix; code: string }
  | { type: "email"; email: string }
  // A free-text query: a name/title prefix, and — when it is one token that could be an
  // id — also tried as a UID / document id.
  | { type: "text"; prefix: string; maybeId: string | null };

const CODE = /^([ULDB])-?([0-9A-Z]{6})$/;

/** Lower-cased, single-spaced form stored as `searchName` and compared against. */
export function normalizeSearchName(value: unknown): string {
  return typeof value === "string" ? value.trim().toLowerCase().replace(/\s+/g, " ") : "";
}

export function classifyAdminQuery(raw: unknown): AdminQuery {
  const q = typeof raw === "string" ? raw.trim() : "";
  if (!q) return { type: "empty" };
  const compact = q.toUpperCase().replace(/\s+/g, "");
  const code = CODE.exec(compact);
  if (code) return { type: "code", prefix: code[1] as CodePrefix, code: `${code[1]}-${code[2]}` };
  if (q.includes("@")) return { type: "email", email: q.toLowerCase() };
  const maybeId = /^[A-Za-z0-9_-]{6,128}$/.test(q) ? q : null;
  return { type: "text", prefix: normalizeSearchName(q), maybeId };
}
