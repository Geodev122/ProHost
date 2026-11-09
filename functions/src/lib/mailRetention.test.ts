import { test } from "node:test";
import assert from "node:assert/strict";
import { MAIL_RETENTION_DAYS, mailExpireAt } from "./email";

test("a queued mail document expires 30 days after it was queued", () => {
  const now = Date.UTC(2026, 10, 9, 12, 0, 0);
  assert.equal(MAIL_RETENTION_DAYS, 30);
  assert.equal(mailExpireAt(now).toISOString(), "2026-12-09T12:00:00.000Z");
});
