import { test } from "node:test";
import assert from "node:assert/strict";
import { NO_INSIGHT, formatInsight, insightTag } from "./aiAccountInsight";

test("an unmatched or admin email reads exactly like a new person (no account probing)", () => {
  assert.match(formatInsight(NO_INSIGHT), /no ProHost account is linked/);
  assert.match(formatInsight({ ...NO_INSIGHT, found: true, role: "ADMIN" }), /no ProHost account is linked/);
  assert.equal(insightTag(NO_INSIGHT), "no-account");
});

test("a found account becomes guidance, flagged private and not to be repeated", () => {
  const i = { found: true, role: "SPECIALIST", premiumActive: false, listings: 0, published: 0, drafts: 0, rentals: 2, lastRentalStatus: "accepted", hasPhoto: false, hasWhatsApp: true, hasLocation: false, daysSinceSignIn: 3 };
  const t = formatInsight(i);
  assert.match(t, /NEVER repeat/); assert.match(t, /Specialist/); assert.match(t, /no Premium/); assert.match(t, /profile photo/); assert.match(t, /country and city/); assert.match(t, /rents rooms/);
  assert.equal(insightTag(i), "SPECIALIST,free,no-listing,tenant");
  assert.match(formatInsight({ ...i, role: "PRO_HOST", premiumActive: true, listings: 2, published: 1, drafts: 1 }), /Premium active[\s\S]*draft waiting/);
});
