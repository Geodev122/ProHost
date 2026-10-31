import { test } from "node:test";
import * as assert from "node:assert/strict";
import { emailVerifiedByToken, emailVerifiedByUserRecord } from "./emailVerifiedRule";

test("email link / code sign-ins (email_verified) count as verified", () => {
  assert.equal(emailVerifiedByToken({ email: "a@b.c", email_verified: true, firebase: { sign_in_provider: "password" } }), true);
  assert.equal(emailVerifiedByToken({ email: "a@b.c", email_verified: true, firebase: { sign_in_provider: "custom" } }), true);
});

test("Google sign-in counts as verified even without the claim", () => {
  assert.equal(emailVerifiedByToken({ email: "a@gmail.com", firebase: { sign_in_provider: "google.com" } }), true);
});

test("no email, or an unverified password/phone account, is not verified", () => {
  assert.equal(emailVerifiedByToken({ firebase: { sign_in_provider: "google.com" } }), false);
  assert.equal(emailVerifiedByToken({ email: "a@b.c", email_verified: false, firebase: { sign_in_provider: "password" } }), false);
  assert.equal(emailVerifiedByToken({ email: "a@b.c", firebase: { sign_in_provider: "phone" } }), false);
});

test("Auth user records follow the same rule", () => {
  assert.equal(emailVerifiedByUserRecord({ email: "a@b.c", emailVerified: true, providerData: [] }), true);
  assert.equal(emailVerifiedByUserRecord({ email: "a@b.c", emailVerified: false, providerData: [{ providerId: "google.com" }] }), true);
  assert.equal(emailVerifiedByUserRecord({ email: "a@b.c", emailVerified: false, providerData: [{ providerId: "password" }] }), false);
  assert.equal(emailVerifiedByUserRecord({ emailVerified: true, providerData: [] }), false);
});
