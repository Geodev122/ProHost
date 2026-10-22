# Email delivery

ProHost has **no mail server of its own** (the Hostinger SMTP mailbox was retired on 2026-10-05).

| Email | Sent by |
|---|---|
| Password reset fallback (only when the `sendPasswordResetLink` callable is unreachable) | **Firebase Authentication's built-in mailer** (the app's `sendPasswordResetEmail`; Auth › Templates). Sender domain `pro-host.tech` once its DNS is verified, otherwise Firebase's default `noreply@prohost-f766f.firebaseapp.com`. |
| Password reset ("Forgot password", `auth/passwordReset.ts`), "set your password" invites for former email-code users (Maintenance `passwordinvites`), email verification, legacy 6-digit code (1.0.43 apps until `authconfig retire`), booking accepted/rejected/cancelled, subscription activated/renewed/expiring | Cloud Functions write `mail/{id}` (`functions/src/lib/email.ts` `sendEmail`) → the **Trigger Email extension** (`firebase/firestore-send-email`) delivers it. Delivery outcome is written back on the doc (`delivery.state`, `delivery.error`). |

`mail` is server-only (firestore.rules deny). Never log full addresses — use `maskEmail()`.

## One-time setup (owner)

### 1. Firebase Auth sender on pro-host.tech
Firebase Console › Authentication › Templates › any template › ✏️ › **Customize domain** → `pro-host.tech`
→ add the DNS records shown (TXT + CNAMEs) at your DNS host → **Verify** (can take up to 48 h). Until then
Firebase sends from its default address — sign-in keeps working.

### 2. Mail provider for the extension (Resend recommended — free 3,000/month)
1. Create an account at resend.com → Domains → Add `pro-host.tech` → add the DNS records shown → Verify.
2. API Keys → Create (permission "Sending access", domain pro-host.tech) → copy it.

### 3. Install the Trigger Email extension
Firebase Console › Extensions › **Explore** › "Trigger Email from Firestore" › Install, with:
- Cloud Functions location: **europe-west1**; Firestore database: `(default)`
- Authentication type: **Username & Password**
- SMTP connection URI: `smtps://resend@smtp.resend.com:465`
- SMTP password: the Resend API key (stored by the extension in Secret Manager)
- Email documents collection: **`mail`**
- Default FROM address: `ProHost <noreply@pro-host.tech>`; Default REPLY-TO: `admin@pro-host.tech`
- Leave users/templates collections empty.

CI deploys with `--only functions,firestore,storage,hosting`, so it never touches the installed extension.

### 4. Check
Send yourself a sign-in code from the app (Use a code instead), then look at the newest doc in `mail`:
`delivery.state` should be `SUCCESS`. `ERROR` → `delivery.error` says why (usually the API key or the
domain not verified yet).

### 5. Clean-up
Secret Manager: delete `HOSTINGER_SMTP_API_KEY` (and the dead `WHISH_SECRET_KEY`).

## Partner enquiries (pro-host.tech/partner)

The form on `/partner` calls `websiteContact` with `topic: "partner"`. Each enquiry:
- arrives at admin@pro-host.tech with subject `[ProHost Partner] Name (Company)`, reply-to the visitor, headers `X-ProHost-Form: partner`, `X-ProHost-Tags: partner, strategic-sponsor, usd-24000`, `Importance: high`;
- is saved in `mailing_list` (sources `partner`, `tags` array, never auto-subscribed to the newsletter) and `mailing_messages` (`source`, `tags`).

Gmail filter for admin@: Has the words `subject:"[ProHost Partner]"` → Apply label `ProHost/Partner`, star, never send to spam.
