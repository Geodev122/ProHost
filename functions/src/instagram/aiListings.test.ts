import { test } from "node:test";
import assert from "node:assert/strict";
import { ListingRow, catalogOverview, cleanCriteria, formatMatches, listingButtons, listingUrl, searchListings, toListingRow } from "./aiListings";

const NOW = Date.UTC(2026, 9, 24);
const clinic = toListingRow("SPC-1", {
  title: "Achrafieh Medical Suite", spaceCategoryName: "Polyclinic", country: "Lebanon", city: "Beirut", district: "Achrafieh", isVerified: true,
  ownerName: "Never shown", ownerPhone: "+961000", ownerEmail: "x@y.z", essentialFacilities: ["High-Speed Fiber Wi-Fi", "Receptionist"],
  favoriteCount: 4, avatarEngagementViews: 120, avatarInquiryClicks: 6, createdAtMillis: NOW - 86_400_000,
  subdivisions: [
    { id: "SUB-a", displayCode: "D-AAAAAA", name: "Exam room 1", type: "ROOMS", capacity: 2, pricing: { strategyType: "HOURLY", hourly: { cellPrices: { "Mon|9": 25 } } } },
    { id: "SUB-b", name: "Board room", type: "CONFERENCE_ROOM", capacity: 10, pricing: { strategyType: "DAY_BASED", dayBased: { distribution: { Mon: { price: 150 } } } } },
  ],
}, 3);
const office = toListingRow("SPC-2", {
  title: "Jounieh Seaside Offices", spaceCategoryName: "Private office", country: "Lebanon", city: "Jounieh",
  subdivisions: [{ id: "SUB-c", name: "Office A", type: "OFFICE", capacity: 4, pricing: { strategyType: "MONTHLY", monthly: { rateUsd: 450 } } }],
}, 0);
const rows: ListingRow[] = [clinic, office];

test("rows carry details and analytics, never the owner's contact", () => {
  assert.equal(clinic.city, "Beirut"); assert.equal(clinic.area, "Achrafieh"); assert.equal(clinic.verified, true);
  assert.deepEqual(clinic.rooms.map((r) => [r.type, r.offered, r.price, r.priceUnit]), [["Room", "hour", 25, "hour"], ["Meeting room", "day", 150, "day"]]);
  assert.deepEqual([clinic.views, clinic.inquiries, clinic.favorites, clinic.bookings], [120, 6, 4, 3]);
  assert.ok(!JSON.stringify(clinic).includes("Never shown") && !JSON.stringify(clinic).includes("+961000"));
  const whole = toListingRow("SPC-3", { title: "Studio", pricing: { strategyType: "HOURLY", hourly: { cellPrices: { a: 15 } } } });
  assert.deepEqual([whole.rooms.length, whole.rooms[0].price, whole.rooms[0].offered], [1, 15, "hour"]);
  // an undivided listing (one division saved as the whole place) keeps its capacity on the listing
  const sized = toListingRow("SPC-5", { title: "Studio", capacity: 6, pricing: { strategyType: "HOURLY", hourly: { cellPrices: { a: 15 } } } });
  assert.equal(sized.rooms[0].capacity, 6);
  assert.equal(searchListings([sized], { attendees: 8 }, NOW).length, 0, "too small");
});

test("criteria are validated and merged over what was known", () => {
  const c = cleanCriteria({ kind: "Clinic", city: " Beirut ", unit: "hour", attendees: "x", maxPrice: 30, features: ["wifi"], junk: 1 }, { attendees: 2 });
  assert.deepEqual(c, { attendees: 2, kind: "clinic", city: "Beirut", unit: "hour", maxPrice: 30, features: ["wifi"] });
  assert.deepEqual(cleanCriteria({ kind: "castle", unit: "week" }), {});
});

test("search: place, kind, unit, capacity and budget filter; the best room is chosen", () => {
  const m = searchListings(rows, { kind: "clinic", city: "beirut", unit: "hour", attendees: 2, maxPrice: 30 }, NOW);
  assert.equal(m.length, 1); assert.equal(m[0].listing.id, "SPC-1"); assert.equal(m[0].room.id, "SUB-a");
  assert.equal(searchListings(rows, { kind: "clinic", city: "Beirut", maxPrice: 20, unit: "hour" }, NOW).length, 0, "over budget");
  assert.equal(searchListings(rows, { kind: "meeting", attendees: 12 }, NOW).length, 0, "too small");
  assert.equal(searchListings(rows, { kind: "meeting", attendees: 8 }, NOW)[0].room.id, "SUB-b");
  assert.equal(searchListings(rows, { kind: "office", unit: "month" }, NOW)[0].listing.id, "SPC-2");
  assert.equal(searchListings(rows, { city: "Tripoli" }, NOW).length, 0);
  // a listing whose address was filled in Arabic is still found by its English city copy
  const arabic = toListingRow("SPC-4", { title: "عيادة", country: "Lebanon", city: "طرابلس", cityEn: "Tripoli", pricing: { strategyType: "HOURLY", hourly: { cellPrices: { a: 15 } } } });
  assert.equal(searchListings([arabic], { city: "Tripoli" }, NOW)[0].listing.id, "SPC-4");
  assert.equal(searchListings(rows, { kind: "office", exclude: ["SPC-2"] }, NOW).length, 0, "already sent");
  // nothing specific: both, the verified and popular one first
  assert.deepEqual(searchListings(rows, {}, NOW).map((x) => x.listing.id), ["SPC-1", "SPC-2"]);
});

test("results block, links and buttons only point at real results", () => {
  const m = searchListings(rows, { kind: "clinic", city: "Beirut" }, NOW);
  const block = formatMatches(m, { kind: "clinic", city: "Beirut" });
  assert.ok(/id=SPC-1/.test(block) && /from \$25 per hour/.test(block) && /3 bookings/.test(block) && /verified/.test(block));
  assert.ok(/NO MATCH/.test(formatMatches([], { kind: "studio" })));
  assert.equal(listingUrl("SPC-1", "SUB-a"), "https://pro-host.tech/listing/SPC-1?room=SUB-a&utm_source=instagram&utm_medium=dm&utm_campaign=ai_agent");
  const b = listingButtons(m, ["SPC-404", "SPC-1"]);
  assert.equal(b.length, 1); assert.ok(b[0].url.startsWith("https://pro-host.tech/listing/SPC-1?room=SUB-a"));
  assert.ok(b[0].label.length <= 20); assert.equal(b[0].label, "Achrafieh Medical S…");
  assert.equal(listingButtons(m, []).length, 1, "the model named none: the best match");
  assert.deepEqual(listingButtons([], ["SPC-1"]), []);
});

test("overview tells the agent what exists", () => {
  const o = catalogOverview(rows);
  assert.ok(/2 published listings, 3 bookable rooms/.test(o) && /Beirut, Lebanon \(1\)/.test(o) && /hour \(1\)/.test(o));
  assert.ok(/none published/.test(catalogOverview([])));
});
