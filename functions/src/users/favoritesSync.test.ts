import { test } from "node:test";
import assert from "node:assert/strict";
import { MONTHS_KEPT, applyFavorite, favoriteDeltas, monthKey, recountFavorites } from "./favoritesSync";

const OCT = Date.UTC(2026, 9, 24);

test("deltas are set differences: re-saving the same listing changes nothing", () => {
  assert.deepEqual(favoriteDeltas(["a", "b"], ["b", "a"]), { added: [], removed: [] });
  assert.deepEqual(favoriteDeltas(["a"], ["a", "b", "b"]), { added: ["b"], removed: [] });
  assert.deepEqual(favoriteDeltas(["a", "b"], ["b"]), { added: [], removed: ["a"] });
  assert.deepEqual(favoriteDeltas(undefined, ["x", 3, ""]), { added: ["x"], removed: [] });
});

test("a save counts once, by month; the owner's own save never counts", () => {
  const listing = { ownerId: "host", favoriteCount: 2, favoritesByMonth: { "202609": 4 } };
  const out = applyFavorite(listing, "guest", 1, OCT, "e1:1")!;
  assert.equal(out.favoriteCount, 3);
  assert.deepEqual(out.favoritesByMonth, { "202609": 4, "202610": 1 });
  assert.equal(applyFavorite(listing, "host", 1, OCT, "e2:1"), null);
  assert.equal(applyFavorite(undefined, "guest", 1, OCT, "e3:1"), null, "deleted listing");
});

test("a redelivered event is skipped", () => {
  const once = applyFavorite({ ownerId: "h", favoriteCount: 0 }, "g", 1, OCT, "e1:1")!;
  assert.equal(applyFavorite({ ownerId: "h", ...once }, "g", 1, OCT, "e1:1"), null);
});

test("an un-save never goes below zero and is recorded as an un-save", () => {
  const out = applyFavorite({ ownerId: "h", favoriteCount: 0 }, "g", -1, OCT, "e1:-1")!;
  assert.equal(out.favoriteCount, 0);
  assert.deepEqual(out.unsavesByMonth, { "202610": 1 });
  assert.equal(out.favoritesByMonth, undefined);
});

test("only the last months are kept", () => {
  const many = Object.fromEntries(Array.from({ length: 20 }, (_, i) => [`2025${String(i % 12 + 1).padStart(2, "0")}`, 1]));
  const out = applyFavorite({ ownerId: "h", favoritesByMonth: many }, "g", 1, OCT, "e:1")!;
  const keys = Object.keys(out.favoritesByMonth as object);
  assert.equal(keys.length, Math.min(MONTHS_KEPT, keys.length));
  assert.ok(keys.includes("202610"));
});

test("recount excludes the owner and duplicates", () => {
  assert.equal(recountFavorites(["a", "b", "host", "a"], "host"), 2);
  assert.equal(monthKey(Date.UTC(2026, 0, 1)), "202601");
});
