import { test } from "node:test";
import assert from "node:assert/strict";
import { STATIC_PAGES, buildSitemap, isPublicListing } from "./sitemapXml";

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
  assert.ok(xml.includes("<loc>https://pro-host.tech/</loc><lastmod>2026-10-11</lastmod>"));
  assert.ok(xml.includes("<loc>https://pro-host.tech/listing/abc123</loc><lastmod>2026-10-01</lastmod>"));
  assert.ok(!xml.includes("bad/id") && !xml.includes("x<y"));
});

test("listing without a timestamp has no lastmod", () => {
  const xml = buildSitemap([{ id: "a1" }], "2026-10-11");
  assert.ok(xml.includes("<loc>https://pro-host.tech/listing/a1</loc><changefreq>"));
});
