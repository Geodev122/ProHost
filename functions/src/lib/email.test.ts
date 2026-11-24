import { test } from "node:test";
import * as assert from "node:assert/strict";
import { isUndeliverableAddress } from "./email";

test("reserved test domains are never queued (the mail provider rejects them with 550)", () => {
  for (const e of ["a@example.com", "b@EXAMPLE.org", "c@example.net", "d@foo.test", "e@x.invalid", "f@localhost", "g@site.example", "nope", ""]) {
    assert.equal(isUndeliverableAddress(e), true, e);
  }
  for (const e of ["owner@pro-host.tech", "x@gmail.com", "y@privaterelay.appleid.com", "z@example.com.lb", "w@testing.io"]) {
    assert.equal(isUndeliverableAddress(e), false, e);
  }
});
