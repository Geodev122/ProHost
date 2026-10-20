#!/usr/bin/env node
/**
 * Google Play publishing through the Android Publisher API (edits), run by the release workflow with the CI service
 * account (GOOGLE_APPLICATION_CREDENTIALS). The account needs "Release to production…" permission for the app in Play
 * Console › Users and permissions; a 401/403 says so.
 *
 *   node scripts/release/play-publish.js probe
 *       Read-only: what each track currently holds, the uploaded version codes, the listing languages. The edit is
 *       deleted, nothing changes.
 *   node scripts/release/play-publish.js publish <track> <aab> <notes.txt> [userFraction] [mapping.txt] [native-symbols.zip]
 *       Uploads the bundle (+ R8 mapping and native symbols), puts it on <track> with the notes, and commits.
 *       userFraction < 1 makes a staged rollout ("inProgress"); 1 releases to everyone ("completed").
 *
 * Results are printed as GitHub annotations (::notice / ::error) so they are readable without the logs.
 */
const fs = require("fs");
const path = require("path");

const PACKAGE = "app.geonajjar.prohost";
const API = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${PACKAGE}`;
const UPLOAD = `https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/${PACKAGE}`;

function notice(msg) { console.log(`::notice title=play::${msg.replace(/\n/g, " ")}`); }
function fail(msg) { console.log(`::error title=play::${msg.replace(/\n/g, " ")}`); process.exit(1); }

async function client() {
  const { GoogleAuth } = require("google-auth-library");
  return new GoogleAuth({ scopes: ["https://www.googleapis.com/auth/androidpublisher"] }).getClient();
}

async function call(c, opts) {
  try {
    return (await c.request(opts)).data;
  } catch (e) {
    const st = e.response?.status;
    const msg = e.response?.data?.error?.message || e.message;
    const hint = st === 401 || st === 403
      ? " — the CI service account needs release permission for ProHost in Play Console › Users and permissions"
      : "";
    throw new Error(`${opts.method || "GET"} ${opts.url.replace(API, "").replace(UPLOAD, "upload")} → HTTP ${st}: ${String(msg).slice(0, 300)}${hint}`);
  }
}

/** docs/release/<version>-notes.txt: <en-US>…</en-US><ar>…</ar>… → [{language, text}] */
function parseNotes(file) {
  const raw = fs.readFileSync(file, "utf8");
  const out = [];
  for (const m of raw.matchAll(/<([a-zA-Z-]+)>\n?([\s\S]*?)\n?<\/\1>/g)) out.push({ language: m[1], text: m[2].trim() });
  if (!out.length) throw new Error(`no <lang>…</lang> blocks in ${file}`);
  for (const n of out) if (n.text.length > 500) throw new Error(`${n.language} notes are ${n.text.length} characters (Play allows 500)`);
  return out;
}

async function probe() {
  const c = await client();
  const edit = await call(c, { url: `${API}/edits`, method: "POST", data: {} });
  try {
    const tracks = (await call(c, { url: `${API}/edits/${edit.id}/tracks` })).tracks || [];
    for (const t of tracks) {
      const rel = (t.releases || []).map((r) => `${r.name || "?"}[${(r.versionCodes || []).join(",")}] ${r.status}${r.userFraction ? ` ${r.userFraction * 100}%` : ""}`);
      notice(`track ${t.track}: ${rel.join(" | ") || "empty"}`);
    }
    const bundles = (await call(c, { url: `${API}/edits/${edit.id}/bundles` })).bundles || [];
    notice(`uploaded bundle version codes: ${bundles.map((b) => b.versionCode).sort((a, b) => a - b).slice(-8).join(", ") || "none"}`);
    const listings = (await call(c, { url: `${API}/edits/${edit.id}/listings` })).listings || [];
    notice(`listing languages: ${listings.map((l) => l.language).join(", ")}`);
    const details = await call(c, { url: `${API}/edits/${edit.id}/details` });
    notice(`default language: ${details.defaultLanguage}`);
  } finally {
    await call(c, { url: `${API}/edits/${edit.id}`, method: "DELETE" }).catch(() => {});
  }
}

async function publish(track, aab, notesFile, fraction, mapping, symbols) {
  const userFraction = Number(fraction || "1");
  if (!(userFraction > 0 && userFraction <= 1)) throw new Error(`userFraction must be in (0, 1], got ${fraction}`);
  const notes = parseNotes(notesFile);
  const c = await client();
  const edit = await call(c, { url: `${API}/edits`, method: "POST", data: {} });
  let committed = false;
  try {
    // Notes only for languages the store listing has (Play rejects the others); the default language always.
    const listings = (await call(c, { url: `${API}/edits/${edit.id}/listings` })).listings || [];
    const details = await call(c, { url: `${API}/edits/${edit.id}/details` });
    const langs = new Set([details.defaultLanguage, ...listings.map((l) => l.language)]);
    const releaseNotes = notes.filter((n) => langs.has(n.language));
    const skipped = notes.filter((n) => !langs.has(n.language)).map((n) => n.language);
    if (!releaseNotes.length) throw new Error(`none of the notes' languages (${notes.map((n) => n.language)}) is in the listing (${[...langs]})`);

    // Play refuses a version code it has seen before ("already been used"): bump versionCode for a new upload.
    const bundle = await call(c, {
      url: `${UPLOAD}/edits/${edit.id}/bundles?uploadType=media`, method: "POST",
      headers: { "Content-Type": "application/octet-stream" }, data: fs.createReadStream(aab),
      timeout: 15 * 60 * 1000,
    });
    const versionCode = bundle.versionCode;
    notice(`uploaded bundle versionCode ${versionCode} (sha256 ${bundle.sha256})`);

    for (const [file, type] of [[mapping, "proguard"], [symbols, "nativeCode"]]) {
      if (!file || !fs.existsSync(file) || fs.statSync(file).size === 0) continue;
      await call(c, {
        url: `${UPLOAD}/edits/${edit.id}/apks/${versionCode}/deobfuscationFiles/${type}?uploadType=media`, method: "POST",
        headers: { "Content-Type": "application/octet-stream" }, data: fs.createReadStream(file), timeout: 10 * 60 * 1000,
      });
      notice(`uploaded ${type} symbols (${path.basename(file)})`);
    }

    // Release name "1.0.43 (43)": the version from the notes file name, the code from Play.
    const name = `${path.basename(notesFile).replace("-notes.txt", "")} (${versionCode})`;
    const release = {
      name, versionCodes: [String(versionCode)], releaseNotes,
      status: userFraction < 1 ? "inProgress" : "completed",
      ...(userFraction < 1 ? { userFraction } : {}),
    };
    await call(c, { url: `${API}/edits/${edit.id}/tracks/${track}`, method: "PUT", data: { track, releases: [release] } });
    await call(c, { url: `${API}/edits/${edit.id}:commit`, method: "POST" });
    committed = true;
    notice(`released ${name} on ${track} at ${Math.round(userFraction * 100)}% (notes: ${releaseNotes.map((n) => n.language).join(", ")}${skipped.length ? `; not in listing: ${skipped.join(", ")}` : ""})`);
  } finally {
    if (!committed) await call(c, { url: `${API}/edits/${edit.id}`, method: "DELETE" }).catch(() => {});
  }
}

(async () => {
  const [cmd, ...args] = process.argv.slice(2);
  try {
    if (cmd === "probe") await probe();
    else if (cmd === "publish") await publish(...args);
    else fail("usage: play-publish.js probe | publish <track> <aab> <notes> [fraction] [mapping] [symbols]");
  } catch (e) {
    fail(e.message);
  }
})();
