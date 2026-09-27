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

const { layout, emailVerificationTemplate } = require(
  path.join(__dirname, "../functions/lib/lib/emailTemplates")
);

const PREVIEW_EMAIL = "test@example.com";
const MOCK_LINK = "https://prohost-f766f.web.app/emaillink?oobCode=TEST_CODE&mode=signIn&apiKey=TEST_KEY";
const MOCK_VERIFY_LINK = "https://prohost-f766f.web.app/emaillink?oobCode=TEST_CODE&mode=verifyEmail&apiKey=TEST_KEY";

// Sign-in link email
const signInHtml = layout("Sign in to ProHost", `
  <h2>Sign in to ProHost</h2>
  <p>Tap the button below to sign in as <strong>${PREVIEW_EMAIL}</strong>.
     This link expires in 60 minutes and can only be used once.</p>
  <p style="text-align:center;margin-top:20px">
    <a class="btn" href="${MOCK_LINK}">Sign in to ProHost</a>
  </p>
  <p style="font-size:12px;color:#999;margin-top:16px">
    If you didn't request this, you can safely ignore this email — your account is secure.
    Never share this link; it grants direct access to your ProHost account.
  </p>
`);

// Verification email
const verifyTpl = emailVerificationTemplate(
  { fullName: "Alex Johnson", email: PREVIEW_EMAIL, role: "SPECIALIST" },
  MOCK_VERIFY_LINK
);

const outSignIn = "/tmp/email-preview-signin.html";
const outVerify = "/tmp/email-preview-verify.html";

fs.writeFileSync(outSignIn, signInHtml, "utf8");
fs.writeFileSync(outVerify, verifyTpl.html, "utf8");

console.log("✓ Sign-in link template →", outSignIn);
console.log("✓ Verification template →", outVerify);
console.log("\nOpen these files in a browser to inspect the email design.");
