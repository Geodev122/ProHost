/**
 * ProHost — GA4 Measurement Protocol API Secret Seeder
 *
 * Saves the GA4 Measurement Protocol API Secret in Firestore at app_config/ga4.
 *
 * Usage (the secret is never stored in this repo):
 *   GA4_API_SECRET=<secret> node scripts/seed-ga4-config.mjs
 */

import { initializeApp, getApps } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";

if (!getApps().length) {
  initializeApp({
    projectId: "prohost-f766f",
  });
}

const db = getFirestore();

async function main() {
  const apiSecret = (process.env.GA4_API_SECRET ?? "").trim();
  if (!apiSecret) {
    console.error("Set GA4_API_SECRET (GA4 Admin › Data streams › Measurement Protocol API secrets).");
    process.exit(2);
  }
  const firebaseAppId = "1:646730915838:android:345a7e12d5994456c8eaaf";

  console.log("Seeding GA4 configuration into app_config/ga4…");

  await db.collection("app_config").doc("ga4").set(
    {
      apiSecret,
      firebaseAppId,
      debug: false,
      updatedAt: Date.now(),
      seededBy: "script",
    },
    { merge: true }
  );

  console.log("✓ Successfully saved GA4 API secret in app_config/ga4!");
}

main().catch((err) => {
  console.error("Error seeding GA4 config:", err);
  process.exit(1);
});
