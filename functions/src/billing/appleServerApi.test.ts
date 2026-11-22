import { test } from "node:test";
import assert from "node:assert/strict";
import { Environment } from "@apple/app-store-server-library";
import { environmentOf } from "./appleServerApi";

test("TestFlight subscriptions are re-read from the sandbox, everything else from production", () => {
  assert.equal(environmentOf("Sandbox"), Environment.SANDBOX);
  assert.equal(environmentOf("sandbox"), Environment.SANDBOX);
  assert.equal(environmentOf("Production"), Environment.PRODUCTION);
  assert.equal(environmentOf(null), Environment.PRODUCTION);
  assert.equal(environmentOf(undefined), Environment.PRODUCTION);
});
