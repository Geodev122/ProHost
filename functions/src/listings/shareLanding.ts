import { onRequest } from "firebase-functions/v2/https";
import { getFirestore } from "firebase-admin/firestore";
import "../lib/admin";

// Same custom domain already used for the Whish payment App Link (see
// initiateWhishPayment.ts / AndroidManifest.xml's autoVerify intent-filter) —
// reusing it means one already-verified Digital Asset Links domain covers
// both link families instead of standing up a second one.
const CANONICAL_HOST = "https://hopebearer-award.com";
const FALLBACK_IMAGE = `${CANONICAL_HOST}/logo.png`;
const PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=app.geonajjar.prohost";

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function renderPage(opts: {
  title: string;
  description: string;
  image: string;
  url: string;
  bodyHeading: string;
  bodySubtext: string;
}): string {
  const { title, description, image, url, bodyHeading, bodySubtext } = opts;
  return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>${escapeHtml(title)}</title>
<meta name="description" content="${escapeHtml(description)}">
<meta property="og:type" content="website">
<meta property="og:site_name" content="ProHost">
<meta property="og:title" content="${escapeHtml(title)}">
<meta property="og:description" content="${escapeHtml(description)}">
<meta property="og:image" content="${escapeHtml(image)}">
<meta property="og:url" content="${escapeHtml(url)}">
<meta name="twitter:card" content="summary_large_image">
<meta name="twitter:title" content="${escapeHtml(title)}">
<meta name="twitter:description" content="${escapeHtml(description)}">
<meta name="twitter:image" content="${escapeHtml(image)}">
<link rel="icon" type="image/png" href="/logo.png">
<style>
  :root {
    --primary: #F25F4C;
    --oxford-blue: #384152;
    --vibrant-blue: #246BEE;
    --bg-light: #F8F9FA;
    --card-bg: #FFFFFF;
    --text-dark: #283544;
    --text-muted: #64748B;
    --border-color: #E2E4E8;
  }
  * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; }
  body { background-color: var(--bg-light); color: var(--text-dark); line-height: 1.6; min-height: 100vh; display: flex; flex-direction: column; }
  header { background-color: #FFFFFF; border-bottom: 1px solid var(--border-color); }
  .nav-container { max-width: 720px; margin: 0 auto; padding: 1rem 1.5rem; display: flex; align-items: center; gap: 0.75rem; }
  .nav-container img { width: 32px; height: 36px; object-fit: contain; }
  .brand-title { font-size: 1.2rem; font-weight: 800; }
  .brand-pro { color: var(--oxford-blue); }
  .brand-host { color: var(--vibrant-blue); }
  main { flex: 1; max-width: 720px; margin: 0 auto; padding: 2rem 1.5rem 3rem; width: 100%; }
  .card { background: var(--card-bg); border: 1px solid var(--border-color); border-radius: 1rem; overflow: hidden; box-shadow: 0 4px 24px rgba(15,23,42,0.06); }
  .card img { width: 100%; aspect-ratio: 16/9; object-fit: cover; display: block; background: var(--border-color); }
  .card-body { padding: 1.5rem; }
  h1 { font-size: 1.5rem; color: var(--oxford-blue); font-weight: 800; margin-bottom: 0.5rem; }
  .subtext { color: var(--text-muted); font-size: 0.95rem; margin-bottom: 1.5rem; }
  .cta-row { display: flex; flex-wrap: wrap; gap: 0.75rem; }
  .btn { display: inline-flex; align-items: center; justify-content: center; padding: 0.75rem 1.25rem; border-radius: 0.6rem; font-weight: 700; font-size: 0.95rem; text-decoration: none; }
  .btn-primary { background: var(--vibrant-blue); color: #fff; }
  .btn-secondary { background: #fff; color: var(--oxford-blue); border: 1px solid var(--border-color); }
  footer { text-align: center; padding: 1.5rem; color: var(--text-muted); font-size: 0.85rem; }
</style>
</head>
<body>
  <header>
    <div class="nav-container">
      <img src="/logo.png" alt="ProHost">
      <span class="brand-title"><span class="brand-pro">Pro</span><span class="brand-host">Host</span></span>
    </div>
  </header>
  <main>
    <div class="card">
      <img src="${escapeHtml(image)}" alt="${escapeHtml(title)}">
      <div class="card-body">
        <h1>${escapeHtml(bodyHeading)}</h1>
        <p class="subtext">${escapeHtml(bodySubtext)}</p>
        <div class="cta-row">
          <a class="btn btn-primary" href="${escapeHtml(url)}">Open in ProHost App</a>
          <a class="btn btn-secondary" href="${escapeHtml(PLAY_STORE_URL)}">Get the App</a>
        </div>
      </div>
    </div>
  </main>
  <footer>ProHost &mdash; workspace rentals in Lebanon</footer>
</body>
</html>`;
}

/**
 * Serves a branded, per-listing landing page with real Open Graph / Twitter
 * Card meta tags — the piece a static Hosting page can't provide, since the
 * title/description/image must reflect the actual listing being shared.
 * Reached via a Hosting rewrite (firebase.json: "/listing/**" -> this
 * function) at https://hopebearer-award.com/listing/{spaceId}.
 *
 * Link-preview crawlers (WhatsApp, Telegram, Facebook, iMessage, etc.) fetch
 * this URL server-side over plain HTTP with no OS involvement, so Android's
 * App Link verification never applies to them — they always see this page's
 * meta tags. A human tapping the same link on a device with ProHost
 * installed never reaches this page at all: the OS intercepts the App Link
 * before it hits a browser. This page is what non-app-users and preview bots
 * see, and its own "Open in ProHost App" button reuses the identical URL,
 * which the OS *will* intercept on a second tap once the app is installed.
 */
export const listingShareLanding = onRequest(async (req, res) => {
  const match = req.path.match(/\/listing\/([^/]+)/);
  const spaceId = match?.[1];
  const url = spaceId ? `${CANONICAL_HOST}/listing/${spaceId}` : CANONICAL_HOST;

  if (!spaceId) {
    res.status(404).send(renderPage({
      title: "Listing not found - ProHost",
      description: "This ProHost listing link is invalid.",
      image: FALLBACK_IMAGE,
      url,
      bodyHeading: "Listing not found",
      bodySubtext: "This link doesn't point to a valid ProHost listing.",
    }));
    return;
  }

  try {
    const snap = await getFirestore().collection("workspace_listings").doc(spaceId).get();
    const data = snap.data();
    // Only ACTIVE listings are meant to be publicly browsable (see
    // Discovery's own status filter) — a Draft/Paused listing's title and
    // photos are not meant to leak into a link preview either.
    if (!snap.exists || !data || data.status !== "ACTIVE") {
      res.status(404).send(renderPage({
        title: "Listing not available - ProHost",
        description: "This listing is no longer available on ProHost.",
        image: FALLBACK_IMAGE,
        url,
        bodyHeading: "Listing not available",
        bodySubtext: "This listing may have been removed or is no longer published.",
      }));
      return;
    }

    const title: string = typeof data.title === "string" && data.title.trim() ? data.title : "A ProHost Listing";
    const rawDescription: string = typeof data.description === "string" ? data.description.trim() : "";
    const district: string = typeof data.district === "string" ? data.district : "";
    const governorateLabel: string = typeof data.governorate === "string"
      ? data.governorate.replace(/_/g, " ").toLowerCase().replace(/\b\w/g, (c: string) => c.toUpperCase())
      : "";
    const category: string = typeof data.spaceCategoryName === "string" ? data.spaceCategoryName : "";
    const location = [district, governorateLabel].filter(Boolean).join(", ");
    const description = rawDescription.length > 0
      ? rawDescription
      : [category, location].filter(Boolean).join(" in ") || "A workspace listing on ProHost.";
    const image: string = Array.isArray(data.imageUrls) && typeof data.imageUrls[0] === "string" && data.imageUrls[0]
      ? data.imageUrls[0]
      : FALLBACK_IMAGE;

    res.status(200).send(renderPage({
      title: `${title} - ProHost`,
      description,
      image,
      url,
      bodyHeading: title,
      bodySubtext: location ? `${category ? category + " • " : ""}${location}` : description,
    }));
  } catch {
    // Never let a Firestore hiccup surface as a raw 500 to a link-preview
    // crawler — fall back to a generic branded card instead of nothing.
    res.status(200).send(renderPage({
      title: "ProHost",
      description: "View this listing in the ProHost app.",
      image: FALLBACK_IMAGE,
      url,
      bodyHeading: "ProHost Listing",
      bodySubtext: "Open the ProHost app to view this listing's full details.",
    }));
  }
});
