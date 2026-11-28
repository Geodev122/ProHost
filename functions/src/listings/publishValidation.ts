import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions/v2";
import { getFirestore } from "firebase-admin/firestore";
import { recordAuditLog } from "../lib/auditLog";
import { isPerAttendee, tiersFor, AttendeeTierDoc } from "../lib/attendeePricing";
import { pricingFromLegacyFormula } from "./legacyPricing";
import { runAs } from "../lib/runtimeIdentity";
import { exceedsListingLimit } from "../billing/playCatalog";
import { keepUsedSlots, SlotBookingDoc, SlotListingDoc } from "./slotUsage";
import { todayIso } from "../bookings/bookingTerms";

/** Minimal shape of the parts of a workspace_listings document this validates —
 * mirrors SpaceListing.toFirestoreMap()'s key names (DataModels.kt), not the
 * full Kotlin model. */
interface RentalPricingConfigDoc {
  strategyType?: string;
  monthly?: { rateUsd?: number; isIndefinite?: boolean; toMonth?: number | null; toYear?: number | null } | null;
  hourly?: { cellPrices?: Record<string, number> } | null;
  shiftBased?: {
    shifts?: Array<{
      name?: string;
      isUnavailable?: boolean;
      // New shape (item 7b): a single flat "price" field. "pricing" is the old
      // 3-field shape (one-time/weekly/monthly), kept only for reading a
      // document written before this change — see structuredConfigHasRealPrice's
      // SHIFT_BASED case below.
      price?: number;
      pricing?: { oneTimePrice?: number; sameDayEveryWeekPrice?: number; monthlyRecurrencePrice?: number };
    }>;
    distribution?: Record<string, string[]>;
  } | null;
  dayBased?: {
    // New shape: a single flat "price" field. The 3 individually-named fields are
    // the old shape, kept only for reading a document written before this change —
    // see structuredConfigHasRealPrice's DAY_BASED case below.
    distribution?: Record<string, { price?: number; oneTimePrice?: number; sameDayEachMonthPrice?: number; sameDayEachWeekPrice?: number }>;
  } | null;
}

interface SubdivisionDoc {
  pricing?: RentalPricingConfigDoc;
  rentalStrategies?: unknown[];
  pricingMode?: string;
  attendeeTiers?: AttendeeTierDoc[];
}

export interface WorkspaceListingDoc {
  ownerId?: string;
  status?: string;
  ownershipProofUrl?: string | null;
  lat?: number;
  lng?: number;
  pricing?: RentalPricingConfigDoc;
  rentalFormulas?: unknown[];
  subdivisions?: SubdivisionDoc[];
  imageUrls?: string[];
}

function structuredConfigHasRealPrice(config: RentalPricingConfigDoc | undefined | null): boolean {
  if (!config) return false;
  switch (config.strategyType) {
    case "MONTHLY": {
      const m = config.monthly;
      if ((m?.rateUsd ?? 0) <= 0) return false;
      // A real rate alone isn't enough: turning "Indefinite" off without ever
      // picking an end month leaves a range the slot-builder's own date check
      // treats as already-ended — the listing would stay live forever with
      // permanently zero bookable availability, never demoted since a rate
      // genuinely was configured.
      if (m?.isIndefinite === false && (m?.toMonth == null || m?.toYear == null)) return false;
      return true;
    }
    case "HOURLY":
      return Object.values(config.hourly?.cellPrices ?? {}).some((v) => (v ?? 0) > 0);
    case "SHIFT_BASED": {
      const shiftsByName = new Map((config.shiftBased?.shifts ?? []).map((s) => [s.name, s]));
      const distribution = config.shiftBased?.distribution ?? {};
      return Object.values(distribution).some((shiftNames) =>
        (shiftNames ?? []).some((name) => {
          const shift = shiftsByName.get(name);
          if (!shift || shift.isUnavailable) return false;
          // New shape writes "price" directly; an older document instead
          // carries the 3-field legacy "pricing" map — either being real (> 0)
          // is enough, mirroring DataModels.kt's ShiftDefinition read path.
          if ((shift.price ?? 0) > 0) return true;
          const p = shift.pricing;
          return (p?.oneTimePrice ?? 0) > 0 || (p?.sameDayEveryWeekPrice ?? 0) > 0 || (p?.monthlyRecurrencePrice ?? 0) > 0;
        })
      );
    }
    case "DAY_BASED": {
      const distribution = config.dayBased?.distribution ?? {};
      return Object.values(distribution).some(
        (p) =>
          (p?.price ?? 0) > 0 ||
          (p?.oneTimePrice ?? 0) > 0 ||
          (p?.sameDayEachMonthPrice ?? 0) > 0 ||
          (p?.sameDayEachWeekPrice ?? 0) > 0
      );
    }
    default:
      return false;
  }
}

/**
 * Whether the whole space has real, priced availability. Documents written by
 * the current app always carry a "pricing" key (RentalPricingConfig.default()
 * is never omitted) — but a listing saved before that field existed, and never
 * re-saved since, has none; for those, price the first legacy formula through
 * pricingFromLegacyFormula (legacyPricing.ts), the same synthesis the app reads.
 */
function spaceHasRealPrice(listing: WorkspaceListingDoc): boolean {
  if (listing.pricing !== undefined) return structuredConfigHasRealPrice(listing.pricing);
  // Not yet migrated (migrateLegacyListingFields): price it exactly as the app reads it.
  if (Array.isArray(listing.rentalFormulas) && listing.rentalFormulas.length > 0) {
    return structuredConfigHasRealPrice(pricingFromLegacyFormula(listing.rentalFormulas[0]) as RentalPricingConfigDoc);
  }
  return false;
}

function subdivisionHasRealPrice(sub: SubdivisionDoc): boolean {
  // Per-attendee: slots carry availability markers (still > 0) and the price is the
  // room's own attendee tiers — both must exist.
  if (isPerAttendee(sub)) {
    return structuredConfigHasRealPrice(sub.pricing) && tiersFor(sub).length > 0;
  }
  if (sub.pricing !== undefined) return structuredConfigHasRealPrice(sub.pricing);
  return Array.isArray(sub.rentalStrategies) && sub.rentalStrategies.length > 0;
}

/**
 * The invariants an ACTIVE listing must actually satisfy — re-checked
 * server-side because two paths can land status at ACTIVE without ever going
 * through CreateListingDialog's own gates: a raw Firestore SDK write (rules
 * only check for an active package, never document completeness — see
 * hasActivePackage in firestore.rules) and autoPublishDraftIfNeeded
 * (entitlements.ts), which flips a Draft to ACTIVE
 * the instant a payment settles, whatever incomplete state that Draft was
 * saved in. Returns human-readable problems; empty means valid.
 */
/** The reason shown on a listing the single-listing plan can't keep published (owner, 22 Nov 2026). */
export const PLAN_LIMIT_REASON = "Your plan publishes 1 listing. Upgrade to ProHost Premium (unlimited) to publish more.";

export function validateListingForPublish(listing: WorkspaceListingDoc): string[] {
  const problems: string[] = [];

  // locationPinned === false: the draft was autosaved before the host dropped the pin, so lat/lng are only a guess.
  if (!listing.lat || !listing.lng || (listing as { locationPinned?: boolean }).locationPinned === false) {
    problems.push("Missing pinned map location.");
  }

  // "At least one photo" was previously enforced only by CreateListingDialog's
  // own Publish gate — a photo-less Draft could still auto-publish through the
  // quota/payment redirect path (autoPublishDraftIfNeeded) and go live with
  // zero images, since nothing server-side ever checked for one.
  if (!Array.isArray(listing.imageUrls) || listing.imageUrls.length === 0) {
    problems.push("Missing at least one listing photo.");
  }

  const subdivisions = listing.subdivisions ?? [];
  if (subdivisions.length === 0) {
    if (!spaceHasRealPrice(listing)) {
      problems.push("No priced availability configured.");
    }
  } else if (!subdivisions.some(subdivisionHasRealPrice)) {
    problems.push("No division has priced availability configured.");
  }

  return problems;
}

/**
 * Whenever a write leaves a listing ACTIVE (missing status also reads as
 * ACTIVE — its own default, same convention as isActiveStatus in
 * listingCountTracker.ts), re-validate it and demote back to DRAFT
 * immediately if it fails. This is the one enforcement point neither
 * firestore.rules nor the client wizard can be bypassed around.
 *
 * publishBlockedReasons is intentionally left client-writable (not added to
 * workspace_listings' protected-fields list): it's a UI hint, not a gate — a
 * host's next save naturally clears it (SpaceListing.toFirestoreMap always
 * writes the current, empty-by-default list), and if the listing is still
 * genuinely invalid this same trigger re-populates it truthfully on that
 * write. A client can't use it to fake compliance.
 *
 * No infinite loop: the demotion write sets status to DRAFT, so the
 * `status !== "ACTIVE"` guard below skips re-validating the write it just
 * made.
 */
export const onWorkspaceListingPublishValidation = onDocumentWritten(
  { document: "workspace_listings/{spaceId}", ...runAs("triggers") },
  async (event) => {
    const after = event.data?.after;
    if (!after?.exists) return;
    const data = after.data() as WorkspaceListingDoc;

    // Owner rule (28 Nov 2026): a slot a pending or accepted booking still uses can't be switched off. The app keeps such
    // slots on before saving (SlotUsage.kt); this puts them back for any other writer. The write retriggers this function,
    // which then finds nothing left to restore.
    const prior = event.data?.before?.exists ? (event.data.before.data() as SlotListingDoc) : null;
    const slotsTouched = (d: SlotListingDoc | WorkspaceListingDoc) =>
      JSON.stringify([(d as SlotListingDoc).pricing ?? null, (d as SlotListingDoc).subdivisions ?? null, (d as SlotListingDoc).schedule?.closedDates ?? null]);
    if (prior && slotsTouched(prior) !== slotsTouched(data)) {
      const snap = await getFirestore().collection("booking_requests")
        .where("spaceId", "==", event.params.spaceId).where("status", "in", ["PENDING", "ACCEPTED"]).get();
      const bookings = snap.docs.map((d) => ({ id: d.id, ...(d.data() as SlotBookingDoc) }));
      const { patch, kept } = keepUsedSlots(event.params.spaceId, prior, data as SlotListingDoc, bookings, todayIso());
      if (kept.length) {
        await after.ref.update({ ...patch, updatedAt: Date.now() });
        logger.info("listing_slots_kept", { spaceId: event.params.spaceId, kept });
        return;
      }
    }
    // Drafts / paused listings: keep the shown reasons current (clients may not write them), so a fixed draft
    // stops showing old ones.
    if (data.status !== undefined && data.status !== "ACTIVE") {
      const stored = (data as { publishBlockedReasons?: string[] }).publishBlockedReasons ?? [];
      if (stored.length) {
        const now = validateListingForPublish(data);
        const kept = stored.filter((r) => r === PLAN_LIMIT_REASON || now.includes(r));
        if (kept.length !== stored.length) await after.ref.update({ publishBlockedReasons: kept });
      }
      return;
    }

    const problems = validateListingForPublish(data);
    // The single-listing plan (ownerListingLimit on the owner's profile) publishes one listing at a time.
    const beforeData = event.data?.before?.exists ? (event.data.before.data() as WorkspaceListingDoc) : null;
    const justPublished = !beforeData || (beforeData.status !== undefined && beforeData.status !== "ACTIVE");
    if (problems.length === 0 && justPublished && typeof data.ownerId === "string" && data.ownerId) {
      const db = getFirestore();
      const owner = await db.collection("user_profiles").doc(data.ownerId).get();
      const limit = owner.data()?.ownerListingLimit;
      if (typeof limit === "number") {
        const live = await db.collection("workspace_listings").where("ownerId", "==", data.ownerId).where("status", "==", "ACTIVE").get();
        // Two listings published at the same moment see each other: only the later one (updatedAt, then id) goes back
        // to Draft, so the host keeps one live instead of none.
        const mine = Number((data as { updatedAt?: number }).updatedAt ?? 0);
        const others = live.docs.filter((d) => {
          if (d.id === event.params.spaceId) return false;
          const theirs = Number(d.data()?.updatedAt ?? 0);
          return theirs < mine || (theirs === mine && d.id < event.params.spaceId) || !mine;
        }).length;
        if (exceedsListingLimit(limit, others)) problems.push(PLAN_LIMIT_REASON);
      }
    }
    if (problems.length === 0) {
      if (((data as { publishBlockedReasons?: string[] }).publishBlockedReasons ?? []).length) {
        await after.ref.update({ publishBlockedReasons: [] });
      }
      return;
    }

    await after.ref.set(
      { status: "DRAFT", publishBlockedReasons: problems, updatedAt: Date.now() },
      { merge: true }
    );
    await recordAuditLog({
      actionType: "LISTING_DEMOTED_INVALID_PUBLISH",
      details: `Listing ${event.params.spaceId} moved back to Draft — failed server-side publish validation: ${problems.join(" ")}`,
      actorEmail: "system@prohost.app",
      severity: "SECURE",
    });
  }
);
