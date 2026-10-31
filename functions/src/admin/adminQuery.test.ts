import { test } from "node:test";
import * as assert from "node:assert/strict";
import { classifyAdminQuery, normalizeSearchName } from "./adminQuery";

test("display codes of every kind, with or without the dash, any case", () => {
  assert.deepEqual(classifyAdminQuery("U-7K3Q9P"), { type: "code", prefix: "U", code: "U-7K3Q9P" });
  assert.deepEqual(classifyAdminQuery("l7k3q9p"), { type: "code", prefix: "L", code: "L-7K3Q9P" });
  assert.deepEqual(classifyAdminQuery(" d-ab12cd "), { type: "code", prefix: "D", code: "D-AB12CD" });
  assert.deepEqual(classifyAdminQuery("B-0000ZZ"), { type: "code", prefix: "B", code: "B-0000ZZ" });
});

test("emails are lower-cased", () => {
  assert.deepEqual(classifyAdminQuery("Geo.Name@Example.com"), { type: "email", email: "geo.name@example.com" });
});

test("a single token is tried as a name prefix and as an id", () => {
  assert.deepEqual(classifyAdminQuery("aBcDeF1234567890xyzUID"), {
    type: "text", prefix: "abcdef1234567890xyzuid", maybeId: "aBcDeF1234567890xyzUID",
  });
});

test("a multi-word name is a name prefix only", () => {
  assert.deepEqual(classifyAdminQuery("  Rita   Khoury "), { type: "text", prefix: "rita khoury", maybeId: null });
});

test("empty input and search-name normalisation", () => {
  assert.deepEqual(classifyAdminQuery("   "), { type: "empty" });
  assert.deepEqual(classifyAdminQuery(undefined), { type: "empty" });
  assert.equal(normalizeSearchName("  Beirut  Dental   Hub "), "beirut dental hub");
  assert.equal(normalizeSearchName(42), "");
});
