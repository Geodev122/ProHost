import { test } from "node:test";
import * as assert from "node:assert/strict";
import { savedListingOf, savedRoomOf } from "./savedKeys";

test("a room key names its listing and room; a whole-place key only the listing", () => {
  assert.equal(savedListingOf("SPC-1#sub-2"), "SPC-1");
  assert.equal(savedRoomOf("SPC-1#sub-2"), "sub-2");
  assert.equal(savedListingOf("SPC-1"), "SPC-1");
  assert.equal(savedRoomOf("SPC-1"), null);
  assert.equal(savedRoomOf("SPC-1#"), null);
});
