import { HttpsError } from "firebase-functions/v2/https";
import { DocumentSnapshot, FieldPath, getFirestore } from "firebase-admin/firestore";
import { getAuth } from "firebase-admin/auth";
import { onCall } from "../lib/callable";
import { recordAuditLog } from "../lib/auditLog";
import { DISPLAY_CODES_COLLECTION } from "../lib/displayCode";
import "../lib/admin";

/**
 * Admin › Demo tab › Purge: deletes every demo document server-side — users, listings and
 * bookings flagged isDemo or with a "demo-" id (and demo users by their "demo." email) —
 * plus their display codes and the demo users' Auth accounts. The admin console holds no
 * lists, so this can't be done from the device any more.
 */
const COLLECTIONS = ["booking_requests", "workspace_listings", "user_profiles"] as const;

async function demoDocs(collection: string): Promise<DocumentSnapshot[]> {
  const ref = getFirestore().collection(collection);
  const queries = [
    ref.where("isDemo", "==", true).get(),
    ref.orderBy(FieldPath.documentId()).startAt("demo-").endBefore("demo.").get(),
    ref.orderBy(FieldPath.documentId()).startAt("DEMO-").endBefore("DEMO.").get(),
  ];
  if (collection === "user_profiles") {
    queries.push(ref.where("email", ">=", "demo.").where("email", "<", "demo/").get());
  }
  const byId = new Map<string, DocumentSnapshot>();
  for (const snap of await Promise.all(queries)) snap.docs.forEach((d) => byId.set(d.id, d));
  return [...byId.values()];
}

export const purgeDemoContent = onCall({ timeoutSeconds: 300 }, async (request) => {
  const auth = request.auth;
  if (!auth) throw new HttpsError("unauthenticated", "Sign in required.");
  if (auth.token.role !== "ADMIN") throw new HttpsError("permission-denied", "Admins only.");

  const db = getFirestore();
  const counts: Record<string, number> = {};
  for (const collection of COLLECTIONS) {
    const docs = await demoDocs(collection);
    counts[collection] = docs.length;
    for (let i = 0; i < docs.length; i += 200) {
      const batch = db.batch();
      for (const d of docs.slice(i, i + 200)) {
        batch.delete(d.ref);
        const data = d.data() ?? {};
        if (typeof data.displayCode === "string" && data.displayCode) {
          batch.delete(db.collection(DISPLAY_CODES_COLLECTION).doc(data.displayCode));
        }
        const subs = Array.isArray(data.subdivisions) ? data.subdivisions : [];
        for (const sub of subs) {
          const code = (sub as Record<string, unknown>)?.displayCode;
          if (typeof code === "string" && code) batch.delete(db.collection(DISPLAY_CODES_COLLECTION).doc(code));
        }
      }
      await batch.commit();
    }
    if (collection === "user_profiles") {
      for (const d of docs) {
        // Demo users are seeded profiles; delete an Auth account only if one exists.
        await getAuth().deleteUser(d.id).catch(() => undefined);
      }
    }
  }

  const total = Object.values(counts).reduce((a, b) => a + b, 0);
  await recordAuditLog({
    actionType: "DEMO_CONTENT_PURGED",
    details: `Purged ${counts.workspace_listings} demo listings, ${counts.booking_requests} bookings and ${counts.user_profiles} users (server-side).`,
    actorEmail: auth.token.email ?? "admin",
    severity: "SECURE",
  });
  return { total, ...counts };
});
