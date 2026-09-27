import { HttpsError } from "firebase-functions/v2/https";
import { onCall } from "../lib/callable";
import { getFirestore } from "firebase-admin/firestore";
import { recordAuditLog } from "../lib/auditLog";
import "../lib/admin";

// Mirrors the Kotlin enums in DataModels.kt (Governorate, SpaceType, Level2Type).
const GOVERNORATE_NAMES: Record<string, string> = {
  BEIRUT: "Beirut",
  MOUNT_LEBANON: "Mount Lebanon",
  NORTH: "North Lebanon (Tripoli/Batroun)",
  SOUTH: "South Lebanon (Sidon/Tyre)",
  BEKAA: "Bekaa (Zahle/Chtaura)",
  NABATIEH: "Nabatieh",
};
const SPACE_TYPE_NAMES: Record<string, string> = {
  PRIVATE_OFFICE: "Private Office",
  CENTER: "Center",
  POLYCLINIC: "Polyclinic",
  COWORKING_SPACE: "Co-working Space",
};
const DIVISION_TYPE_NAMES: Record<string, string> = {
  ROOMS: "Room",
  OFFICE: "Office",
  CONFERENCE_ROOM: "Conference Room",
  THEATER_TRAINING: "Theater / Training Room",
  DESK_IN_SHARED_AREA: "Desk in Shared Area",
  GYM: "Gym",
  TRAINING_ROOM: "Training Room",
  SPORTS_AREA: "Sports Area",
  STUDIO: "Studio",
  STORAGE: "Storage",
};

const UNKNOWN = "Unknown";
const DAY_MS = 24 * 60 * 60 * 1000;

function requireAdmin(auth: { token: Record<string, unknown> } | undefined): void {
  if (!auth) throw new HttpsError("unauthenticated", "Sign in required.");
  if (auth.token.role !== "ADMIN") throw new HttpsError("permission-denied", "Admins only.");
}

function isInLebanon(lat: unknown, lng: unknown): boolean {
  return typeof lat === "number" && typeof lng === "number" &&
    lat >= 33.0 && lat <= 34.7 && lng >= 35.1 && lng <= 36.7;
}

/** Country/city for a listing; listings created before worldwide geocoding have neither stored. */
function resolveLocation(d: FirebaseFirestore.DocumentData): { country: string; city: string } {
  const storedCountry = typeof d.country === "string" ? d.country.trim() : "";
  const storedCity = typeof d.city === "string" ? d.city.trim() : "";
  if (storedCountry) return { country: storedCountry, city: storedCity || UNKNOWN };
  if (isInLebanon(d.lat, d.lng)) {
    return { country: "Lebanon", city: storedCity || GOVERNORATE_NAMES[d.governorate as string] || UNKNOWN };
  }
  return { country: UNKNOWN, city: storedCity || UNKNOWN };
}

function spaceTypeLabel(d: FirebaseFirestore.DocumentData): string {
  const category = typeof d.spaceCategoryName === "string" ? d.spaceCategoryName.trim() : "";
  if (category) return category;
  return SPACE_TYPE_NAMES[d.spaceType as string] ?? UNKNOWN;
}

function inc(map: Record<string, number>, key: string, by = 1): void {
  map[key] = (map[key] ?? 0) + by;
}

interface AnalyticsRequest {
  fromMillis?: number | null;
  toMillis?: number | null;
  /** Null/empty = all countries. */
  country?: string | null;
}

/**
 * Admin-only platform analytics, computed server-side because the admin's live
 * listing feed is capped (FirestoreService) and would undercount.
 * - upgrades: Pro Host upgrades per UTC day (user_profiles.proHostUpgradedAtMillis).
 * - listings: published (ACTIVE) listings created in range, broken down by country,
 *   city, space type, and division type within each space type.
 */
export const getAdminAnalytics = onCall<AnalyticsRequest>(async (request) => {
  requireAdmin(request.auth);

  const from = typeof request.data?.fromMillis === "number" ? request.data.fromMillis : null;
  const to = typeof request.data?.toMillis === "number" ? request.data.toMillis : null;
  if (from != null && to != null && from > to) {
    throw new HttpsError("invalid-argument", "The start date must be before the end date.");
  }
  const countryFilter = request.data?.country?.trim() || null;
  const inRange = (millis: number) => (from == null || millis >= from) && (to == null || millis <= to);
  const db = getFirestore();

  // --- Pro Host upgrades ---
  const upgradesByDay: Record<string, number> = {};
  let upgradesTotal = 0;
  const upgradedSnap = await db.collection("user_profiles")
    .where("proHostUpgradedAtMillis", ">", 0)
    .select("proHostUpgradedAtMillis")
    .get();
  upgradedSnap.forEach((doc) => {
    const at = doc.get("proHostUpgradedAtMillis");
    if (typeof at !== "number" || !inRange(at)) return;
    upgradesTotal++;
    inc(upgradesByDay, String(Math.floor(at / DAY_MS) * DAY_MS));
  });
  const proHostsSnap = await db.collection("user_profiles").where("role", "==", "PRO_HOST")
    .select("proHostUpgradedAtMillis").get();
  const proHostsWithoutDate = proHostsSnap.docs.filter((d) => typeof d.get("proHostUpgradedAtMillis") !== "number").length;

  // --- Listing performance ---
  const byCountry: Record<string, number> = {};
  const byCity: Record<string, number> = {};
  const bySpaceType: Record<string, number> = {};
  const divisionsBySpaceType: Record<string, Record<string, number>> = {};
  const allCountries = new Set<string>();
  let listingsTotal = 0;
  let undatedListings = 0;

  const listingsSnap = await db.collection("workspace_listings")
    .select("status", "country", "city", "lat", "lng", "governorate", "spaceType", "spaceCategoryName", "subdivisions", "createdAtMillis")
    .get();
  listingsSnap.forEach((doc) => {
    const d = doc.data();
    if ((d.status ?? "ACTIVE") !== "ACTIVE") return;
    const { country, city } = resolveLocation(d);
    allCountries.add(country);
    const created = d.createdAtMillis;
    if (typeof created === "number") {
      if (!inRange(created)) return;
    } else if (from != null || to != null) {
      // No creation date: counted only in an all-time view.
      undatedListings++;
      return;
    }
    if (countryFilter && country !== countryFilter) return;

    listingsTotal++;
    inc(byCountry, country);
    inc(byCity, city);
    const typeLabel = spaceTypeLabel(d);
    inc(bySpaceType, typeLabel);
    const divisions = (divisionsBySpaceType[typeLabel] ??= {});
    const subs = Array.isArray(d.subdivisions) ? d.subdivisions : [];
    if (subs.length === 0) {
      inc(divisions, "Whole space");
    } else {
      subs.forEach((sub: unknown) => {
        const type = (sub as { type?: unknown } | null)?.type;
        inc(divisions, (typeof type === "string" && DIVISION_TYPE_NAMES[type]) || UNKNOWN);
      });
    }
  });

  return {
    upgrades: Object.entries(upgradesByDay)
      .map(([day, count]) => ({ dayMillis: Number(day), count }))
      .sort((a, b) => a.dayMillis - b.dayMillis),
    upgradesTotal,
    proHostsWithoutDate,
    listingsTotal,
    undatedListings,
    byCountry: countryFilter ? {} : byCountry,
    byCity,
    bySpaceType,
    divisionsBySpaceType,
    countries: [...allCountries].sort((a, b) => a.localeCompare(b)),
  };
});

/**
 * Admin-only, one-off: fills proHostUpgradedAtMillis for Pro Hosts upgraded before
 * that field existed, from the earliest matching audit event (ROLE_PROMOTED_PRO_HOST
 * or PACKAGE_GRANTED). Idempotent; never overwrites an existing date.
 */
export const backfillProHostUpgradeDates = onCall(async (request) => {
  requireAdmin(request.auth);
  const db = getFirestore();

  const earliestByUid = new Map<string, number>();
  const logs = await db.collection("audit_security_logs")
    .where("actionType", "in", ["ROLE_PROMOTED_PRO_HOST", "PACKAGE_GRANTED"])
    .select("details", "timestamp")
    .get();
  logs.forEach((doc) => {
    const details = String(doc.get("details") ?? "");
    const ts = doc.get("timestamp");
    const uid = /uid=([A-Za-z0-9]+)/.exec(details)?.[1] ?? /granted to ([A-Za-z0-9]+) \(/.exec(details)?.[1];
    if (!uid || typeof ts !== "number") return;
    const prev = earliestByUid.get(uid);
    if (prev == null || ts < prev) earliestByUid.set(uid, ts);
  });

  const proHosts = await db.collection("user_profiles").where("role", "==", "PRO_HOST")
    .select("proHostUpgradedAtMillis").get();
  const writer = db.bulkWriter();
  let updated = 0;
  let stillMissing = 0;
  proHosts.forEach((doc) => {
    if (typeof doc.get("proHostUpgradedAtMillis") === "number") return;
    const at = earliestByUid.get(doc.id);
    if (at == null) {
      stillMissing++;
      return;
    }
    updated++;
    writer.set(doc.ref, { proHostUpgradedAtMillis: at }, { merge: true });
  });
  await writer.close();

  await recordAuditLog({
    actionType: "PRO_HOST_UPGRADE_DATES_BACKFILLED",
    details: `Backfilled ${updated} Pro Host upgrade date(s); ${stillMissing} had no matching audit event.`,
    actorEmail: (request.auth?.token.email as string | undefined) ?? "system@prohost.app",
    severity: "INFO",
  });
  return { updated, stillMissing };
});
