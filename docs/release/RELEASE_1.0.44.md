# ProHost 1.0.44 (versionCode 44)

## What's in it (since 1.0.43)
- **Email + password sign-in** (Firebase Authentication) with **Forgot password**; Google sign-in unchanged. Sign in,
  Create account (live password-rules checklist: 8+ characters, a lowercase letter, a number), Forgot password →
  branded EN/AR reset email ("if an account exists…", never revealing whether it does). Android Credential Manager
  offers a saved password / Google account and saves the password after success.
- **Email code and magic link removed** from the app (deep links, manifest filters, callables). 1.0.43 keeps working
  until Maintenance `authconfig retire`; from then old apps see "Email codes were replaced by passwords. Update ProHost…".
- People who signed up with an email code or link set a password with Forgot password, or from the "set your password"
  invite (Maintenance `passwordinvites`, 30-day link). Same account, same uid, same listings.
- Profile shows "Verify your email · Resend" for password accounts until the address is confirmed (never blocking).
- **Play Integrity fixed** on the server: decodes now succeed (scoped credential), so release-build integrity verdicts
  are enforced on sign-in for the first time.

## Backend / config (already live with the main deploy)
`sendPasswordResetLink`, `passwordSetupLink`, legacy switch `app_config/auth.legacyEmailSignIn`, rules for
`auth_password_invites`, Maintenance `authconfig` / `passwordinvites` / `integrity`.

## Release details
- Released 2026-10-22 to production at 100% (owner's call) by `release-bundle.yml` run 39805068660 from main `b420bf4`:
  bundle versionCode 44 (sha256 `e5447b4d…3ee7`), R8 mapping and native symbols uploaded; probe run 39806224929 shows
  production `1.0.44 (44) completed`. Notes: en-GB (the only store-listing language).
- Before release: Maintenance `authconfig apply` (password policy ENFORCE live), `integrity` decode probe 400 (fixed;
  was 403), `passwordinvites` dry run: 4 accounts (3 magic-link, 1 email-code), none invited yet.
- CI: Android job and the release/screenshot workflows now set up JDK 21 from Temurin (setup-java's JetBrains index
  stopped listing 21 on 2026-10-22).

## Ship order
1. CI green on main (Android, functions, rules, e2e, deploy); Design Screenshots clean; NIGHTHAWK 0 critical/high.
2. Maintenance `authconfig` (dry run) → `authconfig code=apply` (password policy ENFORCE, `pro-host.tech` authorized).
3. Maintenance `integrity` → dummy decode 400. If 403 "not linked": Play Console › App integrity › Play Integrity API ›
   Link Cloud project `prohost-f766f`.
4. `release-bundle.yml` play_track=production rollout=1 → probe shows `1.0.44 (44) completed`.
5. When 1.0.44 is live on Play: `authconfig code=retire`, then `passwordinvites` (dry run) → `code=send`; delivery check.

## Check after release
- [ ] Create an account with email + password → registration form → profile; verification email arrives (EN + AR).
- [ ] Sign out → sign in with the password; wrong password shows the generic message with the Forgot password hint.
- [ ] Forgot password → email → Firebase page sets a new password → sign in with it.
- [ ] Google sign-in (new and returning) unchanged.
- [ ] Profile "Verify your email" disappears after tapping the link and returning to the app.
- [ ] Maintenance `logerrors`: `play_integrity_ok` from a real 1.0.44 sign-in, no `play_integrity_decode_failed`.
