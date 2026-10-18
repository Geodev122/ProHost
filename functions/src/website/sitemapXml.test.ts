import { test } from "node:test";
import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { STATIC_PAGES, buildSitemap, isPublicListing } from "./sitemapXml";

// lib/website/*.test.js → repository root
const ROOT = join(__dirname, "..", "..", "..");

test("only publicly browsable listings are indexable", () => {
  const ok = { status: "ACTIVE", ownerId: "u1" };
  assert.equal(isPublicListing(ok), true);
  assert.equal(isPublicListing({ ...ok, status: "DRAFT" }), false);
  assert.equal(isPublicListing({ ...ok, ownerId: "" }), false);
  assert.equal(isPublicListing({ ...ok, isOwnerSuspended: true }), false);
  assert.equal(isPublicListing({ ...ok, isOwnerPackageLapsed: true }), false);
  assert.equal(isPublicListing({ ...ok, isDemo: true }), false);
  assert.equal(isPublicListing(undefined), false);
});

test("sitemap lists static pages and listing pages, skipping unsafe ids", () => {
  const xml = buildSitemap([{ id: "abc123", updatedAt: Date.UTC(2026, 9, 1) }, { id: "bad/id" }, { id: "x<y" }], "2026-10-11");
  assert.match(xml, /^<\?xml version="1.0" encoding="UTF-8"\?>/);
  assert.equal((xml.match(/<url>/g) ?? []).length, STATIC_PAGES.length + 1);
  assert.ok(xml.includes("<loc>https://pro-host.tech/</loc><xhtml:link"));
  assert.ok(xml.includes('<xhtml:link rel="alternate" hreflang="ar" href="https://pro-host.tech/ar"/>'));
  assert.ok(xml.includes('xmlns:xhtml="http://www.w3.org/1999/xhtml"'));
  assert.ok(xml.includes("<loc>https://pro-host.tech/list-your-space</loc><lastmod>2026-10-11</lastmod>"));
  assert.ok(xml.includes("<loc>https://pro-host.tech/listing/abc123</loc><lastmod>2026-10-01</lastmod>"));
  assert.ok(!xml.includes("bad/id") && !xml.includes("x<y"));
});

test("listing without a timestamp has no lastmod", () => {
  const xml = buildSitemap([{ id: "a1" }], "2026-10-11");
  assert.ok(xml.includes("<loc>https://pro-host.tech/listing/a1</loc><changefreq>"));
});

test("static page slugs name no country or city", () => {
  for (const [path] of STATIC_PAGES) assert.ok(!/lebanon|beirut/i.test(path), `${path} targets a place`);
});

test("every static page has a file in public/ and canonicalises to itself", () => {
  for (const [path] of STATIC_PAGES) {
    const file = join(ROOT, "public", path === "/" ? "index.html" : `${path.slice(1)}.html`);
    assert.ok(existsSync(file), `${path} has no ${file}`);
    // legal pages are hand-written utility pages; the marketing pages must canonicalise to their own URL
    if (["/privacy", "/terms", "/delete-account"].includes(path)) continue;
    const canonical = /<link rel="canonical" href="([^"]+)"/.exec(readFileSync(file, "utf8"));
    assert.ok(canonical, `${path} has no canonical`);
    assert.equal(canonical[1], path === "/" ? "https://pro-host.tech/" : `https://pro-host.tech${path}`, `${path} canonical`);
  }
});

test("retired Lebanon/Beirut slugs are redirected permanently and no longer exist as pages", () => {
  const redirects: Array<{ source: string; destination: string; type: number }> =
    JSON.parse(readFileSync(join(ROOT, "firebase.json"), "utf8")).hosting.redirects;
  const legacy: Record<string, string> = {
    "/clinic-room-rental-lebanon": "/clinic-room-rental",
    "/therapy-room-rental-beirut": "/therapy-room-rental",
    "/meeting-room-rental-beirut": "/meeting-room-rental",
    "/training-room-rental-lebanon": "/training-room-rental",
    "/studio-rental-beirut": "/studio-rental",
    "/office-space-by-the-hour-lebanon": "/office-space-by-the-hour",
  };
  const live = new Set(STATIC_PAGES.map(([p]) => p));
  for (const [from, to] of Object.entries(legacy)) {
    const r = redirects.find((x) => x.source === from);
    assert.ok(r, `no redirect for ${from}`);
    assert.equal(r.destination, to);
    assert.equal(r.type, 301);
    assert.ok(live.has(to), `${to} is not in the sitemap`);
    assert.ok(!live.has(from), `${from} is still in the sitemap`);
    assert.ok(!existsSync(join(ROOT, "public", `${from.slice(1)}.html`)), `${from}.html still exists`);
  }
});

test("every indexable page has a complete share card whose image exists", () => {
  const pub = join(ROOT, "public");
  for (const [p] of STATIC_PAGES) {
    const file = join(pub, `${p === "/" ? "index" : p.slice(1)}.html`);
    const html = readFileSync(file, "utf8");
    const img = /property="og:image" content="https:\/\/pro-host\.tech(\/[^"]+)"/.exec(html);
    assert.ok(img, `${p} has no og:image`);
    assert.ok(existsSync(join(pub, img![1])), `${p}: ${img![1]} is missing`);
    for (const t of ["og:title", "og:description", "og:url", "twitter:card"]) assert.ok(html.includes(t), `${p} lacks ${t}`);
    const title = /<title>(.*?)<\/title>/s.exec(html)![1];
    assert.ok(title.length <= 66, `${p} title is ${title.length} chars`);
    if (!["/privacy", "/terms", "/delete-account"].includes(p)) assert.ok(html.includes("og:image:alt") && html.includes("twitter:title"), `${p} lacks image alt / twitter:title`);
  }
});
