// Email + password sign-in against the Auth + Functions + Firestore emulators: sign-up and
// registration (role, profile, verification email), password sign-in, "Forgot password"
// (branded reset mail → Firebase reset → new password works, unknown addresses answer the same),
// the one-off "set your password" invite for accounts from the retired email code / magic link,
// and the retirement switch for the old callables.
//
//   cd tests/e2e && npm test
import { test } from "node:test";
import assert from "node:assert/strict";
import { createRequire } from "node:module";

const require = createRequire(new URL("../../functions/package.json", import.meta.url));
const { initializeApp, getApps } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getAuth } = require("firebase-admin/auth");

const PROJECT = "demo-prohost";
if (!getApps().length) initializeApp({ projectId: PROJECT });
const db = getFirestore();
const auth = getAuth();
const FUNCTIONS = `http://127.0.0.1:5001/${PROJECT}/europe-west1`;
const AUTH = `http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}`;
const IDT = `${AUTH}/identitytoolkit.googleapis.com/v1`;

async function rest(path, body) {
  const res = await fetch(`${IDT}/${path}?key=e2e`, {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body),
  });
  return { status: res.status, body: await res.json() };
}
const signUp = (email, password) => rest("accounts:signUp", { email, password, returnSecureToken: true });
const signIn = (email, password) => rest("accounts:signInWithPassword", { email, password, returnSecureToken: true });

async function callable(name, data, token) {
  const res = await fetch(`${FUNCTIONS}/${name}`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify({ data }),
  });
  return res.json();
}

async function waitFor(what, fn, timeoutMs = 30000) {
  const end = Date.now() + timeoutMs;
  while (Date.now() < end) {
    const v = await fn();
    if (v) return v;
    await new Promise((r) => setTimeout(r, 300));
  }
  assert.fail(`timed out waiting for ${what}`);
}
async function mailsTo(email) {
  const s = await db.collection("mail").where("to", "==", email).get();
  return s.docs.map((d) => d.data());
}
const hrefs = (html) => [...html.matchAll(/href="([^"]+)"/g)].map((m) => m[1].replace(/&amp;/g, "&"));
const oobCodeOf = (link) => new URL(link).searchParams.get("oobCode");

test("sign up with email + password → registration creates the profile and mails a verification link", async () => {
  const email = "new.user@e2e.test";
  const { status, body } = await signUp(email, "Secret123");
  assert.equal(status, 200);
  const reg = await callable("assignInitialRole", { registration: { fullName: "New User", email, tosAccepted: true } }, body.idToken);
  assert.equal(reg.result?.role, "SPECIALIST", JSON.stringify(reg));
  const profile = (await db.collection("user_profiles").doc(body.localId).get()).data();
  assert.equal(profile.role, "SPECIALIST");
  assert.equal(profile.email, email);
  assert.notEqual(profile.emailVerified, true);
  const user = await auth.getUser(body.localId);
  assert.equal(user.customClaims?.role, "SPECIALIST");
  assert.deepEqual(user.providerData.map((p) => p.providerId), ["password"]);
  await waitFor("verification mail", async () => (await mailsTo(email)).find((m) => /verify/i.test(m.message.subject)));
});

test("password sign-in: the right password works, a wrong one is refused", async () => {
  const email = "signin@e2e.test";
  await signUp(email, "Secret123");
  assert.equal((await signIn(email, "Secret123")).status, 200);
  const wrong = await signIn(email, "Wrong999");
  assert.equal(wrong.status, 400);
});

test("Forgot password: branded mail → reset → the new password signs in; unknown addresses get the same answer", async () => {
  const email = "forgot@e2e.test";
  const { body } = await signUp(email, "Secret123");
  const res = await callable("sendPasswordResetLink", { email });
  assert.deepEqual(res.result, { ok: true });
  const mail = await waitFor("reset mail", async () => (await mailsTo(email)).find((m) => /Reset your ProHost password/.test(m.message.subject)));
  const link = hrefs(mail.message.html).find((h) => h.includes("oobCode"));
  assert.ok(link, "reset mail carries an action link");
  const reset = await rest("accounts:resetPassword", { oobCode: oobCodeOf(link), newPassword: "Changed456" });
  assert.equal(reset.status, 200, JSON.stringify(reset.body));
  assert.equal((await signIn(email, "Secret123")).status, 400);
  const after = await signIn(email, "Changed456");
  assert.equal(after.status, 200);
  assert.equal(after.body.localId, body.localId);

  const unknown = await callable("sendPasswordResetLink", { email: "nobody@e2e.test" });
  assert.deepEqual(unknown.result, { ok: true });
  assert.equal((await mailsTo("nobody@e2e.test")).length, 0);
  const bad = await callable("sendPasswordResetLink", { email: "not-an-email" });
  assert.equal(bad.error?.status, "INVALID_ARGUMENT");
});

test("migration: email-code and magic-link accounts get an invite; the link sets a first password on the same account", async () => {
  const { needsPasswordSetup, factsOf, sendPasswordSetupInvite } = require("./lib/auth/passwordReset");
  const codeUser = await auth.createUser({ email: "code.user@e2e.test", emailVerified: true }); // like verifyEmailOtp
  const linkUser = await auth.createUser({ email: "link.user@e2e.test", emailVerified: true });
  await auth.updateUser(linkUser.uid, { providerToLink: { providerId: "password", uid: "link.user@e2e.test", email: "link.user@e2e.test" } }).catch(() => undefined);
  const googleUser = await auth.createUser({ email: "g.user@e2e.test", emailVerified: true });
  await auth.updateUser(googleUser.uid, { providerToLink: { providerId: "google.com", uid: "g-123", email: "g.user@e2e.test" } });
  const pwUser = (await signUp("pw.user@e2e.test", "Secret123")).body.localId;
  await db.collection("user_profiles").doc(codeUser.uid).set({ role: "SPECIALIST", fullName: "Code User", email: "code.user@e2e.test" });

  assert.equal(needsPasswordSetup(factsOf(await auth.getUser(codeUser.uid))), "email_code");
  assert.equal(needsPasswordSetup(factsOf(await auth.getUser(googleUser.uid))), null);
  assert.equal(needsPasswordSetup(factsOf(await auth.getUser(pwUser))), null);
  const linkCat = needsPasswordSetup(factsOf(await auth.getUser(linkUser.uid)));
  assert.ok(linkCat === "email_link" || linkCat === "email_code");

  assert.equal(await sendPasswordSetupInvite(codeUser.uid, "code.user@e2e.test", "email_code", Date.now(), FUNCTIONS), true);
  const invite = (await db.collection("auth_password_invites").doc(codeUser.uid).get()).data();
  assert.equal(invite.category, "email_code");
  assert.ok(!("token" in invite) && invite.tokenHash.length === 64);
  const mail = await waitFor("invite mail", async () => (await mailsTo("code.user@e2e.test")).find((m) => /Set a password/.test(m.message.subject)));
  const inviteLink = hrefs(mail.message.html).find((h) => h.includes("passwordSetupLink"));
  const u = new URL(inviteLink);

  const get = await fetch(inviteLink);
  assert.equal(get.status, 200);
  assert.match(await get.text(), /method="POST"/);
  const post = await fetch(`${u.origin}${u.pathname}`, {
    method: "POST", redirect: "manual",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ u: u.searchParams.get("u"), t: u.searchParams.get("t") }).toString(),
  });
  assert.equal(post.status, 303);
  const action = post.headers.get("location");
  assert.ok(oobCodeOf(action), `redirects to a Firebase action link: ${action}`);
  assert.equal((await rest("accounts:resetPassword", { oobCode: oobCodeOf(action), newPassword: "FirstPass1" })).status, 200);
  const signedIn = await signIn("code.user@e2e.test", "FirstPass1");
  assert.equal(signedIn.status, 200);
  assert.equal(signedIn.body.localId, codeUser.uid, "same account, same profile");
  assert.ok((await auth.getUser(codeUser.uid)).providerData.some((p) => p.providerId === "password"));

  const wrong = await fetch(`${u.origin}${u.pathname}`, {
    method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ u: codeUser.uid, t: "forged" }).toString(),
  });
  assert.equal(wrong.status, 410);
});

test("retirement switch: with legacyEmailSignIn=false the old code callables answer 'update the app'", async () => {
  const before = await callable("sendEmailOtp", { email: "legacy@e2e.test" });
  assert.deepEqual(before.result, { ok: true });
  await db.doc("app_config/auth").set({ legacyEmailSignIn: false });
  // The functions cache the switch for up to 60 s; poll until the new value is seen.
  const retired = await waitFor("retired answer", async () => {
    const r = await callable("sendEmailOtp", { email: "legacy2@e2e.test" });
    return r.error?.status === "FAILED_PRECONDITION" ? r : null;
  }, 70000);
  assert.match(retired.error.message, /Update ProHost/);
  const verify = await callable("verifyEmailOtp", { email: "legacy@e2e.test", code: "123456" });
  assert.equal(verify.error?.status, "FAILED_PRECONDITION");
  await db.doc("app_config/auth").delete();
});
