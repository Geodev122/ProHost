import { test } from "node:test";
import assert from "node:assert/strict";
import { usedCampaignCode } from "./promoCampaign";

test("a redemption counts only when the purchase used the campaign code (case-insensitive)", () => {
  assert.equal(usedCampaignCode("PROHOST2026", "PROHOST2026", 1), true);
  assert.equal(usedCampaignCode("PROHOST2026", "prohost2026", 1), true);
  assert.equal(usedCampaignCode("PROHOST2026", " PROHOST2026 ", 0), true);
  assert.equal(usedCampaignCode("PROHOST2026", "OTHERCODE", 1), false);
  assert.equal(usedCampaignCode("PROHOST2026", null, null), false);
  assert.equal(usedCampaignCode("", "PROHOST2026", 1), false);
  assert.equal(usedCampaignCode("PROHOST2026", "PROHOST2026", 7), false);
});
