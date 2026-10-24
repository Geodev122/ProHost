import { onRequest } from "firebase-functions/v2/https";
import { getFirestore } from "firebase-admin/firestore";
import "../lib/admin";
import { isPublicListing } from "../website/sitemapXml";
import { listingPlace } from "./listingLocation";

// ProHost's own registered marketing/App-Link domain — a real custom domain
// connected to this same Firebase Hosting site (already carrying the
// marketing landing page and legal docs, see public/index.html). Serves the
// exact same Hosting deployment
// (assetlinks.json included), so Android App Link verification works with
// zero extra domain registration/DNS work.
const CANONICAL_HOST = "https://pro-host.tech";
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

const APP_PACKAGE = "app.geonajjar.prohost";

/**
 * Android hand-off, run in the browser (the page is CDN-cached, so it must not vary by user agent). Instagram and other
 * in-app browsers never fire App Links, so on Android the "Open in ProHost App" button becomes an intent link to the same
 * https URL (the app's verified /listing filter takes it; `room` is kept), falling back to Play when the app is missing,
 * and the page tries once to open the app by itself, falling back to this page with `noapp=1` (no loop).
 */
export function appHandoffScript(playUrl: string): string {
  return `<script>(function(){var ua=navigator.userAgent||"";if(!/Android/i.test(ua))return;` +
    `var q=new URLSearchParams(location.search),p=location.pathname,k=new URLSearchParams();if(q.get("room"))k.set("room",q.get("room"));` +
    `var path=location.host+p+(k.toString()?"?"+k.toString():"");` +
    `function intent(fb){return "intent://"+path+"#Intent;scheme=https;package=${APP_PACKAGE};S.browser_fallback_url="+encodeURIComponent(fb)+";end"}` +
    `var b=document.getElementById("open-app");if(b)b.href=intent(${JSON.stringify(playUrl)});` +
    `if(!q.get("noapp")){q.set("noapp","1");var back=location.origin+p+"?"+q.toString();setTimeout(function(){location.href=intent(back)},300)}})();</script>`;
}

function renderPage(opts: {
  title: string;
  description: string;
  image: string;
  url: string;
  bodyHeading: string;
  bodySubtext: string;
  /** Only real, publicly browsable listings are indexed; error/unavailable pages are noindex. */
  indexable?: boolean;
  /** Extra escaped HTML under the card (rooms, description, related links). */
  extraHtml?: string;
  jsonLd?: object;
  /** A real listing: on Android, hand the visitor over to the app (see appHandoffScript). */
  appHandoff?: boolean;
}): string {
  const { title, description, image, url, bodyHeading, bodySubtext, indexable = false, extraHtml = "", jsonLd, appHandoff = false } = opts;
  return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>${escapeHtml(title)}</title>
<meta name="description" content="${escapeHtml(description.slice(0, 300))}">
<meta name="robots" content="${indexable ? "index, follow, max-image-preview:large" : "noindex, follow"}">
<link rel="canonical" href="${escapeHtml(url)}">${jsonLd ? `
<script type="application/ld+json">${JSON.stringify(jsonLd).replace(/</g, "\\u003c")}</script>` : ""}
<meta property="og:type" content="website">
<meta property="og:site_name" content="ProHost">
<meta property="og:title" content="${escapeHtml(title)}">
<meta property="og:description" content="${escapeHtml(description.slice(0, 300))}">
<meta property="og:image" content="${escapeHtml(image)}">
<meta property="og:url" content="${escapeHtml(url)}">
<meta name="twitter:card" content="summary_large_image">
<meta name="twitter:title" content="${escapeHtml(title)}">
<meta name="twitter:description" content="${escapeHtml(description.slice(0, 300))}">
<meta name="twitter:image" content="${escapeHtml(image)}">
<link rel="icon" type="image/png" href="/logo.png">
<style>
  :root {
    --primary: #F25F4C;
    --oxford-blue: #384152;
    --vibrant-blue: #2B5A8C;
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
  .more { margin-top: 1.25rem; background: var(--card-bg); border: 1px solid var(--border-color); border-radius: 1rem; padding: 1.25rem 1.5rem; }
  .more h2 { font-size: 1.1rem; color: var(--oxford-blue); margin-bottom: .5rem; }
  .more p, .more li { color: var(--text-dark); font-size: .95rem; }
  .more ul { padding-left: 1.2rem; display: grid; gap: .3rem; }
  .links { margin-top: 1rem; display: flex; flex-wrap: wrap; gap: .4rem 1rem; font-size: .9rem; }
  .links a { color: var(--vibrant-blue); }
  footer { text-align: center; padding: 1.5rem; color: var(--text-muted); font-size: 0.85rem; }
</style>
</head>
<body>
  <header>
    <div class="nav-container">
      <a href="${CANONICAL_HOST}/"><img src="/logo.png" alt="ProHost"></a>
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
          <a class="btn btn-primary" id="open-app" href="${escapeHtml(url)}">Open in ProHost App</a>
          <a class="btn btn-secondary" href="${escapeHtml(PLAY_STORE_URL)}">Get the App</a>
        </div>
      </div>
    </div>${extraHtml}
  </main>
  <footer><a href="${CANONICAL_HOST}/" style="color:inherit">ProHost</a> &mdash; rent clinics, offices, studios and meeting rooms by the hour, shift, day or month</footer>${appHandoff ? `
  ${appHandoffScript(PLAY_STORE_URL)}` : ""}
</body>
</html>`;
}

/**
 * Serves a branded, per-listing landing page with real Open Graph / Twitter
 * Card meta tags — the piece a static Hosting page can't provide, since the
 * title/description/image must reflect the actual listing being shared.
 * Reached via a Hosting rewrite (firebase.json: "/listing/**" -> this
 * function) at https://pro-host.tech/listing/{spaceId} — ProHost's own
 * registered domain, not the raw Firebase default hosting domain.
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
    if (!snap.exists || !data || !isPublicListing(data)) {
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
    const place = listingPlace(data);
    const category: string = typeof data.spaceCategoryName === "string" ? data.spaceCategoryName : "";
    const location = place.text;
    const description = rawDescription.length > 0
      ? rawDescription
      : [category, location].filter(Boolean).join(" in ") || "A workspace listing on ProHost.";
    const image: string = Array.isArray(data.imageUrls) && typeof data.imageUrls[0] === "string" && data.imageUrls[0]
      ? data.imageUrls[0]
      : FALLBACK_IMAGE;

    const rooms = (Array.isArray(data.subdivisions) ? data.subdivisions : [])
      .map((r: { name?: unknown; type?: unknown }) => ({
        name: typeof r?.name === "string" ? r.name.trim() : "",
        type: typeof r?.type === "string" ? r.type.replace(/_/g, " ").toLowerCase().replace(/\b\w/g, (c: string) => c.toUpperCase()) : "",
      }))
      .filter((r: { name: string }) => r.name)
      .slice(0, 30);
    const where = location ? ` in ${location}` : "";
    const pageTitle = `${title}${category ? ` · ${category}` : ""} for rent${where} | ProHost`;
    const extraHtml = `
    <section class="more">
      ${rawDescription ? `<h2>About this space</h2><p>${escapeHtml(rawDescription.slice(0, 2000))}</p>` : ""}
      ${rooms.length ? `<h2 style="margin-top:1rem">Rooms you can book</h2><ul>${rooms.map((r: { name: string; type: string }) =>
        `<li>${escapeHtml(r.name)}${r.type ? ` · ${escapeHtml(r.type)}` : ""}</li>`).join("")}</ul>` : ""}
      <p style="margin-top:1rem">Book by the hour, shift, day, month or per attendee and see live availability in the free ProHost app.</p>
      <div class="links"><a href="${CANONICAL_HOST}/clinic-room-rental">Clinic rooms</a><a href="${CANONICAL_HOST}/therapy-room-rental">Therapy rooms</a><a href="${CANONICAL_HOST}/meeting-room-rental">Meeting rooms</a><a href="${CANONICAL_HOST}/office-space-by-the-hour">Offices</a><a href="${CANONICAL_HOST}/list-your-space">List your space</a></div>
    </section>`;
    const jsonLd = {
      "@context": "https://schema.org",
      "@graph": [
        {
          "@type": "Place", "@id": `${url}#place`, name: title, url, image, description: description.slice(0, 500),
          address: { "@type": "PostalAddress", addressLocality: place.locality || undefined, addressRegion: place.region || undefined, addressCountry: place.countryCode },
          ...(rooms.length ? { containsPlace: rooms.map((r: { name: string; type: string }) => ({ "@type": "Room", name: r.name, ...(r.type ? { description: r.type } : {}) })) } : {}),
        },
        {
          "@type": "BreadcrumbList", itemListElement: [
            { "@type": "ListItem", position: 1, name: "ProHost", item: `${CANONICAL_HOST}/` },
            { "@type": "ListItem", position: 2, name: title, item: url },
          ],
        },
      ],
    };

    res.set("Cache-Control", "public, max-age=300, s-maxage=900");
    res.status(200).send(renderPage({
      title: pageTitle,
      description,
      image,
      url,
      bodyHeading: title,
      bodySubtext: location ? `${category ? category + " • " : ""}${location}` : description,
      indexable: true,
      extraHtml,
      jsonLd,
      appHandoff: true,
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
