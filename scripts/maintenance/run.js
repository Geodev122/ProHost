// One-off production maintenance, run only by the manual "Maintenance" workflow
// (.github/workflows/maintenance.yml) with the CI service account. Reuses the compiled Cloud
// Functions code (functions/lib) so every step is the same logic as the admin buttons.
//
//   node scripts/maintenance/run.js audit            read-only integrity + billing health report
//   node scripts/maintenance/run.js j8               production data hygiene (backup → cleanup)
//   node scripts/maintenance/run.js retiregrowth     retired plans (growth/enterprise): holders → permanent grant, rows closed
//   node scripts/maintenance/run.js occupancy        rebuild booking_occupancy from ACCEPTED bookings
//   node scripts/maintenance/run.js emailauth        read-only: why sign-in emails fail (Auth config, mail, limits)
//   node scripts/maintenance/run.js enableemaillink  turn on Firebase Auth "Email link (passwordless)" sign-in
//   node scripts/maintenance/run.js mailtest         queue one test email to the extension's reply-to (owner) address
//   node scripts/maintenance/run.js clearstalesecrets drop retired HOSTINGER_* secret bindings that block deploys
//   node scripts/maintenance/run.js authorphans      read-only: Auth users with no profile, grouped by provider, day and
//                                                    test-robot domain (no emails printed)
//   node scripts/maintenance/run.js prodcheck        production readiness (read-only apart from one RTDN self-test
//                                                    message): deployed functions + indexes, public pages, Play catalog
//                                                    as this account sees it, RTDN topic → function, then the audit
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

  // Base plan ids exactly as Google Play returned them (subscriptions/* is written from Play's API).
  const subs = await db.collection("subscriptions").get();
  const plans = {};
  subs.docs.forEach((d) => { const k = `${d.data().productId || "?"}/${d.data().basePlanId || "null"}:${d.data().status || "?"}`; plans[k] = (plans[k] || 0) + 1; });
  notice(`playSubscriptions=${subs.size} ${JSON.stringify(plans).replace(/[{}]/g, "")}`);

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

async function retiregrowth(db) {
  // Only package_pro_mrr exists now. Whoever still has Pro Host through a retired plan keeps it
  // as a permanent admin grant (owner's decision, Oct 2026); every open billing row for a
  // retired plan is closed and admin overrides on retired tokens are removed.
  const SUPPORTED = "package_pro_mrr";
  const { grantProHost } = require(`${LIB}/billing/entitlementManager`);
  const { ADMIN_FORCED_PLAN_ID, LIFETIME_EXPIRY_MILLIS } = require(`${LIB}/billing/playCatalog`);
  const { recordAuditLog } = require(`${LIB}/lib/auditLog`);

  const retiredSubs = (await db.collection("subscriptions").get()).docs.filter((d) => d.data().productId !== SUPPORTED);
  const holders = new Set(retiredSubs.map((d) => d.data().userId).filter(Boolean));
  const converted = [];
  for (const uid of holders) {
    const p = (await db.collection("user_profiles").doc(uid).get()).data();
    if (!p) continue;
    const code = p.displayCode || "?";
    if (p.role === "PRO_HOST" && p.entitlementSource !== "admin_forced") {
      await step(`grant ${code}`, async () => {
        await grantProHost(uid, {
          source: "admin_forced", planId: ADMIN_FORCED_PLAN_ID, expiryMillis: LIFETIME_EXPIRY_MILLIS,
          orderId: "admin:retire-growth",
        });
        const after = (await db.collection("user_profiles").doc(uid).get()).data() || {};
        return `role=${after.role} plan=${after.ownerPackageId} source=${after.entitlementSource}`;
      });
      converted.push(code);
    } else {
      notice(`${code}: role=${p.role} source=${p.entitlementSource || "-"} — left as is`);
    }
  }
  for (const d of retiredSubs) await d.ref.update({ retired: true }).catch(() => undefined);

  await step("closeRetiredRows", async () => {
    let pending = 0; let unlinked = 0;
    for (const d of (await db.collection("play_billing_pending").where("resolved", "==", false).get()).docs) {
      if (d.data().productId !== SUPPORTED) { await d.ref.update({ resolved: true, outcome: "retired_ignored", resolvedAt: Date.now() }); pending++; }
    }
    for (const d of (await db.collection("play_billing_unresolved").where("resolved", "==", false).get()).docs) {
      if (d.data().productId !== SUPPORTED) { await d.ref.update({ resolved: true, outcome: "retired_ignored", resolvedAt: Date.now() }); unlinked++; }
    }
    return `pending=${pending} unlinked=${unlinked}`;
  });
  await step("removeRetiredOverrides", async () => {
    let n = 0;
    for (const d of (await db.collection("play_purchase_links").get()).docs) {
      if (d.data().productId && d.data().productId !== SUPPORTED) { await d.ref.delete(); n++; }
    }
    return `deleted=${n}`;
  });
  await step("auditLog", () => recordAuditLog({
    actionType: "RETIRED_PLANS_REMOVED",
    details: `Retired Play plans removed (maintenance workflow). Permanent Pro Host grant for: ${converted.join(", ") || "nobody"}.`,
    actorEmail: "maintenance@pro-host.tech",
    severity: "SECURE",
  }).then(() => "recorded"));
}

async function occupancy(db) {
  // One-off backfill of the public occupancy projection (the trigger keeps it current after that).
  const { OCCUPANCY_COLLECTION, occupancyFields } = require(`${LIB}/bookings/occupancyProjection`);
  await step("occupancy", async () => {
    const accepted = await db.collection("booking_requests").where("status", "==", "ACCEPTED").get();
    const keep = new Set(accepted.docs.map((d) => d.id));
    const w = db.bulkWriter();
    accepted.docs.forEach((d) => { if (typeof d.data().spaceId === "string") w.set(db.collection(OCCUPANCY_COLLECTION).doc(d.id), occupancyFields(d.data())); });
    let removed = 0;
    for (const d of (await db.collection(OCCUPANCY_COLLECTION).get()).docs) {
      if (!keep.has(d.id)) { w.delete(d.ref); removed++; }
    }
    await w.close();
    return `written=${accepted.size} removedStale=${removed}`;
  });
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

async function clearstalesecrets() {
  // Old deploys bound HOSTINGER_SMTP_* secrets to some functions. The code no longer declares them, but the
  // binding stays on the Cloud Run service and every redeploy fails with "Permission denied on secret".
  // Remove only those bindings; the next CI deploy then ships the current code.
  const project = process.env.GCLOUD_PROJECT;
  const base = `https://cloudfunctions.googleapis.com/v2/projects/${project}/locations/europe-west1/functions`;
  const fns = [];
  let pageToken = "";
  do {
    const res = await googleApi(`${base}?pageSize=200${pageToken ? `&pageToken=${pageToken}` : ""}`);
    if (!res.ok) throw new Error(`list HTTP ${res.status} ${(await res.text()).slice(0, 200)}`);
    const j = await res.json();
    fns.push(...(j.functions || []));
    pageToken = j.nextPageToken || "";
  } while (pageToken);
  const isStale = (v) => /^HOSTINGER/i.test(v.secret || "");
  const stale = fns.filter((f) => (f.serviceConfig?.secretEnvironmentVariables || []).some(isStale));
  notice(`functions=${fns.length} withStaleSecret=${stale.length}: ${stale.map((f) => f.name.split("/").pop()).join(" ")}`);
  for (const f of stale) {
    await step(`clear ${f.name.split("/").pop()}`, async () => {
      const keep = f.serviceConfig.secretEnvironmentVariables.filter((v) => !isStale(v));
      const res = await googleApi(`https://cloudfunctions.googleapis.com/v2/${f.name}?updateMask=serviceConfig.secretEnvironmentVariables`, {
        method: "PATCH",
        body: JSON.stringify({ serviceConfig: { secretEnvironmentVariables: keep } }),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status} ${(await res.text()).slice(0, 200)}`);
      let op = await res.json();
      for (let i = 0; i < 60 && !op.done; i++) {
        await new Promise((r) => setTimeout(r, 5000));
        op = await (await googleApi(`https://cloudfunctions.googleapis.com/v2/${op.name}`)).json();
      }
      if (!op.done) return "still updating after 5 min";
      return op.error ? `error ${JSON.stringify(op.error).slice(0, 200)}` : `removed (kept ${keep.length} other secrets)`;
    });
  }
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

async function authorphans(db, auth) {
  // Who signed in but never got a profile (assignInitialRole creates it on first sign-in)?
  // Play's pre-launch robots and reviewers sign in with @cloudtestlabaccounts.com accounts.
  const ids = new Set((await db.collection("user_profiles").select().get()).docs.map((d) => d.id));
  const byProvider = {}; const byDay = {}; const byKind = {}; let total = 0; let signedInAgain = 0;
  let page;
  do {
    const r = await auth.listUsers(1000, page);
    for (const u of r.users) {
      if (ids.has(u.uid)) continue;
      total++;
      const prov = (u.providerData || []).map((p) => p.providerId).sort().join("+") || "none";
      byProvider[prov] = (byProvider[prov] || 0) + 1;
      const day = new Date(u.metadata.creationTime).toISOString().slice(0, 10);
      byDay[day] = (byDay[day] || 0) + 1;
      const domain = (u.email || "").split("@")[1] || "";
      const kind = /cloudtestlabaccounts\.com$/i.test(domain) ? "playTestRobot" :
        u.email ? "email" : u.phoneNumber ? "phoneOnly" : "anonymous";
      byKind[kind] = (byKind[kind] || 0) + 1;
      if (u.metadata.lastSignInTime && u.metadata.lastSignInTime !== u.metadata.creationTime) signedInAgain++;
    }
    page = r.pageToken;
  } while (page);
  const j = (o) => JSON.stringify(o).replace(/[{}"]/g, "");
  notice(`authWithoutProfile=${total} signedInAgainLater=${signedInAgain} kinds=${j(byKind)}`);
  notice(`byProvider=${j(byProvider)}`);
  notice(`createdByDay=${j(byDay)}`);
  // The audit log shows whether assignInitialRole ever ran for them (INITIAL_ROLE_ASSIGNED).
  const since = Date.now() - 14 * 24 * 3600 * 1000;
  const logs = await db.collection("audit_security_logs").where("timestamp", ">=", since).get();
  const types = {};
  logs.docs.forEach((d) => { const t = d.data().actionType; types[t] = (types[t] || 0) + 1; });
  notice(`auditLog14d=${j(types)}`);
}

async function prodcheck(db, auth) {
  const project = process.env.GCLOUD_PROJECT;
  // 1. Every function the code exports is deployed, ACTIVE, and on the expected runtime.
  await step("deployedFunctions", async () => {
    const exported = Object.keys(require(`${LIB}/index`));
    const deployed = {};
    let pageToken = "";
    do {
      const res = await googleApi(`https://cloudfunctions.googleapis.com/v2/projects/${project}/locations/-/functions?pageSize=200${pageToken ? `&pageToken=${pageToken}` : ""}`);
      if (!res.ok) throw new Error(`HTTP ${res.status} ${(await res.text()).slice(0, 200)}`);
      const j = await res.json();
      (j.functions || []).forEach((f) => { deployed[f.name.split("/").pop()] = f; });
      pageToken = j.nextPageToken || "";
    } while (pageToken);
    const missing = exported.filter((n) => !deployed[n]);
    const extra = Object.keys(deployed).filter((n) => !exported.includes(n));
    const notActive = Object.entries(deployed).filter(([, f]) => f.state !== "ACTIVE").map(([n, f]) => `${n}:${f.state}`);
    const runtimes = {};
    Object.values(deployed).forEach((f) => { const r = f.buildConfig?.runtime || "?"; runtimes[r] = (runtimes[r] || 0) + 1; });
    return `exported=${exported.length} deployed=${Object.keys(deployed).length} missing=[${missing.join(",")}] ` +
      `notInCode=[${extra.join(",")}] notActive=[${notActive.join(",")}] runtimes=${JSON.stringify(runtimes).replace(/[{}"]/g, "")}`;
  });
  // 2. Firestore composite indexes are built.
  await step("firestoreIndexes", async () => {
    const res = await googleApi(`https://firestore.googleapis.com/v1/projects/${project}/databases/(default)/collectionGroups/-/indexes`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const idx = (await res.json()).indexes || [];
    const states = {};
    idx.forEach((i) => { states[i.state] = (states[i.state] || 0) + 1; });
    return `indexes=${idx.length} ${JSON.stringify(states).replace(/[{}"]/g, "")}`;
  });
  // 2b. Cloud Billing: Blaze is required for Cloud Functions, Secret Manager and outbound calls.
  await step("cloudBilling", async () => {
    const res = await googleApi(`https://cloudbilling.googleapis.com/v1/projects/${project}/billingInfo`);
    if (!res.ok) return `unknown (HTTP ${res.status} — check console.cloud.google.com/billing)`;
    const b = await res.json();
    let account = "";
    if (b.billingAccountName) {
      const a = await googleApi(`https://cloudbilling.googleapis.com/v1/${b.billingAccountName}`);
      account = a.ok ? ` accountOpen=${(await a.json()).open}` : ` account=…${b.billingAccountName.slice(-4)} (status not readable: HTTP ${a.status})`;
    }
    return `billingEnabled=${b.billingEnabled === true}${account}${b.billingEnabled ? "" : " — FUNCTIONS AT RISK: re-link a billing account"}`;
  });
  // 2d. Cloud Run state behind two functions, and their recent errors.
  await step("cloudRunState", async () => {
    const out = [];
    for (const svc of ["legaldocumentpage", "changeslotprice", "playbillingrtdn"]) {
      const r = await googleApi(`https://run.googleapis.com/v2/projects/${project}/locations/europe-west1/services/${svc}`);
      if (!r.ok) { out.push(`${svc}=HTTP${r.status}`); continue; }
      const j = await r.json();
      const c = j.terminalCondition || {};
      out.push(`${svc}=${c.state || "?"}${c.reason ? `/${c.reason}` : ""}${c.message ? `:${String(c.message).slice(0, 80)}` : ""}`);
    }
    return out.join(" | ");
  });
  await step("recentFunctionErrors", async () => {
    const since = new Date(Date.now() - 6 * 3600 * 1000).toISOString();
    const r = await googleApi("https://logging.googleapis.com/v2/entries:list", {
      method: "POST",
      body: JSON.stringify({
        resourceNames: [`projects/${project}`], orderBy: "timestamp desc", pageSize: 50,
        filter: `severity>=ERROR AND (resource.type="cloud_run_revision" OR resource.type="cloud_function") AND timestamp>="${since}"`,
      }),
    });
    if (!r.ok) return `logs not readable (HTTP ${r.status})`;
    const groups = {};
    for (const e of (await r.json()).entries || []) {
      const m = String(e.textPayload || e.jsonPayload?.message || e.protoPayload?.status?.message || "")
        .replace(/[\w.+-]+@[\w-]+\.[\w.]+/g, "<email>").replace(/\s+/g, " ").slice(0, 90);
      const k = `${e.resource?.labels?.service_name || "?"}: ${m}`;
      groups[k] = (groups[k] || 0) + 1;
    }
    return Object.entries(groups).slice(0, 6).map(([k, v]) => `${v}x ${k}`).join(" || ") || "none in 6h";
  });
  // 2c. Deployed functions answer: an HTTP page and an unauthenticated callable (expects UNAUTHENTICATED).
  await step("functionsServing", async () => {
    const base = `https://europe-west1-${project}.cloudfunctions.net`;
    const page = await fetch(`${base}/legalDocumentPage?doc=privacy`);
    const call = await fetch(`${base}/changeSlotPrice`, { method: "POST", headers: { "Content-Type": "application/json" }, body: "{\"data\":{}}" });
    const callBody = (await call.text()).slice(0, 80).replace(/\s+/g, " ");
    return `legalDocumentPage=${page.status} changeSlotPrice(no auth)=${call.status} ${callBody}`;
  });
  // 3. Public pages that Play and the app link to.
  await step("publicPages", async () => {
    const out = [];
    for (const page of ["privacy.html", "terms.html", "delete-account.html", "redeem.html", "emaillink.html", ".well-known/assetlinks.json"]) {
      const r = await fetch(`https://pro-host.tech/${page}`);
      out.push(`${page}=${r.status}`);
    }
    return out.join(" ");
  });
  // 4. The Play catalog as this (CI) account sees it. The deployed functions use their own service
  //    account; a 401/403 here only means the CI account isn't a Play Console user.
  await step("playCatalog", async () => {
    const pkg = "app.geonajjar.prohost";
    const res = await googleApi(`https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${pkg}/subscriptions/package_pro_mrr`);
    if (res.status === 401 || res.status === 403) return `CI account has no Play access (HTTP ${res.status}) — use Admin › Packages › Billing health`;
    if (!res.ok) throw new Error(`HTTP ${res.status} ${(await res.text()).slice(0, 160)}`);
    const j = await res.json();
    return (j.basePlans || []).map((bp) => `${bp.basePlanId}:${bp.state}:${bp.autoRenewingBasePlanType?.billingPeriodDuration || "?"}`).join(", ");
  });
  // 5. RTDN topic → playBillingRtdn: the same self-test message the admin button publishes.
  await step("rtdnSelfTest", async () => {
    const before = (await db.doc("app_config/billing_health").get()).data()?.lastSelfTestAt || 0;
    const message = {
      version: "1.0", packageName: "app.geonajjar.prohost", eventTimeMillis: String(Date.now()),
      selfTest: true, testNotification: { version: "1.0" },
    };
    const res = await googleApi(`https://pubsub.googleapis.com/v1/projects/${project}/topics/play-billing-rtdn:publish`, {
      method: "POST",
      body: JSON.stringify({ messages: [{ data: Buffer.from(JSON.stringify(message)).toString("base64") }] }),
    });
    if (!res.ok) throw new Error(`publish HTTP ${res.status} ${(await res.text()).slice(0, 160)}`);
    for (let i = 0; i < 24; i++) {
      await new Promise((r) => setTimeout(r, 5000));
      const now = (await db.doc("app_config/billing_health").get()).data()?.lastSelfTestAt || 0;
      if (now > before) return `delivered to playBillingRtdn in ~${(i + 1) * 5}s`;
    }
    throw new Error("published, but playBillingRtdn didn't record it within 2 min");
  });
  await audit(db, auth);
}

(async () => {
  require(`${LIB}/lib/admin`);
  const db = admin.firestore();
  const auth = admin.auth();
  const [task, arg] = process.argv.slice(2);
  if (task === "audit") await audit(db, auth);
  else if (task === "j8") { await j8(db, auth); await audit(db, auth); }
  else if (task === "occupancy") await occupancy(db);
  else if (task === "retiregrowth") { await retiregrowth(db); await audit(db, auth); }
  else if (task === "emailauth") await emailauth(db);
  else if (task === "enableemaillink") { await enableemaillink(); await emailauth(db); }
  else if (task === "mailtest") { await mailtest(db); await emailauth(db); }
  else if (task === "clearstalesecrets") await clearstalesecrets();
  else if (task === "prodcheck") await prodcheck(db, auth);
  else if (task === "authorphans") await authorphans(db, auth);
  else throw new Error(`unknown task ${task}`);
})().catch((e) => { fail(`${e && e.message}`); process.exit(1); });
