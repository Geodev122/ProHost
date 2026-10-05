// One-off production maintenance, run only by the manual "Maintenance" workflow
// (.github/workflows/maintenance.yml) with the CI service account. Reuses the compiled Cloud
// Functions code (functions/lib) so every step is the same logic as the admin buttons.
//
//   node scripts/maintenance/run.js audit            read-only integrity + billing health report
//   node scripts/maintenance/run.js j8               production data hygiene (backup → cleanup)
//   node scripts/maintenance/run.js activate <code>  honour a parked retired-plan purchase for U-XXXXXX
//   node scripts/maintenance/run.js emailauth        read-only: why sign-in emails fail (Auth config, mail, limits)
//   node scripts/maintenance/run.js enableemaillink  turn on Firebase Auth "Email link (passwordless)" sign-in
//   node scripts/maintenance/run.js mailtest         queue one test email to the extension's reply-to (owner) address
//
// Results are printed as GitHub "::notice::" annotations (no PII: counts and display codes only).
"use strict";
const path = require("path");
const LIB = path.join(__dirname, "../../functions/lib");
const admin = require(path.join(__dirname, "../../functions/node_modules/firebase-admin"));

const notice = (msg) => console.log(`::notice::${String(msg).replace(/\r?\n/g, " ")}`);
const fail = (msg) => console.log(`::error::${String(msg).replace(/\r?\n/g, " ")}`);
const ADMIN_REQ = (data = {}) => ({
  auth: { uid: "maintenance-workflow", token: { role: "ADMIN", email: "maintenance@pro-host.tech" } },
  data,
  rawRequest: { headers: {} },
});
const RETIRED_COLLECTIONS = ["package_plans", "subscription_formulas", "whish_transactions"];

async function step(name, fn) {
  try {
    const out = await fn();
    notice(`${name}: ${typeof out === "string" ? out : JSON.stringify(out)}`);
    return out;
  } catch (e) {
    fail(`${name} failed: ${e && (e.code || "")} ${e && e.message}`);
    return undefined;
  }
}

async function audit(db, auth) {
  const profiles = await db.collection("user_profiles").get();
  const ids = new Set(profiles.docs.map((d) => d.id));
  let orphanProfiles = 0;
  let profilesNoCode = 0;
  let legacyProfileFields = 0;
  for (const d of profiles.docs) {
    if (!d.data().displayCode) profilesNoCode++;
    if ("subscriptionExpiryMillis" in d.data()) legacyProfileFields++;
    try { await auth.getUser(d.id); } catch (e) { if (e.code === "auth/user-not-found") orphanProfiles++; else throw e; }
  }
  let authUsers = 0;
  let authNoProfile = 0;
  let page;
  do {
    const r = await auth.listUsers(1000, page);
    r.users.forEach((u) => { authUsers++; if (!ids.has(u.uid)) authNoProfile++; });
    page = r.pageToken;
  } while (page);
  notice(`profiles=${profiles.size} orphanProfiles=${orphanProfiles} profilesWithoutCode=${profilesNoCode} ` +
    `legacyProfileFields=${legacyProfileFields} authUsers=${authUsers} authUsersWithoutProfile=${authNoProfile}`);

  const listings = await db.collection("workspace_listings").get();
  let legacy = 0; let demo = 0; let noCode = 0; let ownerEmail = 0;
  listings.docs.forEach((d) => {
    const l = d.data();
    if ("rentalFormulas" in l || "subscriptionExpiryMillis" in l) legacy++;
    if ("ownerEmail" in l) ownerEmail++;
    if (l.isDemo || /^demo-/i.test(d.id)) demo++;
    if (!l.displayCode) noCode++;
  });
  notice(`listings=${listings.size} legacyFields=${legacy} ownerEmail=${ownerEmail} demo=${demo} withoutCode=${noCode}`);

  const bookings = await db.collection("booking_requests").get();
  const noCodeB = bookings.docs.filter((d) => !d.data().displayCode).length;
  notice(`bookings=${bookings.size} withoutCode=${noCodeB}`);

  const retiredCounts = [];
  for (const c of RETIRED_COLLECTIONS) retiredCounts.push(`${c}=${(await db.collection(c).count().get()).data().count}`);
  notice(`retired: ${retiredCounts.join(" ")}`);

  // Email delivery through the Trigger Email extension (it writes delivery.state on each mail doc).
  const since = Date.now() - 24 * 60 * 60 * 1000;
  const mail = await db.collection("mail").where("createdAt", ">=", admin.firestore.Timestamp.fromMillis(since)).get();
  const states = {};
  let lastError = "";
  mail.docs.forEach((d) => {
    const st = d.data().delivery?.state ?? "NOT_PICKED_UP";
    states[st] = (states[st] || 0) + 1;
    if (st === "ERROR" && d.data().delivery?.error) lastError = String(d.data().delivery.error).slice(0, 160);
  });
  notice(`mail last 24h: ${JSON.stringify(states)}${lastError ? ` lastError=${lastError}` : ""}`);

  const pros = profiles.docs.filter((d) => d.data().role === "PRO_HOST")
    .map((d) => `${d.data().displayCode || "?"}:${d.data().ownerPackageId || "-"}/${d.data().entitlementSource || "-"}`);
  notice(`proHosts=[${pros.join(", ")}]`);

  const hb = (await db.doc("app_config/billing_health").get()).data() || {};
  const iso = (ms) => (ms ? new Date(ms).toISOString() : "never");
  notice(`RTDN lastRtdnAt=${iso(hb.lastRtdnAt)} lastSelfTestAt=${iso(hb.lastSelfTestAt)}`);

  const pending = await db.collection("play_billing_pending").where("resolved", "==", false).get();
  const rows = [];
  for (const d of pending.docs) {
    const r = d.data();
    const code = r.uid ? (await db.collection("user_profiles").doc(r.uid).get()).data()?.displayCode : null;
    rows.push(`${code || "?"}:${r.productId}:needsAdmin=${!!r.needsAdmin}`);
  }
  notice(`parkedPurchases=[${rows.join(", ")}]`);
}

async function j8(db, auth) {
  const bucket = admin.storage().bucket();
  const stamp = new Date().toISOString().slice(0, 10);
  // 0. Backup first: everything this run touches, plus the retired collections.
  await step("backup", async () => {
    const dump = {};
    for (const c of ["user_profiles", "workspace_listings", "booking_requests", "display_codes", ...RETIRED_COLLECTIONS]) {
      const s = await db.collection(c).get();
      dump[c] = Object.fromEntries(s.docs.map((d) => [d.id, d.data()]));
    }
    await bucket.file(`archive/${stamp}-pre-cleanup-backup.json`).save(JSON.stringify(dump), { contentType: "application/json" });
    const retired = Object.fromEntries(RETIRED_COLLECTIONS.map((c) => [c, dump[c]]));
    await bucket.file(`archive/${stamp}-retired-collections.json`).save(JSON.stringify(retired), { contentType: "application/json" });
    return `archive/${stamp}-*.json written`;
  });

  const { migrateLegacyListingFields } = require(`${LIB}/admin/migrateLegacyFields`);
  await step("migrateLegacyListingFields", () => migrateLegacyListingFields.run(ADMIN_REQ()));

  const { purgeDemoContent } = require(`${LIB}/admin/purgeDemoContent`);
  await step("purgeDemoContent", () => purgeDemoContent.run(ADMIN_REQ()));

  const { cleanUpAccountData } = require(`${LIB}/lib/accountCleanup`);
  await step("orphanProfiles", async () => {
    let cleaned = 0;
    for (const d of (await db.collection("user_profiles").get()).docs) {
      try {
        await auth.getUser(d.id);
      } catch (e) {
        if (e.code !== "auth/user-not-found") throw e;
        await cleanUpAccountData(d.id, d.data().email || "");
        cleaned++;
      }
    }
    return `cleaned=${cleaned}`;
  });

  const { runBillingSyncOnce } = require(`${LIB}/billing/billingSyncJob`);
  await step("billingSync (legacy entitlement migration)", () => runBillingSyncOnce());

  const { backfillDisplayCodes } = require(`${LIB}/ids/displayCodes`);
  await step("backfillDisplayCodes", () => backfillDisplayCodes.run(ADMIN_REQ()));

  await step("retiredCollections", async () => {
    const out = {};
    for (const c of RETIRED_COLLECTIONS) {
      const s = await db.collection(c).get();
      const w = db.bulkWriter();
      s.docs.forEach((d) => { w.delete(d.ref).catch(() => undefined); });
      await w.close();
      out[c] = s.size;
    }
    return out;
  });

  const { recordAuditLog } = require(`${LIB}/lib/auditLog`);
  await step("auditLog", () => recordAuditLog({
    actionType: "DATA_HYGIENE",
    details: "Production cleanup (maintenance workflow): legacy fields migrated, demo purged, orphan profiles removed, " +
      "legacy entitlements migrated, display codes backfilled, retired collections archived to Storage and deleted.",
    actorEmail: "maintenance@pro-host.tech",
    severity: "SECURE",
  }).then(() => "recorded"));
}

async function activate(db, code) {
  if (!/^U-[0-9A-Z]{6}$/.test(code || "")) throw new Error("activate needs a U- display code");
  const users = await db.collection("user_profiles").where("displayCode", "==", code).limit(2).get();
  if (users.size !== 1) throw new Error(`${code} matched ${users.size} accounts`);
  const uid = users.docs[0].id;
  const rows = await db.collection("play_billing_pending").where("uid", "==", uid).where("resolved", "==", false).get();
  if (rows.empty) { notice(`${code}: no unresolved parked purchase (already handled)`); return; }
  const { adminActivatePurchase } = require(`${LIB}/billing/billingRescue`);
  for (const row of rows.docs) {
    await step(`activate ${code} ${row.data().productId}`,
      () => adminActivatePurchase.run(ADMIN_REQ({ kind: "pending", id: row.id, targetUid: uid })));
  }
  const after = (await db.collection("user_profiles").doc(uid).get()).data() || {};
  notice(`${code}: role=${after.role} plan=${after.ownerPackageId || "-"} source=${after.entitlementSource || "-"}`);
}

async function googleApi(url, init = {}) {
  const { access_token: token } = await admin.app().options.credential.getAccessToken();
  const project = process.env.GCLOUD_PROJECT;
  return fetch(url, {
    ...init,
    headers: { Authorization: `Bearer ${token}`, "x-goog-user-project": project, "Content-Type": "application/json" },
  });
}

async function enableemaillink() {
  // Email/Password provider with "Email link (passwordless sign-in)" on: enabled + passwordRequired=false.
  // Password sign-in is unaffected for existing accounts; the app only uses links and codes.
  await step("enableEmailLink", async () => {
    const project = process.env.GCLOUD_PROJECT;
    const res = await googleApi(
      `https://identitytoolkit.googleapis.com/admin/v2/projects/${project}/config?updateMask=signIn.email.enabled,signIn.email.passwordRequired`,
      { method: "PATCH", body: JSON.stringify({ signIn: { email: { enabled: true, passwordRequired: false } } }) }
    );
    if (!res.ok) throw new Error(`HTTP ${res.status} ${(await res.text()).slice(0, 200)}`);
    const c = await res.json();
    return `raw signIn.email=${JSON.stringify(c.signIn?.email || {}).replace(/[{}]/g, "")}`;
  });
}

async function mailtest(db) {
  // One real email through the Trigger Email extension, to the owner's own reply-to address.
  await step("mailtest", async () => {
    const project = process.env.GCLOUD_PROJECT;
    const res = await googleApi(`https://firebaseextensions.googleapis.com/v1beta/projects/${project}/instances`);
    const inst = res.ok ? ((await res.json()).instances || []).find((i) => /firestore-send-email/.test(i.config?.extensionRef || "")) : null;
    const to = inst?.config?.params?.DEFAULT_REPLY_TO;
    if (!to) throw new Error("no DEFAULT_REPLY_TO on the Trigger Email extension");
    const ref = await db.collection("mail").add({
      to,
      message: {
        subject: "ProHost email delivery test",
        text: "This is an automatic delivery test from the ProHost maintenance workflow. No action needed.",
        html: "<p>This is an automatic delivery test from the ProHost maintenance workflow. No action needed.</p>",
      },
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    });
    for (let i = 0; i < 18; i++) {
      await new Promise((r) => setTimeout(r, 5000));
      const d = (await ref.get()).data()?.delivery;
      if (d && ["SUCCESS", "ERROR"].includes(d.state)) {
        return `state=${d.state}${d.error ? ` error=${String(d.error).slice(0, 220)}` : ""} attempts=${d.attempts ?? "?"}`;
      }
    }
    const d = (await ref.get()).data()?.delivery;
    return `state=${d?.state || "NOT_PICKED_UP"} after 90 s (extension never processed the document)`;
  });
}

async function emailauth(db) {
  // 1. Firebase Auth (Identity Toolkit) config: is email / email-link sign-in enabled?
  await step("authConfig", async () => {
    const project = process.env.GCLOUD_PROJECT;
    const res = await googleApi(`https://identitytoolkit.googleapis.com/admin/v2/projects/${project}/config`);
    if (!res.ok) return `HTTP ${res.status} ${(await res.text()).slice(0, 200)}`;
    const c = await res.json();
    const email = c.signIn?.email || {};
    // Proto JSON omits false fields: a missing passwordRequired means false (email link allowed).
    return `raw signIn.email=${JSON.stringify(email).replace(/[{}]/g, "")} emailLinkAllowed=${!!email.enabled && email.passwordRequired !== true} ` +
      `(email link needs enabled && passwordRequired=false) authorizedDomains=[${(c.authorizedDomains || []).join(" ")}] ` +
      `emailPrivacy.enumerationProtection=${!!c.emailPrivacyConfig?.enableImprovedEmailPrivacy}`;
  });

  // 2. Mail queue (Trigger Email extension) over 72 h, newest error.
  await step("mailQueue72h", async () => {
    const since = admin.firestore.Timestamp.fromMillis(Date.now() - 72 * 3600 * 1000);
    const mail = await db.collection("mail").where("createdAt", ">=", since).get();
    const states = {};
    let lastError = "";
    let newest = 0;
    mail.docs.forEach((d) => {
      const st = d.data().delivery?.state ?? "NOT_PICKED_UP";
      states[st] = (states[st] || 0) + 1;
      const t = d.data().createdAt?.toMillis?.() ?? 0;
      if (st === "ERROR" && t >= newest) { newest = t; lastError = String(d.data().delivery?.error || "").slice(0, 200); }
    });
    const total = (await db.collection("mail").count().get()).data().count;
    return `allTime=${total} last72h=${mail.size} ${JSON.stringify(states).replace(/[{}]/g, "")}` +
      (lastError ? ` lastError=${lastError}` : "");
  });

  // 3. Is the extension installed at all? It stamps delivery.* on docs it has seen.
  await step("extensionSeenAnyMail", async () => {
    const s = await db.collection("mail").orderBy("createdAt", "desc").limit(20).get();
    const seen = s.docs.filter((d) => d.data().delivery).length;
    return `${seen}/${s.size} recent mail docs have delivery status`;
  });

  // 3b. Installed extensions (Trigger Email = firebase/firestore-send-email) and their state.
  await step("extensions", async () => {
    const project = process.env.GCLOUD_PROJECT;
    const res = await googleApi(`https://firebaseextensions.googleapis.com/v1beta/projects/${project}/instances`);
    if (!res.ok) return `HTTP ${res.status} (CI account can't list extensions)`;
    const list = (await res.json()).instances || [];
    return list.length === 0 ? "none installed" : list.map((i) =>
      `${i.name.split("/").pop()}=${i.config?.extensionRef || "?"}:${i.state}` +
      ` params=${Object.entries(i.config?.params || {})
        .filter(([k]) => !/PASSWORD|SECRET|URI|API_KEY/i.test(k))
        .map(([k, v]) => `${k}:${String(v).slice(0, 40)}`).join(",")}` +
      (i.errorStatus ? ` error=${JSON.stringify(i.errorStatus).slice(0, 200)}` : "")).join(" ");
  });

  // 3b2. Function logs for the email path over 7 days (needs Logs Viewer on the CI account).
  await step("functionLogs7d", async () => {
    const project = process.env.GCLOUD_PROJECT;
    const since = new Date(Date.now() - 7 * 24 * 3600 * 1000).toISOString();
    const events = ["email_queued", "email_queue_failed", "generate_sign_in_link_failed", "sign_in_link_sent",
      "sign_in_link_email_failed", "app_check_unverified"];
    const filter = `timestamp>="${since}" AND (${events.map((e) => `jsonPayload.message="${e}"`).join(" OR ")} ` +
      `OR (severity>=ERROR AND (resource.labels.service_name=("sendemailotp" OR "sendsigninemaillink") ` +
      `OR resource.labels.function_name=("sendEmailOtp" OR "sendSignInEmailLink"))))`;
    const res = await googleApi("https://logging.googleapis.com/v2/entries:list", {
      method: "POST",
      body: JSON.stringify({ resourceNames: [`projects/${project}`], filter, orderBy: "timestamp desc", pageSize: 200 }),
    });
    if (!res.ok) return `HTTP ${res.status} (CI account can't read logs)`;
    const entries = (await res.json()).entries || [];
    const counts = {};
    let lastErr = "";
    for (const e of entries) {
      const m = e.jsonPayload?.message || "error";
      counts[m] = (counts[m] || 0) + 1;
      if (!lastErr && (m.includes("failed") || m === "error")) {
        lastErr = `${e.timestamp} ${String(e.jsonPayload?.error || e.textPayload || JSON.stringify(e.jsonPayload || {}))
          .replace(/[\w.+-]+@[\w-]+\.[\w.]+/g, "<email>").slice(0, 220)}`;
      }
    }
    return `${JSON.stringify(counts).replace(/[{}]/g, "")}${lastErr ? ` last=${lastErr}` : ""}`;
  });

  // 3c. Did the email callables ever run? (They take a send slot before queueing mail.)
  await step("sendLimitDocsAllTime", async () => {
    const s = await db.collection("email_send_limits").get();
    const kinds = {};
    s.docs.forEach((d) => { const k = d.id.replace(/_[0-9a-f]{40}$/, ""); kinds[k] = (kinds[k] || 0) + (d.data().count || 0); });
    const otps = (await db.collection("email_otps").count().get()).data().count;
    return `docs=${s.size} sendsByKind=${JSON.stringify(kinds).replace(/[{}]/g, "")} emailOtpDocs=${otps}`;
  });

  // 4. Addresses currently at the send limit (counts only).
  await step("sendLimits", async () => {
    const s = await db.collection("email_send_limits").get();
    const hour = 3600 * 1000;
    const active = s.docs.filter((d) => Date.now() - (d.data().windowStart || 0) < hour);
    const blocked = active.filter((d) => (d.data().count || 0) >= 5);
    const kinds = {};
    blocked.forEach((d) => { const k = d.id.split("_")[0]; kinds[k] = (kinds[k] || 0) + 1; });
    return `activeWindows=${active.length} atLimit=${blocked.length} ${JSON.stringify(kinds).replace(/[{}]/g, "")}`;
  });
}

(async () => {
  require(`${LIB}/lib/admin`);
  const db = admin.firestore();
  const auth = admin.auth();
  const [task, arg] = process.argv.slice(2);
  if (task === "audit") await audit(db, auth);
  else if (task === "j8") { await j8(db, auth); await audit(db, auth); }
  else if (task === "activate") await activate(db, arg);
  else if (task === "emailauth") await emailauth(db);
  else if (task === "enableemaillink") { await enableemaillink(); await emailauth(db); }
  else if (task === "mailtest") { await mailtest(db); await emailauth(db); }
  else throw new Error(`unknown task ${task}`);
})().catch((e) => { fail(`${e && e.message}`); process.exit(1); });
