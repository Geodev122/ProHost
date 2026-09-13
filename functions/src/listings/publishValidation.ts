import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { recordAuditLog } from "../lib/auditLog";

/** Minimal shape of the parts of a workspace_listings document this validates —
 * mirrors SpaceListing.toFirestoreMap()'s key names (DataModels.kt), not the
 * full Kotlin model. */
interface RentalPricingConfigDoc {
  strategyType?: string;
  monthly?: { rateUsd?: number } | null;
  hourly?: { cellPrices?: Record<string, number> } | null;
  shiftBased?: {
    shifts?: Array<{
      name?: string;
      isUnavailable?: boolean;
      pricing?: { oneTimePrice?: number; sameDayEveryWeekPrice?: number; monthlyRecurrencePrice?: number };
    }>;
    distribution?: Record<string, string[]>;
  } | null;
  dayBased?: {
    distribution?: Record<string, { oneTimePrice?: number; sameDayEachMonthPrice?: number; sameDayEachWeekPrice?: number }>;
  } | null;
}

interface SubdivisionDoc {
  pricing?: RentalPricingConfigDoc;
  rentalStrategies?: unknown[];
}

interface WorkspaceListingDoc {
  status?: string;
  ownershipProofUrl?: string | null;
  lat?: number;
  lng?: number;
  pricing?: RentalPricingConfigDoc;
  rentalFormulas?: unknown[];
  subdivisions?: SubdivisionDoc[];
}

function structuredConfigHasRealPrice(config: RentalPricingConfigDoc | undefined | null): boolean {
  if (!config) return false;
  switch (config.strategyType) {
    case "MONTHLY":
      return (config.monthly?.rateUsd ?? 0) > 0;
    case "HOURLY":
      return Object.values(config.hourly?.cellPrices ?? {}).some((v) => (v ?? 0) > 0);
    case "SHIFT_BASED": {
      const shiftsByName = new Map((config.shiftBased?.shifts ?? []).map((s) => [s.name, s]));
      const distribution = config.shiftBased?.distribution ?? {};
      return Object.values(distribution).some((shiftNames) =>
        (shiftNames ?? []).some((name) => {
          const shift = shiftsByName.get(name);
          if (!shift || shift.isUnavailable) return false;
          const p = shift.pricing;
          return (p?.oneTimePrice ?? 0) > 0 || (p?.sameDayEveryWeekPrice ?? 0) > 0 || (p?.monthlyRecurrencePrice ?? 0) > 0;
        })
      );
    }
    case "DAY_BASED": {
      const distribution = config.dayBased?.distribution ?? {};
      return Object.values(distribution).some(
        (p) => (p?.oneTimePrice ?? 0) > 0 || (p?.sameDayEachMonthPrice ?? 0) > 0 || (p?.sameDayEachWeekPrice ?? 0) > 0
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
 * re-saved since, has none; for those, fall back to the legacy rentalFormulas
 * list actually being non-empty (the old model's own publish requirement),
 * mirroring the same backward-compat reasoning as
 * RentalPricingConfig.fromFirestoreMap's synthesis-from-legacy path.
 */
function spaceHasRealPrice(listing: WorkspaceListingDoc): boolean {
  if (listing.pricing !== undefined) return structuredConfigHasRealPrice(listing.pricing);
  return Array.isArray(listing.rentalFormulas) && listing.rentalFormulas.length > 0;
}

function subdivisionHasRealPrice(sub: SubdivisionDoc): boolean {
  if (sub.pricing !== undefined) return structuredConfigHasRealPrice(sub.pricing);
  return Array.isArray(sub.rentalStrategies) && sub.rentalStrategies.length > 0;
}

/**
 * The invariants an ACTIVE listing must actually satisfy — re-checked
 * server-side because two paths can land status at ACTIVE without ever going
 * through CreateListingDialog's own gates: a raw Firestore SDK write (rules
 * only check the package listing-limit quota, never document completeness —
 * see withinListingLimit in firestore.rules) and autoPublishDraftIfNeeded
 * (entitlements.ts), which flips a Draft to ACTIVE
 * the instant a payment settles, whatever incomplete state that Draft was
 * saved in. Returns human-readable problems; empty means valid.
 */
export function validateListingForPublish(listing: WorkspaceListingDoc): string[] {
  const problems: string[] = [];

  if (!listing.ownershipProofUrl || listing.ownershipProofUrl.trim().length === 0) {
    problems.push("Missing ownership or re-rental authorization document.");
  }

  if (!listing.lat || !listing.lng) {
    problems.push("Missing pinned map location.");
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
  "workspace_listings/{spaceId}",
  async (event) => {
    const after = event.data?.after;
    if (!after?.exists) return;
    const data = after.data() as WorkspaceListingDoc;
    if (data.status !== undefined && data.status !== "ACTIVE") return;

    const problems = validateListingForPublish(data);
    if (problems.length === 0) return;

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
