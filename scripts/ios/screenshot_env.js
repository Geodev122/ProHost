// For .github/workflows/ios-screenshots.yml: reads the App Review Specialist login (server-only app_config/app_review, written by
// Maintenance `reviewaccounts`) and one publicly browsable listing id, masks the password and writes DEMO_EMAIL / DEMO_PASSWORD /
// LISTING_ID into $GITHUB_ENV. Prints nothing secret. Needs GOOGLE_APPLICATION_CREDENTIALS and firebase-admin on NODE_PATH.
"use strict";
const fs = require("fs");
const admin = require("firebase-admin");

(async () => {
  admin.initializeApp({ projectId: "prohost-f766f" });
  const db = admin.firestore();
  const review = (await db.collection("app_config").doc("app_review").get()).data() || {};
  if (!review.specialistEmail || !review.specialistPassword) throw new Error("app_config/app_review is empty: run Maintenance reviewaccounts");
  const snap = await db.collection("workspace_listings").where("status", "==", "ACTIVE").limit(20).get();
  const pub = snap.docs.find((d) => {
    const x = d.data();
    return typeof x.ownerId === "string" && x.ownerId && x.isOwnerSuspended !== true && x.isOwnerPackageLapsed !== true && x.isDemo !== true;
  });
  console.log(`::add-mask::${review.specialistPassword}`);
  fs.appendFileSync(process.env.GITHUB_ENV,
    `DEMO_EMAIL=${review.specialistEmail}\nDEMO_PASSWORD=${review.specialistPassword}\nLISTING_ID=${pub ? pub.id : ""}\n`);
  console.log(`demo account ready; public listing ${pub ? "found" : "not found (listing screens skipped)"}`);
})().catch((e) => { console.log(`::error::${e.message}`); process.exit(1); });
