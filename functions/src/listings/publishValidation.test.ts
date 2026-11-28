import { test } from "node:test";
import assert from "node:assert/strict";
import { validateListingForPublish } from "./publishValidation";

const base = {
  lat: 33.89, lng: 35.5, imageUrls: ["https://x/a.jpg"], subdivisions: [],
  pricing: { strategyType: "MONTHLY", monthly: { rateUsd: 300 } },
} as Parameters<typeof validateListingForPublish>[0];

test("a dropped pin publishes", () => {
  assert.deepEqual(validateListingForPublish(base), []);
  assert.deepEqual(validateListingForPublish({ ...base, locationPinned: true } as typeof base), []);
});

test("a draft autosaved before the pin was dropped can't publish on its guessed position", () => {
  assert.ok(validateListingForPublish({ ...base, locationPinned: false } as typeof base).includes("Missing pinned map location."));
});
