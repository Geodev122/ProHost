import { test } from "node:test";
import assert from "node:assert/strict";
import { AppAdapter, ListingRow, OfferBasePlan, appAdapter } from "../instagram/appAdapter";
import { LiveBasePlan } from "../billing/playCatalogLive";
import { toListingRow } from "./listingRows";
import { formatOffers } from "../instagram/aiOffers";
import { searchListings } from "../instagram/aiListings";

/**
 * AppAdapter contract (migration plan P9 step 5, docs/migration/AGENT_ADAPTER.md). The AI agent only knows the neutral types in
 * instagram/appAdapter.ts; these tests pin that ProHost's data fits them, so replacing the in-process implementation with an
 * HTTPS client later cannot change what the agent sees.
 */

const ROW_KEYS = ["id", "code", "title", "category", "country", "city", "cityEn", "area", "verified", "rooms", "features", "views", "inquiries", "favorites", "bookings", "createdAt"];
const ROOM_KEYS = ["id", "code", "name", "type", "capacity", "offered", "price", "priceUnit", "amenities"];

test("a listing row has exactly the contract's fields, and never owner contact data", () => {
  const row: ListingRow = toListingRow("L1", {
    title: "Quiet clinic room", spaceType: "POLYCLINIC", city: "Beirut", country: "Lebanon", ownerId: "u1", ownerEmail: "o@x.com", phone: "+961",
    subdivisions: [{ id: "r1", name: "Room 1", type: "ROOMS", capacity: 3, displayCode: "D-ABC123", pricing: { strategyType: "HOURLY", hourly: { cellPrices: { a: 12 } } } }],
  });
  assert.deepEqual(Object.keys(row).sort(), [...ROW_KEYS].sort());
  assert.deepEqual(Object.keys(row.rooms[0]).sort(), [...ROOM_KEYS].sort());
  const json = JSON.stringify(row);
  assert.ok(!json.includes("o@x.com") && !json.includes("+961") && !json.includes("u1"), "no owner id, email or phone reaches the agent");
});

test("rows from the host app feed the agent's pure matching unchanged", () => {
  const rows = [toListingRow("L1", { title: "Therapy room", spaceType: "POLYCLINIC", city: "Beirut", country: "Lebanon", pricing: { strategyType: "HOURLY", hourly: { cellPrices: { a: 10 } } } })];
  const found = searchListings(rows, { kind: "therapy" });
  assert.ok(Array.isArray(found));
});

test("Play's live catalogue is assignable to the agent's neutral offer types (compile-time) and formats the same", () => {
  const live: LiveBasePlan = {
    basePlanId: "m", state: "ACTIVE", period: "P1M", prices: { US: "8.99 USD", LB: "8.99 USD" }, availableIn: { US: true, LB: true }, regionCount: 2,
    offers: [{ offerId: "o", state: "ACTIVE", eligibility: "new customers: never had this subscription", tags: [], availableIn: { US: true, LB: true }, regionCount: 2, otherRegions: false,
      phases: [{ duration: "P1M", recurrenceCount: 1, pricing: { US: "free", LB: "free" } }] }],
  };
  const neutral: OfferBasePlan = live; // the assignment is the contract check
  assert.match(formatOffers([neutral]), /Monthly plan offer: 1 month free, then regular price 8.99 USD per month/);
});

test("the registry refuses to run without a host adapter and returns the registered one", async () => {
  assert.throws(() => appAdapter(), /No AppAdapter registered/);
  const { setAppAdapter } = await import("../instagram/appAdapter");
  const fake: AppAdapter = {
    readOffers: async () => ({ basePlans: [] }), loadListings: async () => [],
    lookupInsight: async () => ({ found: false, listings: 0, published: 0, drafts: 0, rentals: 0 }),
    sendAdminMail: async () => undefined, escapeHtml: (t) => t,
  };
  setAppAdapter(fake);
  assert.equal(appAdapter(), fake);
});
