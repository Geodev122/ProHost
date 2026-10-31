/**
 * Server port of DataModels.kt RentalPricingConfig.fromLegacyFormula: the structured
 * `pricing` map a listing saved with only the legacy `rentalFormulas` list is read as.
 * migrateLegacyListingFields writes it so the legacy field can be deleted, and
 * publishValidation uses it for not-yet-migrated documents. Keep both sides identical
 * (legacyPricing.test.ts pins the shapes).
 */
type Data = Record<string, unknown>;

const SHIFT_NAMES = ["MORNING", "MID", "EVENING", "NIGHT"] as const;

function hourOf(value: unknown, fallback: number): number {
  const n = parseInt(String(value ?? "").split(":")[0].trim(), 10);
  return Number.isFinite(n) ? n : fallback;
}

function monthlyMap(rate: number): Data {
  return {
    strategyType: "MONTHLY",
    monthly: { rateUsd: rate, fromMonth: 1, fromYear: 2026, isIndefinite: true, toMonth: null, toYear: null, excludedRanges: [] },
    hourly: null,
    shiftBased: null,
    dayBased: null,
  };
}

/** `pricing` for a legacy formula map (null/invalid → the app's default MONTHLY config). */
export function pricingFromLegacyFormula(formula: unknown): Data {
  if (!formula || typeof formula !== "object") return monthlyMap(0);
  const f = formula as Data;
  const type = typeof f.type === "string" ? f.type : "FULL_MONTH";
  const rate = typeof f.rateUsd === "number" ? f.rateUsd : 300;
  const days = Array.isArray(f.daysOfWeek)
    ? f.daysOfWeek.filter((d): d is string => typeof d === "string")
    : ["Mon", "Tue", "Wed", "Thu", "Fri"];
  const start = hourOf(f.startHour ?? "08:00", 8);
  const end = hourOf(f.endHour ?? "18:00", 18);

  switch (type) {
    case "HOURLY": {
      const cellPrices: Record<string, number> = {};
      for (const day of days) for (let h = start; h < end; h++) cellPrices[`${day}|${h}`] = rate;
      return { strategyType: "HOURLY", monthly: null, hourly: { cellPrices }, shiftBased: null, dayBased: null };
    }
    case "SHIFT":
      return {
        strategyType: "SHIFT_BASED",
        monthly: null,
        hourly: null,
        shiftBased: {
          shifts: SHIFT_NAMES.map((name) => ({
            name, startHour: start, endHour: end, isUnavailable: name !== "MORNING", price: rate,
          })),
          distribution: Object.fromEntries(days.map((d) => [d, ["MORNING"]])),
        },
        dayBased: null,
      };
    case "DAY_PER_WEEK":
      return {
        strategyType: "DAY_BASED",
        monthly: null,
        hourly: null,
        shiftBased: null,
        dayBased: {
          useFacilityHours: false,
          customStartHour: start,
          customEndHour: end,
          distribution: Object.fromEntries(days.map((d) => [d, { price: rate }])),
        },
      };
    default:
      return monthlyMap(rate);
  }
}
