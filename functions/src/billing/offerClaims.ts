/**
 * What the website, the campaign email templates and the marketing media promise about the launch offers.
 * Play Console is the only source of the real offers, so this object is the one place that says what the
 * static copy claims: `lintOffers` (Maintenance `playcatalog`) warns when Play stops matching it, and
 * `offerClaims.test.ts` fails CI when a page stops stating it or still states a retired claim.
 * Change the offer in Play, then this file, then the pages — never only one of them.
 */
export const WEBSITE_OFFER_CLAIMS = {
  /** Monthly plan: first N months free (Play offer `free-4-months`, whose phase is 1 month since Oct 2026). */
  monthlyFreeMonths: 1,
  /** Yearly plan: this percent off the first year (Play offer `launch-50-first-year`). */
  yearlyDiscountPercent: 50,
} as const;

/** Every page (and the admin email template) must state both claims, in its own language. */
export const CLAIM_PHRASES = {
  en: [/(?:first|1) month (?:of (?:ProHost )?Premium )?(?:is )?free/i, /50% off (?:their |your |the )?first year/i],
  ar: [/شهر (?:من Premium )?مجانًا/, /خصم 50% على السنة الأولى/],
} as const;

/** Claims that were true once and are not any more. */
export const RETIRED_CLAIMS: RegExp[] = [
  /50% off (?:their |your |the )?first (?:premium )?(?:subscription|month)/i,
  /half[- ]price/i,
  /first (?:premium )?subscription is half/i,
  /خصم 50% على أول اشتراك/,
  // 4 months free was the monthly offer until Oct 2026 (now 1 month).
  /\b4 months free/i,
  /four months (?:of Premium )?(?:are )?free/i,
  /4 أشهر مجانًا/,
];
