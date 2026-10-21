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
//   node scripts/maintenance/run.js mailingcode CODE set (or rotate) the pro-host.tech/admin access code; stores only a
//                                                    salted scrypt hash in app_config/mailing_admin and signs out all
//                                                    admin sessions. Without CODE: reports whether a code is set.
//   node scripts/maintenance/run.js mailingsmoke CODE live check of the website mailing stack: admin login with CODE,
//                                                    address book, preview, a test campaign to admin@pro-host.tech
//                                                    (waits for delivery), one contact-form request from
//                                                    admin@pro-host.tech (waits for the email), /admin and /unsubscribe
//   node scripts/maintenance/run.js igsetup [SHEET_ID] Instagram automation: stores IG_APP_SECRET / IG_ACCESS_TOKEN / IG_VERIFY_TOKEN
//                                                    (GitHub secrets) in the server-only app_config/instagram, checks the token
//                                                    belongs to the Instagram account, subscribes the webhook fields and
//                                                    optionally stores the control-room Sheet id. Prints masked results only.
//   node scripts/maintenance/run.js igsmoke          Instagram automation live check: webhook handshake, wrong token / bad
//                                                    signature rejected, signed empty event accepted, config + health summary
//   node scripts/maintenance/run.js igtest           Instagram pipeline test in dry-run: posts a signed synthetic comment ("link please"), a story reply, a text-less story reaction and a
//                                                    STOP DM from a fake person to /ig-webhook, waits for the engine's decision
//                                                    and prints what it would send. Refuses unless mode is dry-run. Sends nothing.
//   node scripts/maintenance/run.js aiprobe         which Claude model/region the project can call on Vertex AI (functions service account)
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
  // The same functions through Cloud Run's own URL (bypasses the cloudfunctions.net front door).
  await step("runAppServing", async () => {
    const out = [];
    for (const svc of ["legaldocumentpage", "changeslotprice", "assigninitialrole"]) {
      const r = await googleApi(`https://run.googleapis.com/v2/projects/${project}/locations/europe-west1/services/${svc}`);
      if (!r.ok) { out.push(`${svc}=api${r.status}`); continue; }
      const { uri } = await r.json();
      const isPage = svc === "legaldocumentpage";
      const res = await fetch(isPage ? `${uri}/privacy` : uri, isPage ? {} :
        { method: "POST", headers: { "Content-Type": "application/json" }, body: "{\"data\":{}}" });
      out.push(`${svc}=${res.status} ${(await res.text()).replace(/\s+/g, " ").slice(0, 70)}`);
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
    const page = await fetch(`${base}/legalDocumentPage/privacy`);
    const call = await fetch(`${base}/changeSlotPrice`, { method: "POST", headers: { "Content-Type": "application/json" }, body: "{\"data\":{}}" });
    const callBody = (await call.text()).slice(0, 80).replace(/\s+/g, " ");
    return `legalDocumentPage=${page.status} changeSlotPrice(no auth)=${call.status} ${callBody}`;
  });
  // 3. Public pages that Play and the app link to.
  await step("publicPages", async () => {
    const out = [];
    for (const page of ["privacy.html", "terms.html", "delete-account.html", "redeem.html", "emaillink.html", "get", "get-qr.svg", ".well-known/assetlinks.json"]) {
      const r = await fetch(`https://pro-host.tech/${page}`);
      out.push(`${page}=${r.status}`);
    }
    for (const alias of ["ig", "qr", "app"]) {  // marketing short links redirect to /get with campaign tags
      const r = await fetch(`https://pro-host.tech/${alias}`, { redirect: "manual" });
      out.push(`/${alias}=${r.status}→${r.headers.get("location") || "-"}`);
    }
    return out.join(" ");
  });
  // 3b. SEO: sitemap (static pages + live listings), every sitemap page answers 200 with a canonical, robots.txt.
  await step("seo", async () => {
    const xml = await (await fetch("https://pro-host.tech/sitemap.xml")).text();
    const locs = [...xml.matchAll(/<loc>([^<]+)<\/loc>/g)].map((m) => m[1]);
    const bad = [];
    for (const u of locs.filter((l) => !l.includes("/listing/")).concat(locs.filter((l) => l.includes("/listing/")).slice(0, 3))) {
      const r = await fetch(u);
      const html = r.ok ? await r.text() : "";
      if (!r.ok || !/rel="canonical"/.test(html) || /noindex/.test(html)) bad.push(`${u.replace("https://pro-host.tech", "")}=${r.status}`);
    }
    const robots = await (await fetch("https://pro-host.tech/robots.txt")).text();
    // Retired country-named slugs (-lebanon / -beirut) must still 301 to their country-neutral pages.
    const { LEGACY_SLUGS } = require(path.join(__dirname, "../website/seo-pages"));
    const lost = [];
    for (const [oldSlug, newSlug] of Object.entries(LEGACY_SLUGS)) {
      const r = await fetch(`https://pro-host.tech/${oldSlug}`, { redirect: "manual" });
      if (r.status !== 301 || !(r.headers.get("location") || "").endsWith(`/${newSlug}`)) lost.push(`${oldSlug}=${r.status}`);
    }
    return `sitemap urls=${locs.length} (listings=${locs.filter((l) => l.includes("/listing/")).length}) problems=[${bad.join(" ")}] legacyRedirects=${lost.length ? `BROKEN[${lost.join(" ")}]` : `${Object.keys(LEGACY_SLUGS).length} ok`} robotsSitemap=${/Sitemap: https:\/\/pro-host.tech\/sitemap.xml/.test(robots)}`;
  });
  // 4. The Play catalog as this (CI) account sees it (needs the androidpublisher scope and the account
  //    invited in Play Console › Users and permissions). Read-only: base plans and their offers.
  await step("playCatalog", async () => {
    const { GoogleAuth } = require(path.join(__dirname, "../../functions/node_modules/google-auth-library"));
    const client = await new GoogleAuth({ scopes: ["https://www.googleapis.com/auth/androidpublisher"] }).getClient();
    const base = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/app.geonajjar.prohost/subscriptions/package_pro_mrr";
    try {
      const sub = (await client.request({ url: base })).data;
      const plans = (sub.basePlans || []).map((bp) => {
        const lb = (bp.regionalConfigs || []).find((c) => c.regionCode === "LB");
        const price = lb?.price ? `${lb.price.units || 0}.${String(lb.price.nanos || 0).padStart(9, "0").slice(0, 2)} ${lb.price.currencyCode}` : "no LB price";
        return `${bp.basePlanId}:${bp.state}:${bp.autoRenewingBasePlanType?.billingPeriodDuration || "?"}:LB=${price}`;
      });
      let offers = "";
      try {
        const o = (await client.request({ url: `${base}/basePlans/-/offers` })).data;
        offers = (o.subscriptionOffers || []).map((x) => `${x.basePlanId}/${x.offerId}:${x.state}`).join(", ") || "none";
      } catch (e) { offers = `offers HTTP ${e.response?.status}`; }
      return `basePlans=[${plans.join(" | ")}] offers=[${offers}]`;
    } catch (e) {
      const st = e.response?.status;
      return `HTTP ${st} ${String(e.response?.data?.error?.message || e.message).slice(0, 160)}`;
    }
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

async function mailingcode(db, code) {
  const ref = db.collection("app_config").doc("mailing_admin");
  if (!code) {
    const snap = await ref.get();
    notice(`mailing admin code: ${snap.exists && snap.get("hash") ? `set (updated ${snap.get("updatedAt")?.toDate?.().toISOString() ?? "?"})` : "NOT set"}`);
    return;
  }
  if (!/^\d{4,12}$/.test(code.trim())) throw new Error("the code must be 4-12 digits");
  const { hashAdminCode } = require(`${LIB}/website/mailingAdmin`);
  const salt = require("crypto").randomBytes(16).toString("hex");
  await ref.set({ salt, hash: hashAdminCode(code, salt), updatedAt: admin.firestore.FieldValue.serverTimestamp() });
  const sessions = await db.collection("mailing_admin_sessions").get();
  const batch = db.batch();
  sessions.docs.forEach((d) => batch.delete(d.ref));
  await batch.commit();
  notice(`mailing admin code set (${code.trim().length} digits); ${sessions.size} old admin session(s) signed out`);
}

const maskId = (v) => (v ? `${String(v).slice(0, 4)}…${String(v).slice(-3)}` : "(none)");

async function igsetup(db, sheetId) {
  const secret = process.env.IG_APP_SECRET || "";
  const token = process.env.IG_ACCESS_TOKEN || "";
  const verify = process.env.IG_VERIFY_TOKEN || "";
  const missing = [["IG_APP_SECRET", secret], ["IG_ACCESS_TOKEN", token], ["IG_VERIFY_TOKEN", verify]].filter(([, v]) => !v).map(([k]) => k);
  if (missing.length) throw new Error(`missing GitHub secrets: ${missing.join(", ")} (Settings › Secrets and variables › Actions)`);
  const graph = require(`${LIB}/instagram/graph`);
  const me = await graph.whoAmI(token);
  if (!me.id) throw new Error("the access token did not return an Instagram account");
  notice(`token belongs to @${me.username} (id ${maskId(me.id)})`);

  let expiresAt = Date.now() + 60 * 86400000;
  let accessToken = token;
  try {
    const r = await graph.refreshToken(token);
    if (r.token) { accessToken = r.token; expiresAt = Date.now() + r.expiresInSec * 1000; notice(`token refreshed, valid ${Math.floor(r.expiresInSec / 86400)} days`); }
  } catch (e) {
    notice(`token not refreshed yet (${String(e.message).slice(0, 80)}); assuming 60 days from now`);
  }

  const ref = db.collection("app_config").doc("instagram");
  const current = (await ref.get()).data() || {};
  await ref.set({
    appSecret: secret, verifyToken: verify, accessToken, igUserId: me.id, username: me.username, tokenExpiresAt: expiresAt,
    mode: current.mode === "live" ? "live" : "dry-run", killSwitch: Boolean(current.killSwitch),
    ...(sheetId ? { sheetId: sheetId.trim() } : {}),
    ...(process.env.ANTHROPIC_API_KEY ? { anthropicKey: process.env.ANTHROPIC_API_KEY } : {}),
  }, { merge: true });
  if (process.env.ANTHROPIC_API_KEY) notice("Anthropic API key stored (only used when ai_provider = anthropic)");
  notice(`app_config/instagram saved (mode ${current.mode === "live" ? "live" : "dry-run"}${sheetId ? ", sheet linked" : current.sheetId ? ", sheet already linked" : ", NO sheet linked yet"})`);

  try {
    const ok = await graph.subscribeApp({ igUserId: me.id, token: accessToken });
    notice(`webhook subscription for @${me.username}: ${ok ? "active" : "not confirmed"}`);
  } catch (e) {
    fail(`webhook subscription failed: ${String(e.message).slice(0, 200)} (set the callback URL in the Meta app first)`);
  }
  const sid = sheetId || current.sheetId;
  if (sid) {
    try {
      const { google } = require(path.join(__dirname, "../../functions/node_modules/googleapis"));
      const sheets = google.sheets({ version: "v4", auth: new google.auth.GoogleAuth({ scopes: ["https://www.googleapis.com/auth/spreadsheets"] }) });
      await require(`${LIB}/instagram/sheetsBridge`).ensureTabs(sheets, sid);
      notice("sheet tabs OK (Rules, Log, Contacts, Status)");
    } catch (e) {
      notice(`sheet not reachable from this account yet (${String(e.message).slice(0, 100)}); the runtime service account must be an Editor of the sheet`);
    }
  }
}

async function igsmoke(db) {
  const BASE = "https://pro-host.tech/ig-webhook";
  const verify = process.env.IG_VERIFY_TOKEN || "";
  const secret = process.env.IG_APP_SECRET || "";
  if (!verify || !secret) throw new Error("IG_VERIFY_TOKEN and IG_APP_SECRET GitHub secrets are required");
  const check = (name, ok, extra) => (ok ? notice(`✓ ${name}${extra ? ` (${extra})` : ""}`) : fail(`✗ ${name}${extra ? ` (${extra})` : ""}`));
  const g1 = await fetch(`${BASE}?hub.mode=subscribe&hub.verify_token=${encodeURIComponent(verify)}&hub.challenge=smoke123`);
  check("handshake echoes the challenge", g1.status === 200 && (await g1.text()) === "smoke123", `HTTP ${g1.status}`);
  const g2 = await fetch(`${BASE}?hub.mode=subscribe&hub.verify_token=wrong&hub.challenge=x`);
  check("wrong verify token is refused", g2.status === 403, `HTTP ${g2.status}`);
  const body = JSON.stringify({ object: "instagram", entry: [] });
  const bad = await fetch(BASE, { method: "POST", headers: { "Content-Type": "application/json", "X-Hub-Signature-256": "sha256=00" }, body });
  check("bad signature is refused", bad.status === 403, `HTTP ${bad.status}`);
  const { sign } = require(`${LIB}/instagram/signature`);
  const good = await fetch(BASE, { method: "POST", headers: { "Content-Type": "application/json", "X-Hub-Signature-256": sign(body, secret) }, body });
  check("signed empty event is accepted", good.status === 200, `HTTP ${good.status}`);

  const c = (await db.collection("app_config").doc("instagram").get()).data() || {};
  const rules = await db.collection("ig_rules").get();
  const last = await db.collection("ig_events").orderBy("createdAt", "desc").limit(1).get();
  const left = c.tokenExpiresAt ? Math.floor((c.tokenExpiresAt - Date.now()) / 86400000) : null;
  const sync = (await db.collection("ig_stats").doc("sync").get()).data();
  notice(sync ? `sheet sync: ${sync.ok ? "ok" : "FAILING"} at ${new Date(sync.at).toISOString()} ${sync.ok ? `(${String(sync.note || "")})` : String(sync.error || "").slice(0, 300)}` : "sheet sync: never ran since this version deployed");
  notice(`account @${c.username || "?"} · mode ${c.mode || "dry-run"}${c.killSwitch ? " · KILL SWITCH ON" : ""} · rules ${rules.size} · token ${left === null ? "?" : left + " days"} left · sheet ${c.sheetId ? "linked" : "NOT linked"} · last event ${last.empty ? "none yet" : new Date(last.docs[0].get("createdAt")).toISOString()}`);
}

async function igtest(db) {
  const secret = process.env.IG_APP_SECRET || "";
  if (!secret) throw new Error("IG_APP_SECRET GitHub secret is required");
  const cfg = (await db.collection("app_config").doc("instagram").get()).data() || {};
  if (cfg.mode === "live" && !cfg.killSwitch) throw new Error("mode is LIVE: igtest only runs in dry-run (or with the kill switch on)");
  if (!cfg.igUserId) throw new Error("run igsetup first");
  const { sign } = require(`${LIB}/instagram/signature`);
  const fake = `igtest${Date.now()}`;
  const post = async (body) => {
    const raw = JSON.stringify(body);
    const r = await fetch("https://pro-host.tech/ig-webhook", { method: "POST", headers: { "Content-Type": "application/json", "X-Hub-Signature-256": sign(raw, secret) }, body: raw });
    return r.status;
  };
  const wait = async (id) => {
    for (let i = 0; i < 30; i++) {
      const d = await db.collection("ig_events").doc(id).get();
      if (d.exists && d.get("status") && d.get("status") !== "processing") return d.data();
      await new Promise((r) => setTimeout(r, 1000));
    }
    return null;
  };
  const show = (label, d) => notice(d ? `${label}: status=${d.status} rule="${d.ruleName || "-"}" reason="${d.reason}" replies="${String(d.replies || "").slice(0, 160)}"` : `${label}: no result in 30 s`);
  const now = Math.floor(Date.now() / 1000);
  const cid = `${fake}c`;
  notice(`comment accepted: HTTP ${await post({ object: "instagram", entry: [{ id: cfg.igUserId, time: now, changes: [{ field: "comments", value: { id: cid, text: "link please", from: { id: fake, username: "igtest_user" }, media: { id: "igtest_media" } } }] }] })}`);
  show("comment 'link please'", await wait(`c_${cid}`));
  const mid = `${fake}m`;
  notice(`STOP dm accepted: HTTP ${await post({ object: "instagram", entry: [{ id: cfg.igUserId, time: now, messaging: [{ sender: { id: fake }, recipient: { id: cfg.igUserId }, timestamp: now * 1000, message: { mid, text: "STOP" } }] }] })}`);
  show("DM 'STOP'", await wait(`m_${mid}`));
  const story = async (label, text) => {
    const sid = `${fake}s${label}`;
    const code = await post({ object: "instagram", entry: [{ id: cfg.igUserId, time: now, messaging: [{ sender: { id: `${fake}${label}` }, recipient: { id: cfg.igUserId }, timestamp: now * 1000, message: { mid: sid, ...(text ? { text } : {}), reply_to: { story: { id: "igtest_story", url: "https://example.invalid" } } } }] }] });
    notice(`story ${label} accepted: HTTP ${code}`);
    show(`story ${label}`, await wait(`m_${sid}`));
    await db.collection("ig_contacts").doc(`${fake}${label}`).delete();
  };
  const dmAi = async (label, text) => {
    const sid = `${fake}a${label}`;
    const sender = `${fake}ai${label}`;
    const code = await post({ object: "instagram", entry: [{ id: cfg.igUserId, time: now, messaging: [{ sender: { id: sender }, recipient: { id: cfg.igUserId }, timestamp: now * 1000, message: { mid: sid, text } }] }] });
    notice(`AI dm ${label} accepted: HTTP ${code}`);
    show(`AI dm ${label} "${text}"`, await wait(`m_${sid}`));
    await db.collection("ig_contacts").doc(sender).delete();
    await db.collection("ig_threads").doc(sender).delete();
  };
  if ((cfg.ai || {}).enabled) {
    await dmAi("en", "how can I list my clinic room and what does it cost?");
    await dmAi("ar", "كيف أحمّل التطبيق وهل يوجد نسخة للآيفون؟");
  } else notice("AI agent is off (Status ai_enabled): AI DM cases skipped");
  await story("reply", "love this!");
  await story("reaction", "");
  await db.collection("ig_contacts").doc(fake).delete();
  notice("fake contact removed; the two test events stay in the log (they reach the sheet Log tab within a minute)");
}

async function mailingsmoke(db, code) {
  const FN = "https://europe-west1-prohost-f766f.cloudfunctions.net/";
  const call = async (name, data) => {
    const r = await fetch(FN + name, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ data }) });
    const j = await r.json().catch(() => ({}));
    if (j.error) throw new Error(`${name}: ${j.error.status} ${j.error.message}`);
    return j.result;
  };
  const waitMail = async (query, label) => {
    for (let i = 0; i < 24; i++) {
      const docs = (await query.get()).docs;
      const states = docs.map((d) => d.get("delivery.state") || "PENDING");
      if (docs.length && states.every((s) => s === "SUCCESS" || s === "ERROR")) {
        notice(`${label}: ${states.join(",")}${states.includes("ERROR") ? ` (${docs.map((d) => d.get("delivery.error")).filter(Boolean).join("; ").slice(0, 200)})` : ""}`);
        return;
      }
      await new Promise((r) => setTimeout(r, 5000));
    }
    fail(`${label}: not delivered within 2 minutes`);
  };
  for (const page of ["", "admin", "get", "unsubscribe?t=smoketest-invalid-token"]) {
    const r = await fetch(`https://pro-host.tech/${page}`);
    notice(`page /${page} = ${r.status}`);
  }
  const { token } = await call("mailingAdminLogin", { code: String(code || "") });
  const data = await call("mailingAdminData", { token });
  notice(`address book: ${data.contacts.length} contacts, ${data.contacts.filter((c) => c.subscribed).length} subscribed, ${data.messages.length} messages`);
  const campaign = { subject: "ProHost mailing smoke test", heading: "Smoke test", body: "This is an automatic test of the ProHost campaign email.\n\n- Template\n- Delivery", ctaLabel: "Open ProHost", ctaUrl: "https://pro-host.tech/get" };
  const prev = await call("mailingAdminPreview", { token, campaign });
  notice(`preview html ${prev.html.length} chars, unsubscribe link ${prev.html.includes("/unsubscribe?t=") ? "present" : "MISSING"}`);
  const sent = await call("mailingAdminSend", { token, campaign, testOnly: true });
  await waitMail(db.collection("mail").where("campaignId", "==", sent.campaignId), "test campaign to admin");
  const status = await call("mailingAdminCampaign", { token, campaignId: sent.campaignId });
  notice(`campaign status: ${JSON.stringify(status.counts)}`);
  const before = Date.now();
  await call("websiteContact", { name: "ProHost smoke test", email: "admin@pro-host.tech", organization: "ProHost", role: "Other", message: "Automatic test of the pro-host.tech contact form. No action needed.", subscribe: false });
  await new Promise((r) => setTimeout(r, 3000));
  const since = admin.firestore.Timestamp.fromMillis(before - 5000);
  const last = (await db.collection("mail").where("createdAt", ">=", since).get()).docs
    .find((d) => d.get("to") === "admin@pro-host.tech" && !d.get("campaignId") && String(d.get("message.subject") || "").startsWith("New contact"));
  if (!last) fail("contact form: no notification email queued");
  else await waitMail(db.collection("mail").where(admin.firestore.FieldPath.documentId(), "==", last.id), "contact form email to admin");
}


// Live Play catalog / subscriptions, read by the functions' own service account (invited in Play Console)
// through the onMaintenanceRequest trigger: write maintenance_requests/{id}, wait for the result.
async function serverTask(db, task, arg = null) {
  const ref = db.collection("maintenance_requests").doc();
  await ref.set({ task, arg, createdAt: Date.now() });
  for (let i = 0; i < 60; i++) {
    await new Promise((r) => setTimeout(r, 2000));
    const d = (await ref.get()).data();
    if (d && d.status) {
      await ref.delete().catch(() => {});
      if (d.status !== "done") throw new Error(`${task} failed: ${d.result}`);
      return JSON.parse(d.result);
    }
  }
  throw new Error(`${task}: no answer from onMaintenanceRequest in 120 s (is it deployed?)`);
}

// Repairs Play Console slips through the Play API (functions' service account): a promo offer stranded on an
// inactive base plan is copied onto the live monthly plan, and claimed offers limited to "any subscription in this
// app" are widened to "this subscription". Dry run unless the code is "apply".
async function playofferfix(db, arg) {
  const apply = arg === "apply";
  await step("playOfferFix", async () => {
    const res = await serverTask(db, "playOfferFix", apply ? "apply" : null);
    res.steps.forEach((s) => notice(`playOfferFix ${s.step}: ${s.action} → ${s.result}`));
    return `${apply ? "applied" : "dry run (code: apply to write)"}: ${res.steps.length} step(s)`;
  });
  if (apply) await playcatalog(db);
}

async function aiquota(db, arg) {
  const res = await serverTask(db, "aiQuota", arg === "request" || arg === "status" ? arg : null);
  if (res.lines) { res.lines.forEach((l) => notice(`QUOTA STATUS ${l}`)); return; }
  if (res.results) { res.results.forEach((r) => notice(`QUOTA REQUEST ${r}`)); return; }
  notice(`Cloud Quotas: ${res.note}; ${res.rows.length} global Haiku 4.5 row(s)`);
  res.rows.forEach((r) => notice(`QUOTA ${r.quotaId} | ${JSON.stringify(r.dimensions)} | value=${r.value} ${r.unit} | raw=${r.raw}`));
}

async function aiprobe(db) {
  const rows = await serverTask(db, "aiProbe");
  rows.forEach((r) => notice(`${r.ok ? "✓" : "✗"} ${r.model} @ ${r.region}: ${r.detail}`));
  const ok = rows.filter((r) => r.ok);
  notice(ok.length ? `Vertex Claude works: first usable = ${ok[0].model} @ ${ok[0].region}` : "No Claude model answered on Vertex yet: enable Vertex AI API + Claude Haiku in Model Garden and grant the functions service account 'Vertex AI User' (see the errors above)");
}

async function playcatalog(db) {
  let cat = null;
  await step("playCatalogLive", async () => {
    cat = await serverTask(db, "playCatalog");
    const lines = [];
    for (const bp of cat.basePlans) {
      lines.push(`BASE ${bp.basePlanId} ${bp.state} ${bp.period} LB=${bp.prices.LB}${bp.availableIn.LB ? "" : " (not for new subscribers in LB)"} US=${bp.prices.US} regions=${bp.regionCount}`);
      for (const o of bp.offers) {
        lines.push(`  OFFER ${o.offerId} ${o.state} | ${o.eligibility} | tags=[${o.tags.join(",")}] | LB ${o.availableIn.LB ? "on" : "OFF"} regions=${o.regionCount}${o.otherRegions ? "+other" : ""}`);
        for (const p of o.phases) lines.push(`    phase ${p.recurrenceCount}x${p.duration} LB=${p.pricing.LB} US=${p.pricing.US}`);
      }
      if (!bp.offers.length) lines.push("  (no offers)");
    }
    lines.forEach((l) => console.log(l));
    // The detail as one annotation too (ids, prices, eligibility only — no people), readable without the raw log.
    notice(`playCatalogDetail: ${lines.map((l) => l.trim()).join(" ; ")}`);
    return `${cat.basePlans.length} base plans, ${cat.basePlans.reduce((n, b) => n + b.offers.length, 0)} offers (details in the log)`;
  });
  // Strict Play: Play Console is the only billing config, so its mistakes are flagged here (lintOffers).
  await step("playOfferLint", async () => {
    if (!cat) throw new Error("catalog not read (see playCatalogLive)");
    const lint = cat.lint || [];
    lint.forEach((w) => console.log(`WARN ${w}`));
    if (lint.length) notice(`playOfferLintDetail: ${lint.join(" ; ")}`);
    return lint.length ? `${lint.length} warning(s) — fix them in Play Console (details in the log)` : "no warnings";
  });
  // The promo card is a Play offer now (tags promo, code-…, ends-…); the old Firestore campaign doc is retired.
  await step("retiredPromoDoc", async () => {
    const ref = db.collection("promo_campaigns").doc("current");
    if (!(await ref.get()).exists) return "none";
    await ref.delete();
    return "deleted promo_campaigns/current (retired; the promo is configured in Play Console)";
  });
  await step("playSubscriptionsLive", async () => {
    const res = await serverTask(db, "playSubscriptions");
    res.rows.forEach((r) => console.log(JSON.stringify(r)));
    return `${res.count} recorded subscriptions (details in the log)`;
  });
  // Redeem-code plumbing: purchases made outside the app (Play Store / promo code) waiting to be claimed.
  await step("redeemPlumbing", async () => {
    const [links, unresolved, pending] = await Promise.all([
      db.collection("play_purchase_links").get(),
      db.collection("play_billing_unresolved").where("resolved", "==", false).get(),
      db.collection("play_billing_pending").where("resolved", "==", false).get(),
    ]);
    const kinds = {};
    links.docs.forEach((d) => { const k = d.get("adminOverride") ? "adminOverride" : d.get("claimedVia") || d.get("source") || "link"; kinds[k] = (kinds[k] || 0) + 1; });
    return `purchaseLinks=${links.size} ${JSON.stringify(kinds)} unlinkedOpen=${unresolved.size} parkedOpen=${pending.size}`;
  });
}



// Phone VERIFICATION (not sign-in) diagnosis: is Phone enabled, which countries may receive SMS (SMS region
// policy), test numbers, and are the app's signing fingerprints registered in Firebase? arg "allow:SY,..." adds
// those regions to the SMS region policy (touches only smsRegionConfig; the log says what changed).
async function smsauth(db, arg) {
  const project = process.env.GCLOUD_PROJECT;
  const base = `https://identitytoolkit.googleapis.com/admin/v2/projects/${project}/config`;
  let config = null;
  await step("smsConfig", async () => {
    const res = await googleApi(base);
    if (!res.ok) return `HTTP ${res.status} ${(await res.text()).slice(0, 200)}`;
    config = await res.json();
    const r = config.smsRegionConfig || {};
    const allow = r.allowlistOnly?.allowedRegions, deny = r.allowByDefault?.disallowedRegions;
    const policy = allow ? `ALLOWLIST ${allow.join(",")} (SY ${allow.includes("SY") ? "allowed" : "NOT allowed"})`
      : deny ? `DENYLIST ${deny.join(",")} (SY ${deny.includes("SY") ? "BLOCKED" : "allowed"})`
      : "no region policy (all regions the provider supports)";
    // SMS fraud protection (reCAPTCHA / toll-fraud rules) can silently drop sends in every country.
    const rc = config.recaptchaConfig || {};
    const fraud = `recaptchaPhoneEnforcement=${rc.phoneEnforcementState || "unset"} tollFraudRules=${JSON.stringify(rc.tollFraudManagedRules || []).slice(0, 160)} ` +
      `useSmsTollFraudProtection=${rc.useSmsTollFraudProtection === true} useSmsBotScore=${rc.useSmsBotScore === true}`;
    return `phoneEnabled=${config.signIn?.phoneNumber?.enabled === true} policy=${policy} ${fraud} ` +
      `testNumbers=${Object.keys(config.signIn?.phoneNumber?.testPhoneNumbers || {}).length} ` +
      `quotaConfigured=${Object.keys(config.quota?.signUpQuotaConfig || {}).length > 0}`;
  });
  await step("smsRegionFix", async () => {
    if (!arg || !arg.startsWith("allow:")) return "skipped (pass allow:SY to add regions to the policy)";
    if (!config) return "config not read";
    const want = arg.slice(6).split(",").map((x) => x.trim().toUpperCase()).filter(Boolean);
    const r = config.smsRegionConfig || {};
    let next, note;
    if (r.allowlistOnly) {
      const have = r.allowlistOnly.allowedRegions || [];
      const add = want.filter((x) => !have.includes(x));
      if (!add.length) return `already allowed: ${want.join(",")}`;
      next = { allowlistOnly: { allowedRegions: [...have, ...add] } };
      note = `allowlist += ${add.join(",")}`;
    } else if (r.allowByDefault) {
      const have = r.allowByDefault.disallowedRegions || [];
      const keep = have.filter((x) => !want.includes(x));
      if (keep.length === have.length) return `not on the denylist: ${want.join(",")}`;
      next = { allowByDefault: { disallowedRegions: keep } };
      note = `denylist -= ${want.filter((x) => have.includes(x)).join(",")}`;
    } else return "no region policy set: nothing to change in Firebase (the block is elsewhere)";
    const res = await googleApi(`${base}?updateMask=smsRegionConfig`, { method: "PATCH", body: JSON.stringify({ smsRegionConfig: next }) });
    return res.ok ? `updated: ${note}` : `HTTP ${res.status} ${(await res.text()).slice(0, 300)} (change it in Firebase Console › Authentication › Settings › SMS region policy)`;
  });
  // APIs phone verification depends on (Identity Toolkit sends the SMS; Android Device Verification / Play
  // Integrity prove the app) and whether billing is on (real SMS beyond test numbers needs it).
  await step("smsApisAndBilling", async () => {
    const out = [];
    const names = ["identitytoolkit.googleapis.com", "androidcheck.googleapis.com", "playintegrity.googleapis.com",
      "firebaseappcheck.googleapis.com", "recaptchaenterprise.googleapis.com"];
    const qs = names.map((n) => `names=projects/${project}/services/${n}`).join("&");
    const svc = await googleApi(`https://serviceusage.googleapis.com/v1/projects/${project}/services:batchGet?${qs}`);
    if (svc.ok) {
      const j = await svc.json();
      out.push(...(j.services || []).map((x) => `${x.config?.name}=${x.state}`));
    } else out.push(`serviceusage HTTP ${svc.status}`);
    const bill = await googleApi(`https://cloudbilling.googleapis.com/v1/projects/${project}/billingInfo`);
    out.push(bill.ok ? `billingEnabled=${(await bill.json()).billingEnabled === true}` : `billing HTTP ${bill.status}`);
    return out.join(" ");
  });
  // API keys: an Android restriction that omits the app's package/fingerprint, or an API target list that
  // leaves out Identity Toolkit, breaks phone verification exactly like a missing SHA.
  await step("apiKeyRestrictions", async () => {
    const res = await googleApi(`https://apikeys.googleapis.com/v2/projects/${project}/locations/global/keys`);
    if (!res.ok) return `HTTP ${res.status} ${(await res.text()).slice(0, 160)}`;
    const keys = (await res.json()).keys || [];
    return keys.map((k) => {
      const r = k.restrictions || {};
      const android = r.androidKeyRestrictions ? `android(${(r.androidKeyRestrictions.allowedApplications || []).map((a) => a.packageName).join("|")} apps=${(r.androidKeyRestrictions.allowedApplications || []).length})` : "";
      const targets = (r.apiTargets || []).map((t) => t.service.replace(".googleapis.com", "")).join("|");
      return `${k.displayName || k.uid}: ${android || "no-android-restriction"} apis=${targets || "any"}`;
    }).join(" ;; ") || "no keys listed";
  });
  // Fingerprints registered for the Android app vs the Play app-signing secrets (values never printed).
  await step("androidFingerprints", async () => {
    const list = await googleApi(`https://firebase.googleapis.com/v1beta1/projects/${project}/androidApps`);
    if (!list.ok) return `HTTP ${list.status} ${(await list.text()).slice(0, 160)}`;
    const app = ((await list.json()).apps || []).find((a) => a.packageName === "app.geonajjar.prohost");
    if (!app) return "Android app app.geonajjar.prohost not found in Firebase";
    const sha = await googleApi(`https://firebase.googleapis.com/v1beta1/${app.name}/sha`);
    if (!sha.ok) return `HTTP ${sha.status} ${(await sha.text()).slice(0, 160)}`;
    const certs = ((await sha.json()).certificates || []).map((c) => String(c.shaHash || "").toLowerCase());
    const norm = (v) => String(v || "").replace(/[: \n]/g, "").toLowerCase();
    const want = { playSha1: norm(process.env.PLAY_APP_SIGNING_SHA1), playSha256: norm(process.env.PLAY_APP_SIGNING_SHA256) };
    const has = (h) => h ? certs.includes(h) : "secret-missing";
    return `registered=${certs.length} (sha1=${certs.filter((c) => c.length === 40).length} sha256=${certs.filter((c) => c.length === 64).length}) ` +
      `playAppSigningSha1Registered=${has(want.playSha1)} playAppSigningSha256Registered=${has(want.playSha256)}`;
  });
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
  else if (task === "mailingcode") await mailingcode(db, arg);
  else if (task === "mailingsmoke") await mailingsmoke(db, arg);
  else if (task === "igsetup") await igsetup(db, arg);
  else if (task === "igsmoke") await igsmoke(db);
  else if (task === "igtest") await igtest(db);
  else if (task === "aiprobe") await aiprobe(db);
  else if (task === "aiquota") await aiquota(db, arg);
  else if (task === "playcatalog") await playcatalog(db);
  else if (task === "smsauth") await smsauth(db, arg);
  else if (task === "playofferfix") await playofferfix(db, arg);
  else throw new Error(`unknown task ${task}`);
})().catch((e) => { fail(`${e && e.message}`); process.exit(1); });
