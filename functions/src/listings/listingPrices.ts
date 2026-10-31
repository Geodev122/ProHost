/**
 * Server mirror of SpaceCalculationUtils.lowestPriceFor / findLowestPriceForSubdivision
 * (app/src/main/java/com/example/ui/util/SpaceCalculationUtils.kt) — keep both identical
 * (listingPrices.test.ts pins the cases). Used by the Instagram agent's listing finder.
 */
import { isPerAttendee, tiersFor } from "../lib/attendeePricing";
import { pricingFromLegacyFormula } from "./legacyPricing";

type Data = Record<string, unknown>;
export type PriceUnit = "hour" | "shift" | "day" | "month" | "person";
export interface UnitPrice { unit: PriceUnit; amount: number }

const obj = (v: unknown): Data => (v && typeof v === "object" ? (v as Data) : {});
const pos = (v: unknown): number | null => (typeof v === "number" && Number.isFinite(v) && v > 0 ? v : null);
const minOf = (vals: Array<number | null>): number | null => {
  const ok = vals.filter((v): v is number => v !== null);
  return ok.length ? Math.min(...ok) : null;
};

/** The booking unit of a strategy (what the slots are sold by). */
export function unitOfStrategy(strategyType: unknown): PriceUnit {
  switch (strategyType) {
    case "HOURLY": return "hour";
    case "SHIFT_BASED": return "shift";
    case "DAY_BASED": return "day";
    default: return "month";
  }
}

/** Lowest configured price of one pricing config, in that config's own unit; null if unpriced. */
export function lowestPriceFor(pricing: unknown): UnitPrice | null {
  const p = obj(pricing);
  const type = p.strategyType ?? "MONTHLY";
  let amount: number | null = null;
  switch (type) {
    case "HOURLY": amount = minOf(Object.values(obj(obj(p.hourly).cellPrices)).map(pos)); break;
    case "SHIFT_BASED": amount = minOf((Array.isArray(obj(p.shiftBased).shifts) ? obj(p.shiftBased).shifts as unknown[] : []).map((s) => pos(obj(s).price))); break;
    case "DAY_BASED": amount = minOf(Object.values(obj(obj(p.dayBased).distribution)).map((d) => pos(obj(d).price))); break;
    default: amount = pos(obj(p.monthly).rateUsd);
  }
  return amount === null ? null : { unit: unitOfStrategy(type), amount };
}

/** A room's lowest price: per attendee for per-attendee rooms (their slot prices are availability markers), else its config. */
export function roomPrice(sub: unknown): UnitPrice | null {
  const s = obj(sub);
  if (isPerAttendee(s)) {
    const m = minOf(tiersFor(s).map((t) => pos(t.pricePerAttendeeUsd)));
    return m === null ? null : { unit: "person", amount: m };
  }
  return lowestPriceFor(s.pricing);
}

/** The whole listing's pricing config: `pricing`, else the legacy first formula (not-yet-migrated documents). */
export function listingPricing(d: Data): Data {
  if (d.pricing && typeof d.pricing === "object") return d.pricing as Data;
  const formulas = Array.isArray(d.rentalFormulas) ? d.rentalFormulas : [];
  return formulas.length ? pricingFromLegacyFormula(formulas[0]) : {};
}
