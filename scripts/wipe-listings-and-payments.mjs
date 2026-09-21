/**
 * ProHost — Transactional data wipe script
 *
 * Deletes all workspace listings, booking requests, Whish transactions, and
 * unresolved Play Billing entries from Firestore, leaving user_profiles and
 * all configuration/system collections untouched.
 *
 * Usage:
 *   node scripts/wipe-listings-and-payments.mjs           # real delete
 *   node scripts/wipe-listings-and-payments.mjs --dry-run  # count only
 *
 * Requirements:
 *   GOOGLE_APPLICATION_CREDENTIALS env var must point to a service account
 *   JSON key with Firestore admin access, OR run inside a Firebase project
 *   environment where ADC (Application Default Credentials) is already set.
 *
 *   npm install firebase-admin   (if not already installed in project root)
 */

import { initializeApp, cert, getApps } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";

const DRY_RUN = process.argv.includes("--dry-run");

if (!getApps().length) {
  initializeApp();
}

const db = getFirestore();

const COLLECTIONS_TO_WIPE = [
  "workspace_listings",
  "booking_requests",
  "whish_transactions",
  "play_billing_unresolved",
];

const SAFE_COLLECTIONS = [
  "user_profiles",
  "package_plans",
  "system_metadata",
  "schema_architecture",
  "legal_documents",
  "audit_security_logs",
  "subscription_formulas",
  "id_review_queue",
  "hashtag_usage",
];

async function countCollection(name) {
  const snap = await db.collection(name).count().get();
  return snap.data().count;
}

async function deleteCollection(name) {
  let deleted = 0;
  const bw = db.bulkWriter();
  bw.onWriteError((error) => {
    console.error(`  BulkWriter error on ${name}:`, error);
    return false; // do not retry
  });

  // Stream in batches of 500 to avoid loading all docs into memory.
  let query = db.collection(name).limit(500);
  let snapshot = await query.get();

  while (!snapshot.empty) {
    snapshot.docs.forEach((doc) => bw.delete(doc.ref));
    await bw.flush();
    deleted += snapshot.size;
    process.stdout.write(`\r  ${name}: ${deleted} deleted…`);

    if (snapshot.size < 500) break;
    const last = snapshot.docs[snapshot.docs.length - 1];
    snapshot = await db.collection(name).limit(500).startAfter(last).get();
  }

  await bw.close();
  process.stdout.write(`\r  ${name}: ${deleted} deleted.   \n`);
  return deleted;
}

async function main() {
  console.log(`\nProHost data wipe — ${DRY_RUN ? "DRY RUN (no changes)" : "LIVE DELETE"}\n`);
  console.log("Collections to wipe:");

  const counts = {};
  for (const col of COLLECTIONS_TO_WIPE) {
    const n = await countCollection(col);
    counts[col] = n;
    console.log(`  ${col}: ${n} documents`);
  }

  console.log("\nCollections preserved (not touched):");
  for (const col of SAFE_COLLECTIONS) {
    const n = await countCollection(col);
    console.log(`  ${col}: ${n} documents`);
  }

  const total = Object.values(counts).reduce((a, b) => a + b, 0);
  if (total === 0) {
    console.log("\nNothing to delete. All target collections are already empty.");
    process.exit(0);
  }

  if (DRY_RUN) {
    console.log(`\n[DRY RUN] Would delete ${total} documents across ${COLLECTIONS_TO_WIPE.length} collections.`);
    console.log("Run without --dry-run to execute.");
    process.exit(0);
  }

  console.log(`\nDeleting ${total} documents…\n`);
  let totalDeleted = 0;
  for (const col of COLLECTIONS_TO_WIPE) {
    if (counts[col] > 0) {
      totalDeleted += await deleteCollection(col);
    } else {
      console.log(`  ${col}: already empty, skipped.`);
    }
  }

  console.log(`\nDone. ${totalDeleted} documents deleted.`);
  console.log("user_profiles and all system collections are untouched.\n");
}

main().catch((err) => {
  console.error("Fatal error:", err);
  process.exit(1);
});
