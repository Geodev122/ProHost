/**
 * Pure helpers for pro-host.tech/sitemap.xml (kept separate from the HTTP handler so they're unit-tested).
 * Static pages come from STATIC_PAGES; listings are added only when they're publicly browsable.
 */
export const SITE = "https://pro-host.tech";

/**
 * Indexable static pages: path, change frequency, priority. Keep in sync with public/ and firebase.json.
 * Slugs name no country or city (ProHost is open to every market); the retired "-lebanon" / "-beirut" slugs are
 * 301-redirected in firebase.json and must never come back here.
 */
export const STATIC_PAGES: ReadonlyArray<[string, string, string]> = [
  ["/", "weekly", "1.0"],
  ["/ar", "weekly", "0.9"],
  ["/list-your-space", "weekly", "0.9"],
  ["/clinic-room-rental", "weekly", "0.9"],
  ["/therapy-room-rental", "weekly", "0.9"],
  ["/meeting-room-rental", "weekly", "0.9"],
  ["/training-room-rental", "weekly", "0.8"],
  ["/studio-rental", "weekly", "0.8"],
  ["/office-space-by-the-hour", "weekly", "0.8"],
  ["/privacy", "yearly", "0.3"],
  ["/terms", "yearly", "0.3"],
  ["/delete-account", "yearly", "0.2"],
];

/** Same visibility as Discovery: ACTIVE, owned, not suspended/lapsed, not demo content. */
export function isPublicListing(d: Record<string, unknown> | undefined): boolean {
  if (!d) return false;
  return d.status === "ACTIVE" && typeof d.ownerId === "string" && d.ownerId.length > 0 &&
    d.isOwnerSuspended !== true && d.isOwnerPackageLapsed !== true && d.isDemo !== true;
}

const xmlEsc = (s: string) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");

function isoDay(ms: unknown): string | null {
  const n = typeof ms === "number" ? ms : NaN;
  return Number.isFinite(n) && n > 0 ? new Date(n).toISOString().slice(0, 10) : null;
}

export function buildSitemap(listings: Array<{ id: string; updatedAt?: unknown }>, today = new Date().toISOString().slice(0, 10)): string {
  const urls = STATIC_PAGES.map(([path, freq, prio]) =>
    `<url><loc>${SITE}${path}</loc><lastmod>${today}</lastmod><changefreq>${freq}</changefreq><priority>${prio}</priority></url>`);
  for (const l of listings) {
    if (!/^[A-Za-z0-9_-]{1,128}$/.test(l.id)) continue;
    const mod = isoDay(l.updatedAt);
    urls.push(`<url><loc>${SITE}/listing/${xmlEsc(l.id)}</loc>${mod ? `<lastmod>${mod}</lastmod>` : ""}<changefreq>weekly</changefreq><priority>0.7</priority></url>`);
  }
  return `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${urls.join("\n")}\n</urlset>\n`;
}
