import { HttpsError } from "firebase-functions/v2/https";
import { getFirestore } from "firebase-admin/firestore";
import { onCall } from "../lib/callable";
import { recordAuditLog } from "../lib/auditLog";
import { sendPushToUser } from "../lib/push";
import { isPerAttendee } from "../lib/attendeePricing";
import { hasEnded, todayIso } from "../bookings/bookingTerms";
import {
  PriceBookingDoc, PriceMode, PricingDoc, SlotKind, SlotRef, TierDoc,
  bookingUses, priceOf, reprice, round2, withSlotPrice, withTierPrice,
} from "../bookings/priceChange";
import "../lib/admin";

/**
 * Pro Host "Change price": sets one slot's price (or one per-attendee tier's price) on a listing
 * and applies the host's choice to every pending request / accepted booking that uses it —
 * KEEP (old price), NOW (remaining dates/months from today) or NEXT_TERM (next billing period).
 * Bookings are server-written because rules never let a client change a booking's total;
 * every affected specialist gets a PRICE_CHANGE push that opens My Rentals.
 */

const KINDS: SlotKind[] = ["MONTHLY", "HOURLY", "SHIFT", "DAY", "TIER"];
const MODES: PriceMode[] = ["KEEP", "NOW", "NEXT_TERM"];
const MAX_PRICE = 500000;
const HISTORY_LIMIT = 20;

interface ChangeSlotPriceData {
  spaceId?: string;
  ref?: { scopeId?: string; kind?: string; key?: string };
  newPrice?: number;
  decisions?: Record<string, string>;
}

interface SubDoc {
  id?: string;
  name?: string;
  pricing?: PricingDoc;
  pricingMode?: string;
  attendeeTiers?: TierDoc[];
  [k: string]: unknown;
}

interface Applied {
  bookingId: string;
  practitionerId?: string;
  displayCode?: string;
  mode: PriceMode;
  oldTotal: number;
  newTotal: number;
  effectiveFrom: string;
}

const DAY_NAMES: Record<string, string> = {
  Mon: "Monday", Tue: "Tuesday", Wed: "Wednesday", Thu: "Thursday", Fri: "Friday", Sat: "Saturday", Sun: "Sunday",
};

export function money(v: number): string {
  return `$${Number.isInteger(v) ? v.toFixed(0) : v.toFixed(2)}`;
}

/** Human label for a slot, e.g. "Morning shift", "Monday 09:00–10:00", "Monthly rent". */
export function slotLabel(ref: SlotRef, tiers: TierDoc[] | undefined): string {
  switch (ref.kind) {
  case "MONTHLY": return "Monthly rent";
  case "SHIFT": return `${ref.key.charAt(0)}${ref.key.slice(1).toLowerCase()} shift`;
  case "DAY": return DAY_NAMES[ref.key] ?? ref.key;
  case "HOURLY": {
    const [day, h] = ref.key.split("|");
    const hour = Number(h);
    const pad = (n: number) => `${String(n).padStart(2, "0")}:00`;
    return `${DAY_NAMES[day] ?? day} ${pad(hour)}–${pad(hour + 1)}`;
  }
  case "TIER": return `${(tiers ?? []).find((t) => t.id === ref.key)?.name ?? "Attendee package"} (per person)`;
  }
  return "Slot";
}

export const changeSlotPrice = onCall<ChangeSlotPriceData>(async (request) => {
  const auth = request.auth;
  if (!auth) throw new HttpsError("unauthenticated", "Sign in required.");
  const { spaceId, ref: rawRef, newPrice, decisions } = request.data ?? {};
  if (typeof spaceId !== "string" || spaceId.length === 0 || spaceId.length > 200) {
    throw new HttpsError("invalid-argument", "spaceId is required.");
  }
  if (!rawRef || typeof rawRef.scopeId !== "string" || !KINDS.includes(rawRef.kind as SlotKind) ||
    typeof rawRef.key !== "string" || rawRef.key.length > 100) {
    throw new HttpsError("invalid-argument", "A valid slot is required.");
  }
  if (typeof newPrice !== "number" || !Number.isFinite(newPrice) || newPrice <= 0 || newPrice > MAX_PRICE) {
    throw new HttpsError("invalid-argument", "Enter a price above 0.");
  }
  const price = round2(newPrice);
  const ref: SlotRef = { scopeId: rawRef.scopeId, kind: rawRef.kind as SlotKind, key: rawRef.key };
  const isAdmin = auth.token.role === "ADMIN";
  if (!isAdmin && auth.token.role !== "PRO_HOST") {
    throw new HttpsError("permission-denied", "Only Pro Hosts can change listing prices.");
  }

  const db = getFirestore();
  if (!isAdmin) {
    const profile = await db.collection("user_profiles").doc(auth.uid).get();
    if (profile.get("isSuspended") === true) {
      throw new HttpsError("permission-denied", "Your account is suspended.");
    }
  }

  const listingRef = db.collection("workspace_listings").doc(spaceId);
  const bookingsQuery = db.collection("booking_requests")
    .where("spaceId", "==", spaceId)
    .where("status", "in", ["PENDING", "ACCEPTED"]);
  const today = todayIso();

  const outcome = await db.runTransaction(async (tx) => {
    const listingSnap = await tx.get(listingRef);
    if (!listingSnap.exists) throw new HttpsError("not-found", "Listing not found.");
    const listing = listingSnap.data() ?? {};
    if (!isAdmin && listing.ownerId !== auth.uid) {
      throw new HttpsError("permission-denied", "You can only change prices on your own listings.");
    }
    const bookingsSnap = await tx.get(bookingsQuery);

    const subs = (Array.isArray(listing.subdivisions) ? listing.subdivisions : []) as SubDoc[];
    const subIndex = subs.findIndex((s) => s?.id === ref.scopeId);
    const isWholeSpace = ref.scopeId === spaceId && subs.length === 0;
    if (!isWholeSpace && subIndex < 0) throw new HttpsError("not-found", "That room no longer exists.");
    const sub = subIndex >= 0 ? subs[subIndex] : undefined;
    const pricing: PricingDoc = (sub ? sub.pricing : listing.pricing) ?? {};
    const tiers: TierDoc[] = sub?.attendeeTiers ?? [];
    const perAttendee = sub ? isPerAttendee(sub) : false;
    if (ref.kind === "TIER" ? !perAttendee : perAttendee) {
      throw new HttpsError("failed-precondition",
        perAttendee ? "This room is priced per person — change its attendee packages." : "This room has no attendee packages.");
    }
    if (ref.kind !== "TIER" && pricing.strategyType !== ({
      MONTHLY: "MONTHLY", HOURLY: "HOURLY", SHIFT: "SHIFT_BASED", DAY: "DAY_BASED",
    } as Record<string, string>)[ref.kind]) {
      throw new HttpsError("failed-precondition", "This slot's pricing changed meanwhile. Reopen Change price.");
    }
    const oldPrice = priceOf(pricing, tiers, ref);
    if (oldPrice === null) throw new HttpsError("not-found", "That slot is no longer offered.");
    if (round2(oldPrice) === price) throw new HttpsError("invalid-argument", "That is already the current price.");

    // 1. The listing: exactly one price changes; every other key (displayCode included) is kept.
    const label = slotLabel(ref, tiers);
    if (sub) {
      const nextSubs = subs.map((s, i) => {
        if (i !== subIndex) return s;
        return ref.kind === "TIER" ?
          { ...s, attendeeTiers: withTierPrice(tiers, ref, price) } :
          { ...s, pricing: withSlotPrice(pricing, ref, price) };
      });
      tx.update(listingRef, { subdivisions: nextSubs, updatedAt: Date.now() });
    } else {
      tx.update(listingRef, { pricing: withSlotPrice(pricing, ref, price), updatedAt: Date.now() });
    }

    // 2. Bookings that use this slot: the host's decision per booking (default KEEP).
    const applied: Applied[] = [];
    for (const doc of bookingsSnap.docs) {
      const b = doc.data() as PriceBookingDoc & {
        practitionerId?: string; displayCode?: string; priceChanges?: unknown[];
      };
      if (hasEnded(b, today) || !bookingUses(b, spaceId, pricing, ref)) continue;
      const requested = decisions?.[doc.id];
      const mode: PriceMode = MODES.includes(requested as PriceMode) ? requested as PriceMode : "KEEP";
      const oldTotal = round2(b.totalAmountUsd ?? 0);
      const r = reprice(b, pricing, ref, oldPrice, price, mode, today);
      const change = {
        at: Date.now(),
        slotLabel: label,
        oldPrice: round2(oldPrice),
        newPrice: price,
        mode,
        oldTotal,
        newTotal: r.newTotal,
        effectiveFrom: r.effectiveFrom,
      };
      const history = (Array.isArray(b.priceChanges) ? b.priceChanges : []).slice(-(HISTORY_LIMIT - 1));
      const update: Record<string, unknown> = { lastPriceChange: change, priceChanges: [...history, change] };
      if (r.newTotal !== oldTotal) update.totalAmountUsd = r.newTotal;
      if (ref.kind === "TIER" && mode === "NOW" && r.units > 0) update.attendeePackagePriceUsd = price;
      tx.update(doc.ref, update);
      applied.push({
        bookingId: doc.id, practitionerId: b.practitionerId, displayCode: b.displayCode,
        mode, oldTotal, newTotal: r.newTotal, effectiveFrom: r.effectiveFrom,
      });
    }
    return { applied, oldPrice: round2(oldPrice), label, title: String(listing.title ?? "your space"), roomName: sub?.name };
  });

  // Side effects once, after commit.
  const place = outcome.roomName ? `${outcome.title} · ${outcome.roomName}` : outcome.title;
  for (const a of outcome.applied) {
    if (!a.practitionerId) continue;
    const change = `${outcome.label} from ${money(outcome.oldPrice)} to ${money(price)}`;
    let body: string;
    if (a.mode === "KEEP") {
      body = `Your host changed ${change}. Your booking keeps its current price: ${money(a.oldTotal)}.`;
    } else if (a.newTotal === a.oldTotal) {
      body = `Your host changed ${change}. Your current booking stays at ${money(a.oldTotal)}; the new price applies when you renew.`;
    } else {
      const when = a.effectiveFrom && a.effectiveFrom > today ? `from ${a.effectiveFrom}` : "from today";
      body = `Your host changed ${change}. Your total is now ${money(a.newTotal)} (was ${money(a.oldTotal)}), ${when}. ` +
        "Not OK? You can withdraw or cancel it in My Rentals.";
    }
    await sendPushToUser(a.practitionerId, `Price update · ${place}`, body, {
      category: "PRICE_CHANGE",
      targetTab: "pro_rentals",
      bookingId: a.bookingId,
      spaceId,
    });
  }
  const counts = { keep: 0, now: 0, nextTerm: 0 };
  for (const a of outcome.applied) {
    if (a.mode === "KEEP") counts.keep++; else if (a.mode === "NOW") counts.now++; else counts.nextTerm++;
  }
  await recordAuditLog({
    actionType: "LISTING_PRICE_CHANGED",
    details: `${place}: ${outcome.label} ${money(outcome.oldPrice)} → ${money(price)}. ` +
      `Bookings: ${counts.now} now, ${counts.nextTerm} next term, ${counts.keep} kept.`,
    actorEmail: auth.token.email ?? auth.uid,
    severity: "INFO",
  });
  return { oldPrice: outcome.oldPrice, newPrice: price, affected: outcome.applied.length, ...counts };
});
