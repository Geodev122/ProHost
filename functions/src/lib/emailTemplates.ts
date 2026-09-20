/**
 * All outbound email templates for ProHost.
 * Each function returns { subject, html } ready to pass to sendEmail().
 * Templates are role- and tier-aware: a UserContext enriches every render.
 */

export interface UserContext {
  fullName: string;
  email: string;
  role: "SPECIALIST" | "PRO_HOST" | "ADMIN";
  kycLevel?: number;           // 0–3
  activeListingCount?: number;
  packageName?: string;        // e.g. "Growth", "Pro", "Enterprise"
  joinedDays?: number;         // days since createdAtMillis
}

// ─── Base layout ────────────────────────────────────────────────────────────

function layout(title: string, body: string): string {
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8"/>
  <meta name="viewport" content="width=device-width,initial-scale=1"/>
  <title>${title}</title>
  <style>
    body { margin:0; padding:0; background:#F5F5F5; font-family:'Helvetica Neue',Arial,sans-serif; color:#1A1A1A; }
    .wrap { max-width:600px; margin:32px auto; background:#FFFFFF; border-radius:12px; overflow:hidden; box-shadow:0 2px 8px rgba(0,0,0,.08); }
    .header { background:linear-gradient(135deg,#FF6B35,#FF8C42); padding:28px 32px; }
    .header h1 { margin:0; color:#FFFFFF; font-size:22px; font-weight:700; letter-spacing:-.3px; }
    .header p  { margin:4px 0 0; color:rgba(255,255,255,.85); font-size:13px; }
    .body   { padding:32px; }
    .body h2 { margin:0 0 12px; font-size:18px; font-weight:600; }
    .body p  { margin:0 0 16px; font-size:14px; line-height:1.6; color:#444; }
    .btn { display:inline-block; margin:8px 0 16px; padding:12px 28px; background:#FF6B35; color:#FFFFFF !important; border-radius:8px; text-decoration:none; font-size:14px; font-weight:600; }
    .card { background:#FFF8F5; border:1px solid #FFD6C2; border-radius:8px; padding:16px 20px; margin:16px 0; }
    .card p { margin:4px 0; font-size:13px; color:#555; }
    .card strong { color:#FF6B35; }
    .footer { background:#F9F9F9; padding:20px 32px; border-top:1px solid #EEE; font-size:12px; color:#999; text-align:center; }
    .footer a { color:#FF6B35; text-decoration:none; }
  </style>
</head>
<body>
  <div class="wrap">
    <div class="header">
      <h1>ProHost</h1>
      <p>Workspace Hosting Platform</p>
    </div>
    <div class="body">${body}</div>
    <div class="footer">
      &copy; 2026 ProHost &nbsp;·&nbsp; <a href="https://pro-host.tech">pro-host.tech</a>
      &nbsp;·&nbsp; <a href="mailto:admin@pro-host.tech">Support</a><br/>
      You're receiving this because you have a ProHost account.
    </div>
  </div>
</body>
</html>`;
}

// ─── Email Verification ──────────────────────────────────────────────────────

export function emailVerificationTemplate(ctx: UserContext, verifyUrl: string) {
  const subject = "Verify your ProHost email address";
  const html = layout(subject, `
    <h2>Hi ${ctx.fullName},</h2>
    <p>Thanks for joining ProHost! Please confirm your email address to continue setting up your account.</p>
    <p style="text-align:center">
      <a class="btn" href="${verifyUrl}">Verify Email Address</a>
    </p>
    <p style="font-size:12px;color:#999">This link expires in 24 hours. If you didn't create a ProHost account, you can safely ignore this email.</p>
  `);
  return { subject, html };
}

export function emailVerificationResendTemplate(ctx: UserContext, verifyUrl: string) {
  const subject = "New verification link for your ProHost account";
  const html = layout(subject, `
    <h2>Hi ${ctx.fullName},</h2>
    <p>Here's your new email verification link. The previous one has been invalidated.</p>
    <p style="text-align:center">
      <a class="btn" href="${verifyUrl}">Verify Email Address</a>
    </p>
    <p style="font-size:12px;color:#999">Expires in 24 hours.</p>
  `);
  return { subject, html };
}

// ─── ID Document Review ──────────────────────────────────────────────────────

export function idDocumentSubmittedTemplate(ctx: UserContext) {
  const subject = "ID document received — under review";
  const html = layout(subject, `
    <h2>We've received your ID, ${ctx.fullName}</h2>
    <p>Your identity document has been submitted for review. Our team will verify it within 1 business day.</p>
    <div class="card">
      <p>You'll receive an email once the review is complete.</p>
      <p>You can check your verification status at any time in <strong>Profile → Verification</strong>.</p>
    </div>
    ${ctx.role === "SPECIALIST"
      ? `<p>Once verified, you'll be able to upgrade to a <strong>Pro Host</strong> plan and start listing your spaces.</p>`
      : ""}
  `);
  return { subject, html };
}

export function idDocumentApprovedTemplate(ctx: UserContext) {
  const subject = "Identity verified ✓ — you can now upgrade to Pro Host";
  const html = layout(subject, `
    <h2>You're verified, ${ctx.fullName}!</h2>
    <p>Your identity document has been approved. Your account is now fully verified.</p>
    <p>You can now upgrade to a <strong>Pro Host</strong> plan to start publishing workspace listings and receiving bookings.</p>
    <p style="text-align:center">
      <a class="btn" href="prohost://owner_subscriptions">Upgrade to Pro Host</a>
    </p>
  `);
  return { subject, html };
}

export function idDocumentRejectedTemplate(ctx: UserContext, reason?: string) {
  const subject = "Action needed — ID document could not be verified";
  const html = layout(subject, `
    <h2>Hi ${ctx.fullName},</h2>
    <p>Unfortunately we weren't able to verify your identity document.${reason ? ` Reason: <em>${reason}</em>.` : ""}</p>
    <div class="card">
      <p>Please re-upload a clear, unobstructed photo of a valid government-issued ID (passport, national ID card, or driving licence).</p>
    </div>
    <p style="text-align:center">
      <a class="btn" href="prohost://profile">Re-upload ID Document</a>
    </p>
  `);
  return { subject, html };
}

// ─── Booking Events ──────────────────────────────────────────────────────────

export interface BookingContext {
  bookingId: string;
  listingTitle: string;
  specialistName: string;
  ownerName: string;
  dateRange: string;        // e.g. "Sep 22 – Sep 24, 2026"
  totalUsd: number;
}

export function newBookingRequestTemplate(owner: UserContext, booking: BookingContext) {
  const subject = `New booking request — ${booking.listingTitle}`;
  const html = layout(subject, `
    <h2>New booking request, ${owner.fullName}</h2>
    <p><strong>${booking.specialistName}</strong> has requested to book your space.</p>
    <div class="card">
      <p><strong>Listing:</strong> ${booking.listingTitle}</p>
      <p><strong>Dates:</strong> ${booking.dateRange}</p>
      <p><strong>Quoted amount:</strong> $${booking.totalUsd.toFixed(2)}</p>
    </div>
    <p>Open the ProHost app to review and respond to this request.</p>
    <p style="text-align:center">
      <a class="btn" href="prohost://owner_hub">Review Request</a>
    </p>
  `);
  return { subject, html };
}

export function bookingAcceptedTemplate(specialist: UserContext, booking: BookingContext) {
  const subject = `Booking confirmed — ${booking.listingTitle}`;
  const html = layout(subject, `
    <h2>Your booking is confirmed, ${specialist.fullName}!</h2>
    <p>Great news — <strong>${booking.ownerName}</strong> has accepted your booking request.</p>
    <div class="card">
      <p><strong>Listing:</strong> ${booking.listingTitle}</p>
      <p><strong>Dates:</strong> ${booking.dateRange}</p>
      <p><strong>Total:</strong> $${booking.totalUsd.toFixed(2)}</p>
    </div>
    <p>Payment is settled directly with the space owner on arrival.</p>
    <p style="text-align:center">
      <a class="btn" href="prohost://my_bookings">View Booking</a>
    </p>
  `);
  return { subject, html };
}

export function bookingRejectedTemplate(specialist: UserContext, booking: BookingContext) {
  const subject = `Booking not available — ${booking.listingTitle}`;
  const html = layout(subject, `
    <h2>Hi ${specialist.fullName},</h2>
    <p>Unfortunately your booking request for <strong>${booking.listingTitle}</strong> (${booking.dateRange}) was not accepted.</p>
    <p>Don't worry — there are many other great workspaces on ProHost.</p>
    <p style="text-align:center">
      <a class="btn" href="prohost://discovery">Browse More Spaces</a>
    </p>
  `);
  return { subject, html };
}

export function bookingCancelledTemplate(recipient: UserContext, booking: BookingContext, cancelledByRole: string) {
  const subject = `Booking cancelled — ${booking.listingTitle}`;
  const cancellerLabel = cancelledByRole === "owner" ? "the host" : "the specialist";
  const html = layout(subject, `
    <h2>Booking cancelled, ${recipient.fullName}</h2>
    <p>Your booking for <strong>${booking.listingTitle}</strong> (${booking.dateRange}) has been cancelled by ${cancellerLabel}.</p>
    <p style="text-align:center">
      <a class="btn" href="prohost://my_bookings">View My Bookings</a>
    </p>
  `);
  return { subject, html };
}

// ─── Subscription Events ─────────────────────────────────────────────────────

export function subscriptionActivatedTemplate(ctx: UserContext, planName: string) {
  const subject = `Welcome to Pro Host — ${planName} plan activated!`;
  const listingWord = (ctx.activeListingCount ?? 0) > 0 ? "your listings are live" : "publish your first listing";
  const html = layout(subject, `
    <h2>You're a Pro Host now, ${ctx.fullName}!</h2>
    <p>Your <strong>${planName}</strong> subscription is active. Head to your Owner Hub to ${listingWord}.</p>
    <div class="card">
      <p>As a Pro Host you can create workspace listings, manage bookings, and build your hosting profile.</p>
    </div>
    <p style="text-align:center">
      <a class="btn" href="prohost://owner_hub">Go to Owner Hub</a>
    </p>
  `);
  return { subject, html };
}

export function subscriptionRenewedTemplate(ctx: UserContext, planName: string, expiryDate: string) {
  const subject = `Subscription renewed — ${planName}`;
  const html = layout(subject, `
    <h2>Renewed and ready, ${ctx.fullName}</h2>
    <p>Your <strong>${planName}</strong> subscription has automatically renewed. Your Pro Host access continues through <strong>${expiryDate}</strong>.</p>
  `);
  return { subject, html };
}

export function subscriptionExpiringTemplate(ctx: UserContext, planName: string, daysLeft: number, expiryDate: string) {
  const subject = `Your Pro Host subscription expires in ${daysLeft} day${daysLeft !== 1 ? "s" : ""}`;
  const html = layout(subject, `
    <h2>Subscription expiring soon, ${ctx.fullName}</h2>
    <p>Your <strong>${planName}</strong> plan expires on <strong>${expiryDate}</strong> (${daysLeft} day${daysLeft !== 1 ? "s" : ""} away).</p>
    <p>Renew now to keep your listings live and avoid interrupting active bookings.</p>
    <p style="text-align:center">
      <a class="btn" href="prohost://owner_subscriptions">Renew Subscription</a>
    </p>
    ${(ctx.activeListingCount ?? 0) > 0
      ? `<p style="font-size:12px;color:#999">You currently have ${ctx.activeListingCount} active listing${ctx.activeListingCount !== 1 ? "s" : ""}. They'll be hidden if your subscription lapses.</p>`
      : ""}
  `);
  return { subject, html };
}

export function subscriptionExpiredTemplate(ctx: UserContext) {
  const subject = "Your Pro Host subscription has ended";
  const html = layout(subject, `
    <h2>Subscription ended, ${ctx.fullName}</h2>
    <p>Your Pro Host subscription has expired. Your listings are now hidden from Discovery.</p>
    <p>Renew at any time to restore your listings and Pro Host access instantly.</p>
    <p style="text-align:center">
      <a class="btn" href="prohost://owner_subscriptions">Renew Now</a>
    </p>
  `);
  return { subject, html };
}

// ─── Inquiry ─────────────────────────────────────────────────────────────────

export function inAppInquiryTemplate(sender: UserContext, recipientName: string, subject: string, message: string) {
  const emailSubject = `ProHost inquiry from ${sender.fullName}: ${subject}`;
  const roleLabel = sender.role === "PRO_HOST" ? "Pro Host" : "Specialist";
  const html = layout(emailSubject, `
    <h2>New inquiry from ${sender.fullName}</h2>
    <div class="card">
      <p><strong>From:</strong> ${sender.fullName} (${roleLabel})</p>
      <p><strong>Email:</strong> ${sender.email}</p>
      <p><strong>Subject:</strong> ${subject}</p>
    </div>
    <p><strong>Message:</strong></p>
    <p>${message.replace(/\n/g, "<br/>")}</p>
    <p style="font-size:12px;color:#999">Reply directly to this email to respond to ${sender.fullName}.</p>
  `);
  return { subject: emailSubject, html };
}
