import { onRequest } from "firebase-functions/v2/https";
import { getFirestore } from "firebase-admin/firestore";
import "../lib/admin";

const CANONICAL_HOST = "https://pro-host.tech";
const PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=app.geonajjar.prohost";

// Public route -> the exact same legal_documents/{docId} Firestore doc the
// in-app Admin Console "Legal Documents" card and LegalDocumentDialog use
// (see DataModels.kt's LegalDocumentVersion.ADMIN_MANAGED_DOC_IDS). This is
// the strategy revision: the public pro-host.tech pages and the in-app
// WebView popups now read the exact same admin-published source, instead of
// hand-maintained static HTML that could silently drift from what the app
// actually shows.
const ROUTE_TO_DOC_ID: Record<string, string> = {
  "/privacy": "privacy_policy",
  "/terms": "terms_of_use",
  "/revocation": "revocation_policy",
};

const ROUTE_TO_TITLE: Record<string, string> = {
  "/privacy": "Privacy Policy",
  "/terms": "Terms of Use",
  "/revocation": "Revocation Policy",
};

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

// A thin, on-brand navigation strip injected right after <body> so a visitor
// can still get back to the marketing site / app download, without touching
// the admin-uploaded document's own content or styling.
function siteBanner(): string {
  return `<div style="background:#384152;color:#fff;padding:10px 20px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:14px;display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap;">
  <a href="/" style="color:#fff;text-decoration:none;font-weight:700;">&larr; ProHost</a>
  <a href="${PLAY_STORE_URL}" style="color:#fff;text-decoration:none;font-weight:700;background:#F25F4C;padding:6px 14px;border-radius:6px;">Get the App</a>
</div>`;
}

function injectBanner(html: string): string {
  if (/<body[^>]*>/i.test(html)) {
    return html.replace(/<body[^>]*>/i, (match) => `${match}${siteBanner()}`);
  }
  // Defensive fallback if the stored content isn't a full <html> document
  // for some reason — never show a bannerless page.
  return `${siteBanner()}${html}`;
}

function renderPlaceholder(title: string, url: string): string {
  return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>${escapeHtml(title)} - ProHost</title>
<link rel="icon" type="image/png" href="/logo.png">
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; }
  body { background: #F8F9FA; color: #283544; min-height: 100vh; display: flex; flex-direction: column; }
  main { flex: 1; display: flex; align-items: center; justify-content: center; padding: 2rem; text-align: center; }
  h1 { font-size: 1.4rem; margin-bottom: 0.75rem; }
  p { color: #64748B; max-width: 32rem; }
</style>
</head>
<body>
  ${siteBanner()}
  <main>
    <div>
      <h1>${escapeHtml(title)} isn't published yet</h1>
      <p>Check back shortly — this page is generated from ProHost's admin-managed legal content.</p>
    </div>
  </main>
</body>
</html>`;
}

/**
 * Serves ProHost's 3 public legal pages (Privacy Policy / Terms of Use /
 * Revocation Policy) at pro-host.tech/privacy, /terms, /revocation — read
 * live from the same admin-published Storage HTML the in-app WebView popups
 * already use (legal_documents/{docId} in Firestore -> its current Storage
 * URL). This replaces the old hand-maintained static privacy.html/terms.html
 * files, which had no connection to what an admin actually publishes via
 * Admin Console's Legal Documents card and could silently disagree with the
 * in-app copy. A short cache window (5 min) means a fresh admin upload shows
 * up quickly without hitting Storage on every single request.
 */
export const legalDocumentPage = onRequest(async (req, res) => {
  const docId = ROUTE_TO_DOC_ID[req.path];
  const title = ROUTE_TO_TITLE[req.path] ?? "Legal Document";
  const url = `${CANONICAL_HOST}${req.path}`;

  if (!docId) {
    res.status(404).send(renderPlaceholder("Page not found", url));
    return;
  }

  try {
    const snap = await getFirestore().collection("legal_documents").doc(docId).get();
    const data = snap.data();
    const storageUrl = typeof data?.url === "string" ? data.url : null;
    if (!storageUrl) {
      res.status(200).send(renderPlaceholder(title, url));
      return;
    }
    const resp = await fetch(storageUrl);
    if (!resp.ok) {
      res.status(200).send(renderPlaceholder(title, url));
      return;
    }
    const html = await resp.text();
    res.set("Cache-Control", "public, max-age=300");
    res.status(200).send(injectBanner(html));
  } catch {
    res.status(200).send(renderPlaceholder(title, url));
  }
});
