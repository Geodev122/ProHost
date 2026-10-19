import { test } from "node:test";
import assert from "node:assert/strict";
import { ISO_CODES, countryCode, listingPlace } from "./listingLocation";

test("legacy listings (no stored country) inside Lebanon keep their Lebanese place text", () => {
  const p = listingPlace({ district: "Jounieh", governorate: "MOUNT_LEBANON", lat: 33.98, lng: 35.62 });
  assert.equal(p.country, "Lebanon");
  assert.equal(p.countryCode, "LB");
  assert.equal(p.text, "Jounieh, Mount Lebanon");
  assert.equal(p.locality, "Jounieh");
  assert.equal(p.region, "Mount Lebanon");
});

test("a Lebanese listing with a stored country and city is unchanged", () => {
  const p = listingPlace({ country: "Lebanon", city: "Beirut", district: "Achrafieh", governorate: "BEIRUT", lat: 33.89, lng: 35.52 });
  assert.equal(p.text, "Achrafieh, Beirut");
  assert.equal(p.countryCode, "LB");
});

test("a listing elsewhere shows its own country and city, never a Lebanese governorate", () => {
  const p = listingPlace({ country: "France", city: "Paris", district: "Le Marais", governorate: "BEIRUT", lat: 48.857, lng: 2.36 });
  assert.equal(p.country, "France");
  assert.equal(p.countryCode, "FR");
  assert.equal(p.text, "Le Marais, Paris");
  assert.equal(p.region, "");
});

test("an explicit country beats coordinates that fall inside Lebanon's bounding box", () => {
  const p = listingPlace({ country: "Syria", city: "Damascus", district: "Mezzeh", governorate: "BEIRUT", lat: 33.51, lng: 36.29 });
  assert.equal(p.countryCode, "SY");
  assert.equal(p.text, "Mezzeh, Damascus");
});

test("an unresolvable stored country name falls back to the coordinates", () => {
  assert.equal(listingPlace({ country: "لبنان", district: "الحمرا", governorate: "BEIRUT", lat: 33.896, lng: 35.48 }).countryCode, "LB");
  const p = listingPlace({ country: "Atlantis", city: "Poseidonis", district: "", lat: 10, lng: 10 });
  assert.equal(p.countryCode, undefined);
  assert.equal(p.text, "Poseidonis");
});

test("no country, no coordinates in Lebanon: nothing is invented", () => {
  const p = listingPlace({ district: "", lat: 0, lng: 0 });
  assert.equal(p.country, "");
  assert.equal(p.countryCode, undefined);
  assert.equal(p.text, "");
});

test("district equal to the city is not repeated", () => {
  assert.equal(listingPlace({ country: "Spain", city: "Madrid", district: "madrid" }).text, "madrid");
});

test("country names resolve to ISO codes", () => {
  assert.equal(countryCode("United States"), "US");
  assert.equal(countryCode("United Arab Emirates"), "AE");
  assert.equal(countryCode("Saudi Arabia"), "SA");
  assert.equal(countryCode("lb"), "LB");
  assert.equal(countryCode(""), undefined);
});

test("the ISO code table is complete and every code has an English name", () => {
  const codes = ISO_CODES.split(" ");
  assert.equal(codes.length, 249);
  assert.equal(new Set(codes).size, 249);
  const names = new Intl.DisplayNames(["en"], { type: "region" });
  for (const c of codes) assert.notEqual(names.of(c), c, `${c} has no English name`);
  assert.equal(countryCode("France"), "FR");
  assert.equal(countryCode("Myanmar (Burma)"), "MM");
  assert.equal(countryCode("United Kingdom"), "GB");
});
