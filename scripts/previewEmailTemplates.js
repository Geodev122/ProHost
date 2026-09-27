#!/usr/bin/env node
/**
 * Renders ProHost email templates to local HTML files for visual inspection.
 * Run: node scripts/previewEmailTemplates.js
 * Then open /tmp/email-preview-signin.html and /tmp/email-preview-verify.html in a browser.
 *
 * Requires the functions TypeScript to be compiled first:
 *   npm --prefix functions run build
 */
const path = require("path");
const fs = require("fs");

const { otpSignInTemplate, signInLinkTemplate, emailVerificationTemplate, subscriptionActivatedTemplate, newBookingRequestTemplate } = require(
  path.join(__dirname, "../functions/lib/lib/emailTemplates")
);

const PREVIEW_EMAIL = "alex.johnson@example.com";
const MOCK_LINK = "https://prohost-f766f.web.app/emaillink?oobCode=TEST_CODE&mode=signIn&apiKey=TEST_KEY";
const MOCK_VERIFY_LINK = "https://prohost-f766f.web.app/emaillink?oobCode=TEST_CODE&mode=verifyEmail&apiKey=TEST_KEY";

const MOCK_USER = { fullName: "Alex Johnson", email: PREVIEW_EMAIL, role: "SPECIALIST" };
const MOCK_PRO_HOST = { fullName: "Sara Khalil", email: "sara@example.com", role: "PRO_HOST", activeListingCount: 2 };

const MOCK_OTP_URL = "https://europe-west1-prohost-f766f.cloudfunctions.net/clickEmailOtpLink?email=alex%40example.com&code=482931";

// Templates to preview
const previews = [
  { name: "otp",           tpl: otpSignInTemplate(PREVIEW_EMAIL, "482 931", MOCK_OTP_URL) },
  { name: "signin",        tpl: signInLinkTemplate(PREVIEW_EMAIL, MOCK_LINK) },
  { name: "verify",        tpl: emailVerificationTemplate(MOCK_USER, MOCK_VERIFY_LINK) },
  { name: "sub-activated", tpl: subscriptionActivatedTemplate(MOCK_PRO_HOST, "Growth") },
  { name: "booking-req",   tpl: newBookingRequestTemplate(MOCK_PRO_HOST, {
      bookingId: "bk_01",
      listingTitle: "Creative Studio — Hamra",
      specialistName: "Alex Johnson",
      ownerName: "Sara Khalil",
      dateRange: "Oct 14 – Oct 16, 2026",
      totalUsd: 280,
    })
  },
];

const scratchpad = process.env.CLAUDE_SCRATCHPAD || "/tmp";
previews.forEach(({ name, tpl }) => {
  const out = path.join(scratchpad, `email-preview-${name}.html`);
  fs.writeFileSync(out, tpl.html, "utf8");
  console.log(`✓ ${name.padEnd(14)} → ${out}`);
});

const outSignIn = path.join(scratchpad, "email-preview-signin.html");
const outVerify = path.join(scratchpad, "email-preview-verify.html");

console.log("\nOpen these files in a browser to inspect the email design.");
