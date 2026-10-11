/**
 * pro-host.tech/sitemap.xml (hosting rewrite): the indexable static pages plus every publicly browsable listing page
 * (/listing/{id}, served by listingShareLanding). Cached for an hour at the CDN. If Firestore fails, the static
 * pages are still returned so crawlers never get an error.
 */
import * as logger from "firebase-functions/logger";
import { onRequest } from "firebase-functions/v2/https";
import { getFirestore } from "firebase-admin/firestore";
import "../lib/admin";
import { buildSitemap, isPublicListing } from "./sitemapXml";

export const siteSitemap = onRequest({ memory: "256MiB", timeoutSeconds: 60 }, async (_req, res) => {
  let listings: Array<{ id: string; updatedAt?: unknown }> = [];
  try {
    const snap = await getFirestore().collection("workspace_listings").where("status", "==", "ACTIVE")
      .select("status", "ownerId", "isOwnerSuspended", "isOwnerPackageLapsed", "isDemo", "updatedAt").limit(5000).get();
    listings = snap.docs.filter((d) => isPublicListing(d.data())).map((d) => ({ id: d.id, updatedAt: d.get("updatedAt") }));
  } catch (e) {
    logger.warn("sitemap_listings_failed", { error: String(e) });
  }
  res.set("Content-Type", "application/xml; charset=utf-8");
  res.set("Cache-Control", "public, max-age=600, s-maxage=3600");
  res.status(200).send(buildSitemap(listings));
});
