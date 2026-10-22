import { test } from "node:test";
import * as assert from "node:assert/strict";
import { createHash } from "crypto";
import { inviteTokenValid, inviteUrl, needsPasswordSetup } from "./passwordReset";
import { passwordResetTemplate, passwordSetupInviteTemplate } from "../lib/emailTemplates";
import { legacyEmailSignInFrom, LEGACY_EMAIL_RETIRED_MESSAGE } from "../lib/authConfig";

const facts = (providerIds: string[], extra: Partial<{ email: string; disabled: boolean; hasPasswordHash: boolean }> = {}) =>
  ({ email: "a@b.com", disabled: false, hasPasswordHash: false, providerIds, ...extra });

test("email-code accounts (no provider) and magic-link accounts (password, no hash) get an invite", () => {
  assert.equal(needsPasswordSetup(facts([])), "email_code");
  assert.equal(needsPasswordSetup(facts(["password"])), "email_link");
});

test("Google, real password, phone, disabled, no email and Play test robots get nothing", () => {
  assert.equal(needsPasswordSetup(facts(["google.com"])), null);
  assert.equal(needsPasswordSetup(facts(["password", "google.com"])), null);
  assert.equal(needsPasswordSetup(facts(["password"], { hasPasswordHash: true })), null);
  assert.equal(needsPasswordSetup(facts(["phone"])), null);
  assert.equal(needsPasswordSetup(facts([], { disabled: true })), null);
  assert.equal(needsPasswordSetup(facts([], { email: "" })), null);
  assert.equal(needsPasswordSetup(facts([], { email: "robot@cloudtestlabaccounts.com" })), null);
});

test("invite tokens: only the matching, unexpired token opens the link", () => {
  const token = "tok-123";
  const doc = { tokenHash: createHash("sha256").update(token).digest("hex"), expiresAt: 2_000 };
  assert.equal(inviteTokenValid(doc, token, 1_000), true);
  assert.equal(inviteTokenValid(doc, "tok-124", 1_000), false);
  assert.equal(inviteTokenValid(doc, token, 2_001), false);
  assert.equal(inviteTokenValid(undefined, token, 1_000), false);
  assert.equal(inviteTokenValid({ ...doc, expiresAt: undefined }, token, 1_000), false);
  assert.equal(inviteTokenValid(doc, "", 1_000), false);
});

test("invite URL carries the uid and token, URL-encoded", () => {
  assert.equal(inviteUrl("u/1", "a+b", "https://fn"), "https://fn/passwordSetupLink?u=u%2F1&t=a%2Bb");
});

test("reset and invite emails escape the address and the link and are bilingual", () => {
  const evil = "<x>@b.com";
  const reset = passwordResetTemplate(evil, "https://l?a=1&b=2");
  assert.ok(!reset.html.includes("<x>"));
  assert.ok(reset.html.includes("&lt;x&gt;@b.com"));
  assert.ok(reset.html.includes('href="https://l?a=1&amp;b=2"'));
  assert.ok(reset.html.includes('dir="rtl"'));
  const invite = passwordSetupInviteTemplate("a@b.com", "https://fn/passwordSetupLink?u=1&t=2");
  assert.match(invite.subject, /password/i);
  assert.ok(invite.html.includes("30 days") && invite.html.includes("Forgot password"));
});

test("legacy email sign-in switch: missing or true = on, only an explicit false retires it", () => {
  assert.equal(legacyEmailSignInFrom(undefined), true);
  assert.equal(legacyEmailSignInFrom({}), true);
  assert.equal(legacyEmailSignInFrom({ legacyEmailSignIn: true }), true);
  assert.equal(legacyEmailSignInFrom({ legacyEmailSignIn: false }), false);
  assert.match(LEGACY_EMAIL_RETIRED_MESSAGE, /Forgot password/);
});
