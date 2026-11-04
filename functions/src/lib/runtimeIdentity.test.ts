import { test } from "node:test";
import assert from "node:assert/strict";
import { runAs, runtimeServiceAccount } from "./runtimeIdentity";

test("a group maps to its dedicated service account", () => {
  assert.equal(runtimeServiceAccount("public"), "run-public@prohost-f766f.iam.gserviceaccount.com");
  assert.deepEqual(runAs("billing"), { serviceAccount: "run-billing@prohost-f766f.iam.gserviceaccount.com" });
});
