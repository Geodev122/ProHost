/**
 * All outbound email templates for ProHost.
 * Each function returns { subject, html } ready to pass to sendEmail().
 * Templates are role- and tier-aware: a UserContext enriches every render.
 */

export interface UserContext {
  fullName: string;
  email: string;
  role: "SPECIALIST" | "PRO_HOST" | "ADMIN";
  activeListingCount?: number;
  packageName?: string;        // e.g. "Growth", "Pro", "Enterprise"
  joinedDays?: number;         // days since createdAtMillis
}

// ─── Base layout ────────────────────────────────────────────────────────────

// ProHost logo — served from Firebase Hosting, already live at pro-host.tech/logo.png
const LOGO_URL = "https://pro-host.tech/logo.png";

/**
 * @param title    <title> tag and fallback subject label
 * @param body     Inner HTML for the main content area
 * @param preheader Optional preview text shown in email-client inbox lists
 *                 before the email is opened (50–90 chars ideal).
 */
export function layout(title: string, body: string, preheader = ""): string {
  // Pad preheader with zero-width non-breaking spaces so email clients
  // don't pull in the next visible text into the preview snippet.
  const preheaderHtml = preheader
    ? `<span style="display:none;max-height:0;overflow:hidden;mso-hide:all">${preheader
        }&nbsp;&#8204;&nbsp;&#8204;&nbsp;&#8204;&nbsp;&#8204;&nbsp;&#8204;&nbsp;&#8204;&nbsp;&#8204;&nbsp;&#8204;&nbsp;&#8204;&nbsp;&#8204;&nbsp;&#8204;&nbsp;&#8204;&nbsp;&#8204;&nbsp;&#8204;&nbsp;&#8204;&nbsp;&#8204;&nbsp;&#8204;&nbsp;&#8204;&nbsp;&#8204;&nbsp;&#8204;&nbsp;&#8204;&nbsp;&#8204;&nbsp;&#8204;&nbsp;&#8204;&nbsp;&#8204;&nbsp;&#8204;&nbsp;&#8204;&nbsp;&#8204;&nbsp;&#8204;&nbsp;&#8204;&nbsp;&#8204;&nbsp;&#8204;&nbsp;&#8204;&nbsp;&#8204;&nbsp;&#8204;&nbsp;&#8204;&nbsp;&#8204;&nbsp;&#8204;&nbsp;&#8204;&nbsp;&#8204;</span>`
    : "";

  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8"/>
  <meta name="viewport" content="width=device-width,initial-scale=1"/>
  <meta name="color-scheme" content="light"/>
  <meta name="supported-color-schemes" content="light"/>
  <title>${title}</title>
  <style>
    /* ── Reset ── */
    *, *::before, *::after { box-sizing: border-box; }
    body { margin:0; padding:0; background:#EDE8E3; font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','Helvetica Neue',Arial,sans-serif; color:#1A1A1A; -webkit-font-smoothing:antialiased; }

    /* ── Outer shell ── */
    .outer { background:#EDE8E3; padding:40px 16px 56px; }
    .wrap  { max-width:580px; margin:0 auto; background:#FFFFFF; border-radius:18px; overflow:hidden; box-shadow:0 6px 28px rgba(0,0,0,.09); }

    /* ── Header ── */
    .hd { padding:28px 36px 24px; background:linear-gradient(145deg,#FF6635 0%,#E84C00 100%); }
    .hd-wordmark { margin:0; color:#FFF; font-size:22px; font-weight:800; letter-spacing:-0.5px; line-height:1; display:block; }
    .hd-sub { margin:5px 0 0; color:rgba(255,255,255,.7); font-size:11.5px; font-weight:500; letter-spacing:0.7px; text-transform:uppercase; }

    /* ── Body ── */
    .bd { padding:36px 36px 28px; }
    .bd h2 { margin:0 0 14px; font-size:21px; font-weight:700; color:#111; line-height:1.3; }
    .bd p  { margin:0 0 16px; font-size:14.5px; line-height:1.7; color:#4C4C4C; }
    .bd p:last-child { margin-bottom:0; }

    /* ── CTA button ── */
    .cta { margin:26px 0 22px; text-align:center; }
    .btn { display:inline-block; padding:15px 40px; background:linear-gradient(135deg,#FF6635,#E84C00); color:#FFFFFF !important; border-radius:11px; text-decoration:none; font-size:15.5px; font-weight:700; letter-spacing:0.1px; line-height:1; box-shadow:0 4px 16px rgba(235,80,10,.40); }

    /* ── Info card (booking/subscription details) ── */
    .card { background:#FFF8F4; border:1px solid #FFCFB5; border-radius:11px; padding:18px 22px; margin:20px 0; }
    .card p { margin:6px 0; font-size:13.5px; color:#555; line-height:1.55; }
    .card strong { color:#C94008; }

    /* ── Security / notice block (auth emails) ── */
    .notice { background:#FFF5F0; border-left:3px solid #FF6635; border-radius:0 9px 9px 0; padding:13px 16px; margin:20px 0; }
    .notice p { margin:0; font-size:13px; color:#7A3A18; line-height:1.6; }

    /* ── Muted fine print ── */
    .fine { font-size:12.5px !important; color:#A0A0A0 !important; line-height:1.6 !important; margin-top:20px !important; }

    /* ── Divider ── */
    .div { border:none; border-top:1px solid #EDE8E3; margin:0; }

    /* ── Footer ── */
    .ft { background:#FAF7F5; padding:22px 36px 26px; text-align:center; }
    .ft p { margin:0 0 4px; font-size:12px; color:#B0A59E; line-height:1.7; }
    .ft a { color:#FF6635; text-decoration:none; }

    /* ── Mobile ── */
    @media only screen and (max-width:600px) {
      .outer { padding:0 0 32px; }
      .wrap  { border-radius:0; box-shadow:none; }
      .hd    { padding:22px 20px 18px; }
      .bd    { padding:26px 20px 22px; }
      .ft    { padding:18px 20px 22px; }
      .btn   { display:block; width:100%; text-align:center; }
    }
  </style>
</head>
<body>
  <div class="outer">
    ${preheaderHtml}
    <div class="wrap">

      <div class="hd">
        <table role="presentation" cellpadding="0" cellspacing="0" border="0">
          <tr>
            <td style="vertical-align:middle;padding-right:13px">
              <img src="${LOGO_URL}" alt="ProHost" width="44" height="44"
                   style="display:block;border:0;border-radius:10px"/>
            </td>
            <td style="vertical-align:middle">
              <span class="hd-wordmark">ProHost</span>
              <div class="hd-sub">Workspace Hosting Platform</div>
            </td>
          </tr>
        </table>
      </div>

      <div class="bd">${body}</div>

      <hr class="div"/>
      <div class="ft">
        <p>&copy; 2026 ProHost &nbsp;&middot;&nbsp; <a href="https://pro-host.tech">pro-host.tech</a> &nbsp;&middot;&nbsp; <a href="mailto:admin@pro-host.tech">Support</a></p>
        <p>You're receiving this because you have a ProHost account.</p>
      </div>

    </div>
  </div>
</body>
</html>`;
}

// ─── OTP sign-in (legacy 6-digit code flow) ──────────────────────────────────

export function otpSignInTemplate(email: string, code: string, clickUrl: string) {
  const subject = "Your ProHost sign-in code";
  const html = layout(subject, `
    <h2>Your sign-in code</h2>
    <p>Use the code below to sign in as <strong style="color:#1A1A1A">${email}</strong>.
       It expires in <strong style="color:#1A1A1A">10 minutes</strong>.</p>
    <div style="text-align:center;margin:28px 0">
      <span style="display:inline-block;font-size:36px;font-weight:800;letter-spacing:10px;color:#111;background:#F5F0EC;padding:16px 28px;border-radius:12px">${code}</span>
    </div>
    <div class="cta">
      <a class="btn" href="${clickUrl}">Sign in automatically</a>
    </div>
    <div class="notice">
      <p><strong>Security:</strong> Never share this code. ProHost will never ask for it.</p>
    </div>
    <p class="fine">If you didn't request this, ignore this email. Your account is secure.</p>
  `, `Your ProHost sign-in code is ${code}`);
  return { subject, html };
}

// ─── Sign-in link (magic link) ────────────────────────────────────────────────

export function signInLinkTemplate(email: string, link: string) {
  const subject = "Sign in to ProHost";
  const html = layout(subject, `
    <h2>Your sign-in link</h2>
    <p>Tap the button below to sign in as <strong style="color:#1A1A1A">${email}</strong>.
       This link expires in <strong style="color:#1A1A1A">60 minutes</strong> and works only once.</p>
    <div class="cta">
      <a class="btn" href="${link}">Sign in to ProHost</a>
    </div>
    <div class="notice">
      <p><strong>Security:</strong> Never share this link — it grants direct access to your account.
         ProHost will never ask you to forward it.</p>
    </div>
    <p class="fine">If you didn't request this, ignore this email. Your account hasn't been touched.</p>
  `, "Your ProHost sign-in link — tap to open the app");
  return { subject, html };
}

// ─── Email Verification ──────────────────────────────────────────────────────

export function emailVerificationTemplate(ctx: UserContext, verifyUrl: string) {
  const subject = "Verify your ProHost email address";
  const html = layout(subject, `
    <h2>Welcome to ProHost, ${ctx.fullName}!</h2>
    <p>You're almost set. Please confirm your email address to activate your account and start discovering workspaces.</p>
    <div class="cta">
      <a class="btn" href="${verifyUrl}">Verify Email Address</a>
    </div>
    <p class="fine">This link expires in 24 hours. If you didn't create a ProHost account, you can safely ignore this email.</p>
  `, "Confirm your email to activate your ProHost account");
  return { subject, html };
}

export function emailVerificationResendTemplate(ctx: UserContext, verifyUrl: string) {
  const subject = "New verification link — ProHost";
  const html = layout(subject, `
    <h2>Here's your new verification link</h2>
    <p>Hi ${ctx.fullName}, your previous link has been invalidated. Use the button below to verify your email address.</p>
    <div class="cta">
      <a class="btn" href="${verifyUrl}">Verify Email Address</a>
    </div>
    <p class="fine">This link expires in 24 hours.</p>
  `, "New verification link for your ProHost account");
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
  /** "20 attendees × $8/person (Standard)" for per-attendee bookings. */
  attendeeSummary?: string | null;
  rejectionReason?: string | null;
}

function attendeeLine(booking: BookingContext): string {
  return booking.attendeeSummary ? `<p><strong>Attendees:</strong> ${booking.attendeeSummary}</p>` : "";
}

export function newBookingRequestTemplate(owner: UserContext, booking: BookingContext) {
  const subject = `New booking request — ${booking.listingTitle}`;
  const html = layout(subject, `
    <h2>New booking request</h2>
    <p><strong>${booking.specialistName}</strong> has requested to book your space. Review the details and respond in the app.</p>
    <div class="card">
      <p><strong>Listing:</strong> ${booking.listingTitle}</p>
      <p><strong>Dates:</strong> ${booking.dateRange}</p>
      ${attendeeLine(booking)}
      <p><strong>Quoted amount:</strong> $${booking.totalUsd.toFixed(2)}</p>
      <p><strong>From:</strong> ${booking.specialistName}</p>
    </div>
    <div class="cta">
      <a class="btn" href="prohost://owner_hub">Review Request</a>
    </div>
  `, `${booking.specialistName} wants to book ${booking.listingTitle}`);
  return { subject, html };
}

export function bookingAcceptedTemplate(specialist: UserContext, booking: BookingContext) {
  const subject = `Booking confirmed — ${booking.listingTitle}`;
  const html = layout(subject, `
    <h2>Your booking is confirmed!</h2>
    <p><strong>${booking.ownerName}</strong> has accepted your request. See you there.</p>
    <div class="card">
      <p><strong>Listing:</strong> ${booking.listingTitle}</p>
      <p><strong>Dates:</strong> ${booking.dateRange}</p>
      ${attendeeLine(booking)}
      <p><strong>Total:</strong> $${booking.totalUsd.toFixed(2)}</p>
    </div>
    <p>Payment is settled directly with the space owner on arrival.</p>
    <div class="cta">
      <a class="btn" href="prohost://my_bookings">View Booking</a>
    </div>
  `, `Your booking at ${booking.listingTitle} is confirmed`);
  return { subject, html };
}

export function bookingRejectedTemplate(specialist: UserContext, booking: BookingContext) {
  const subject = `Booking not available — ${booking.listingTitle}`;
  const html = layout(subject, `
    <h2>Hi ${specialist.fullName},</h2>
    <p>Unfortunately your booking request for <strong>${booking.listingTitle}</strong> (${booking.dateRange}) wasn't accepted this time.</p>
    ${booking.rejectionReason ? `<p><strong>Reason:</strong> ${booking.rejectionReason}</p>` : ""}
    <p>Don't worry — there are many other great workspaces on ProHost.</p>
    <div class="cta">
      <a class="btn" href="prohost://discovery">Browse More Spaces</a>
    </div>
  `, `Your booking request for ${booking.listingTitle} wasn't accepted`);
  return { subject, html };
}

export function bookingCancelledTemplate(recipient: UserContext, booking: BookingContext, cancelledByRole: string) {
  const subject = `Booking cancelled — ${booking.listingTitle}`;
  const cancellerLabel = cancelledByRole === "owner" ? "the host" : "the specialist";
  const html = layout(subject, `
    <h2>Booking cancelled</h2>
    <p>Your booking for <strong>${booking.listingTitle}</strong> (${booking.dateRange}) has been cancelled by ${cancellerLabel}.</p>
    <div class="cta">
      <a class="btn" href="prohost://my_bookings">View My Bookings</a>
    </div>
  `, `Your ProHost booking for ${booking.listingTitle} has been cancelled`);
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
    <div class="cta">
      <a class="btn" href="prohost://owner_hub">Go to Owner Hub</a>
    </div>
  `, `Your ProHost ${planName} plan is now active`);
  return { subject, html };
}

export function subscriptionRenewedTemplate(ctx: UserContext, planName: string, expiryDate: string) {
  const subject = `Subscription renewed — ${planName}`;
  const html = layout(subject, `
    <h2>Renewed and ready, ${ctx.fullName}</h2>
    <p>Your <strong>${planName}</strong> subscription has automatically renewed. Your Pro Host access continues through <strong>${expiryDate}</strong>.</p>
  `, `Your ProHost ${planName} plan has been renewed`);
  return { subject, html };
}

export function subscriptionExpiringTemplate(ctx: UserContext, planName: string, daysLeft: number, expiryDate: string) {
  const subject = `Your Pro Host subscription expires in ${daysLeft} day${daysLeft !== 1 ? "s" : ""}`;
  const html = layout(subject, `
    <h2>Subscription expiring soon</h2>
    <p>Hi ${ctx.fullName}, your <strong>${planName}</strong> plan expires on <strong>${expiryDate}</strong> — ${daysLeft} day${daysLeft !== 1 ? "s" : ""} from now.</p>
    <p>Renew now to keep your listings live and avoid interrupting active bookings.</p>
    <div class="cta">
      <a class="btn" href="prohost://owner_subscriptions">Renew Subscription</a>
    </div>
    ${(ctx.activeListingCount ?? 0) > 0
      ? `<p class="fine">You have ${ctx.activeListingCount} active listing${ctx.activeListingCount !== 1 ? "s" : ""}. They'll be hidden if your subscription lapses.</p>`
      : ""}
  `, `Your ProHost ${planName} plan expires in ${daysLeft} day${daysLeft !== 1 ? "s" : ""}`);
  return { subject, html };
}

export function subscriptionExpiredTemplate(ctx: UserContext) {
  const subject = "Your Pro Host subscription has ended";
  const html = layout(subject, `
    <h2>Subscription ended</h2>
    <p>Hi ${ctx.fullName}, your Pro Host subscription has expired. Your listings are now hidden from Discovery.</p>
    <p>Renew at any time to restore your listings and Pro Host access instantly.</p>
    <div class="cta">
      <a class="btn" href="prohost://owner_subscriptions">Renew Now</a>
    </div>
  `, "Renew your ProHost subscription to restore your listings");
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
    <p class="fine">Reply directly to this email to respond to ${sender.fullName}.</p>
  `, `Inquiry from ${sender.fullName} on ProHost`);
  return { subject: emailSubject, html };
}
