import { test } from "node:test";
import assert from "node:assert/strict";
import { parseConfig } from "./config";
import { blankProfile, PROHOST_PROFILE } from "./brands";
import { brandForVerifyToken, routeEntries } from "./routing";
import { sign } from "./signature";

const prohost = parseConfig({ igUserId: "1001", appSecret: "pro-secret", verifyToken: "pro-verify" }, PROHOST_PROFILE);
const acme = parseConfig({ igUserId: "2002", appSecret: "acme-secret", verifyToken: "acme-verify" }, blankProfile("acme", "Acme"),
  { phoneNumberId: "3003", appSecret: "acme-wa-secret", verifyToken: "acme-wa-verify" });

test("the handshake finds the brand by its verify token", () => {
  assert.equal(brandForVerifyToken([prohost, acme], "acme-verify")?.brandId, "acme");
  assert.equal(brandForVerifyToken([prohost, acme], "pro-verify")?.brandId, "prohost");
  assert.equal(brandForVerifyToken([prohost, acme], "nope"), undefined);
  assert.equal(brandForVerifyToken([prohost, acme], ""), undefined);
  assert.equal(brandForVerifyToken([prohost, acme], "acme-wa-verify", "whatsapp")?.brandId, "acme");
});

test("each entry goes to the brand owning the account and must be signed with that brand's secret", () => {
  const entries = [{ id: "1001" }, { id: "2002" }, { id: "9999" }];
  const body = JSON.stringify({ entry: entries });
  const signedByAcme = sign(body, "acme-secret");
  const r = routeEntries([prohost, acme], entries, body, signedByAcme);
  assert.deepEqual(r.work.map((w) => w.config.brandId), ["acme"]);
  assert.deepEqual(r.unowned, ["9999"]);
  assert.deepEqual(r.badSignature, ["prohost"]);
});

test("one brand's secret never opens another brand's events", () => {
  const entries = [{ id: "2002" }];
  const body = JSON.stringify({ entry: entries });
  const r = routeEntries([prohost, acme], entries, body, sign(body, "pro-secret"));
  assert.equal(r.work.length, 0);
  assert.deepEqual(r.badSignature, ["acme"]);
});

test("WhatsApp entries route by phone number id and use the WhatsApp app secret", () => {
  const entries = [{ id: "waba", phone: "3003" }];
  const body = JSON.stringify({ entry: entries });
  const r = routeEntries([prohost, acme], entries, body, sign(body, "acme-wa-secret"), "whatsapp", (e) => e.phone);
  assert.deepEqual(r.work.map((w) => w.config.brandId), ["acme"]);
  const bad = routeEntries([prohost, acme], entries, body, sign(body, "acme-secret"), "whatsapp", (e) => e.phone);
  assert.equal(bad.work.length, 0);
});
