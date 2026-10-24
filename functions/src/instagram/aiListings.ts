import { getFirestore } from "firebase-admin/firestore";
import "../lib/admin";
import { isPublicListing } from "../website/sitemapXml";
import { listingPlace } from "../listings/listingLocation";
import { PriceUnit, listingPricing, lowestPriceFor, roomPrice, unitOfStrategy } from "../listings/listingPrices";
import { OCCUPANCY_COLLECTION } from "../bookings/occupancyProjection";

/**
 * The DM agent's read-only view of the live ProHost catalogue: every publicly browsable listing (isPublicListing) with its
 * rooms, prices, place and usage analytics (views, inquiries, saves, accepted bookings). Never owner names, phones or emails.
 * `searchListings` (pure, unit-tested) matches a person's need; links open the app on the listing (pro-host.tech/listing/{id}).
 */

type Data = Record<string, unknown>;
export interface RoomRow {
  id: string; code: string; name: string; type: string; capacity: number | null;
  /** How its slots are booked (hour, shift, day, month). */ offered: PriceUnit;
  price: number | null; priceUnit: PriceUnit | null; amenities: string[];
}
export interface ListingRow {
  id: string; code: string; title: string; category: string; country: string; city: string; /** English copy of a city typed in another language (app 1.0.45). */ cityEn: string; area: string; verified: boolean;
  rooms: RoomRow[]; features: string[]; views: number; inquiries: number; favorites: number; bookings: number; createdAt: number;
}

const ROOM_TYPES: Record<string, string> = {
  ROOMS: "Room", OFFICE: "Office", CONFERENCE_ROOM: "Meeting room", THEATER_TRAINING: "Training hall", DESK_IN_SHARED_AREA: "Desk",
  GYM: "Gym", TRAINING_ROOM: "Training room", SPORTS_AREA: "Sports area", STUDIO: "Studio", STORAGE: "Storage",
};
const SPACE_TYPES: Record<string, string> = { PRIVATE_OFFICE: "Private office", CENTER: "Center", POLYCLINIC: "Polyclinic", COWORKING_SPACE: "Coworking space" };

const str = (v: unknown, n = 120) => (typeof v === "string" ? v.replace(/\s+/g, " ").trim().slice(0, n) : "");
const num = (v: unknown) => (typeof v === "number" && Number.isFinite(v) ? v : 0);
const strs = (v: unknown) => (Array.isArray(v) ? v.map((x) => str(x, 60)).filter(Boolean) : []);

/** One listing document → its catalogue row (pure). */
export function toListingRow(id: string, d: Data, bookings = 0): ListingRow {
  const place = listingPlace(d);
  const category = str(d.spaceCategoryName) || SPACE_TYPES[str(d.spaceType)] || "Workspace";
  const subs = Array.isArray(d.subdivisions) ? (d.subdivisions as Data[]) : [];
  const rooms: RoomRow[] = subs.length
    ? subs.map((s) => {
      const p = roomPrice(s);
      const pricing = (s.pricing ?? {}) as Data;
      return {
        id: str(s.id, 80), code: str(s.displayCode, 12), name: str(s.name, 60) || ROOM_TYPES[str(s.type)] || "Room", type: ROOM_TYPES[str(s.type)] || "Room",
        capacity: num(s.capacity) > 0 ? num(s.capacity) : null, offered: unitOfStrategy(pricing.strategyType),
        price: p?.amount ?? null, priceUnit: p?.unit ?? null, amenities: strs(s.amenities).slice(0, 8),
      };
    })
    : [(() => {
      const pricing = listingPricing(d);
      const p = lowestPriceFor(pricing);
      return { id: "", code: "", name: str(d.title, 60), type: category, capacity: num(d.capacity) > 0 ? num(d.capacity) : null, offered: unitOfStrategy(pricing.strategyType), price: p?.amount ?? null, priceUnit: p?.unit ?? null, amenities: [] };
    })()];
  const equipment = Array.isArray(d.equipment) ? (d.equipment as Data[]).map((e) => str(e.name, 40)).filter(Boolean) : [];
  return {
    id, code: str(d.displayCode, 12), title: str(d.title, 80) || category, category, country: place.country, city: str(d.city, 60) || place.locality, cityEn: str(d.cityEn, 60),
    area: str(d.district, 60), verified: d.isVerified === true, rooms,
    features: [...strs(d.essentialFacilities), ...equipment].slice(0, 14),
    views: num(d.avatarEngagementViews), inquiries: num(d.avatarInquiryClicks), favorites: num(d.favoriteCount), bookings, createdAt: num(d.createdAtMillis),
  };
}

let cache: { rows: ListingRow[]; at: number } | null = null;
const TTL = 10 * 60_000;

/** All publicly browsable listings as rows, cached 10 minutes per instance. */
export async function listingsCatalog(now: number): Promise<ListingRow[]> {
  if (cache && now - cache.at < TTL) return cache.rows;
  const db = getFirestore();
  const [snap, occ] = await Promise.all([
    db.collection("workspace_listings").where("status", "==", "ACTIVE").limit(2000).get(),
    db.collection(OCCUPANCY_COLLECTION).select("spaceId").limit(5000).get(),
  ]);
  const bookings = new Map<string, number>();
  occ.docs.forEach((o) => { const s = String(o.get("spaceId") ?? ""); if (s) bookings.set(s, (bookings.get(s) ?? 0) + 1); });
  const rows = snap.docs.filter((d) => isPublicListing(d.data())).map((d) => toListingRow(d.id, d.data(), bookings.get(d.id) ?? 0));
  cache = { rows, at: now };
  return rows;
}

// ---- matching (pure) ----

export const KINDS = ["clinic", "therapy", "office", "desk", "meeting", "training", "studio", "gym", "space"] as const;
export type Kind = typeof KINDS[number];
const KIND_MATCH: Record<Exclude<Kind, "space">, { types: string[]; words: string[] }> = {
  clinic: { types: ["Room"], words: ["clinic", "medical", "polyclinic", "doctor", "dental", "dentist", "عيادة", "طبي", "طبية"] },
  therapy: { types: ["Room"], words: ["therapy", "physio", "psycho", "counsel", "massage", "wellness", "علاج", "نفسي"] },
  office: { types: ["Office", "Desk"], words: ["office", "coworking", "bureau", "مكتب"] },
  desk: { types: ["Desk"], words: ["desk", "coworking", "shared", "hot desk"] },
  meeting: { types: ["Meeting room"], words: ["meeting", "conference", "boardroom", "اجتماع"] },
  training: { types: ["Training room", "Training hall"], words: ["training", "workshop", "class", "seminar", "lecture", "تدريب"] },
  studio: { types: ["Studio"], words: ["studio", "photo", "podcast", "dance", "استوديو"] },
  gym: { types: ["Gym", "Sports area"], words: ["gym", "sport", "fitness", "yoga", "pilates", "رياضة"] },
};
const UNITS: PriceUnit[] = ["hour", "shift", "day", "month", "person"];

export interface SearchCriteria {
  kind?: Kind; city?: string; country?: string; unit?: PriceUnit; attendees?: number; maxPrice?: number; features?: string[];
  /** Listing ids already sent: "show me more" skips them. */ exclude?: string[];
}

/** The model's `search` object, merged over what was already known (pure; unknown values dropped). */
export function cleanCriteria(raw: unknown, prev: SearchCriteria = {}): SearchCriteria {
  const r = (raw && typeof raw === "object" ? raw : {}) as Data;
  const out: SearchCriteria = { ...prev };
  const kind = str(r.kind, 20).toLowerCase();
  if ((KINDS as readonly string[]).includes(kind)) out.kind = kind as Kind;
  const city = str(r.city, 60); if (city) out.city = city;
  const country = str(r.country, 60); if (country) out.country = country;
  const unit = str(r.unit, 10).toLowerCase(); if ((UNITS as string[]).includes(unit)) out.unit = unit as PriceUnit;
  const att = Math.round(num(r.attendees)); if (att > 0 && att < 1000) out.attendees = att;
  const max = num(r.maxPrice); if (max > 0) out.maxPrice = max;
  const feats = strs(r.features).slice(0, 5); if (feats.length) out.features = feats;
  return out;
}

const norm = (s: string) => s.normalize("NFD").replace(/[̀-ًͯ-ٟ]/g, "").toLowerCase().replace(/\s+/g, " ").trim();
const has = (hay: string, needle: string) => !!needle && norm(hay).includes(norm(needle));

export interface Match { listing: ListingRow; room: RoomRow; score: number; reasons: string[] }

function roomFits(l: ListingRow, r: RoomRow, c: SearchCriteria): { ok: boolean; score: number; reasons: string[] } {
  let score = 0;
  const reasons: string[] = [];
  if (c.kind && c.kind !== "space") {
    const k = KIND_MATCH[c.kind];
    const text = `${l.title} ${l.category} ${r.name}`;
    const typeHit = k.types.includes(r.type);
    const wordHit = k.words.some((w) => has(text, w));
    // a generic "Room" only counts for clinic / therapy when the listing itself reads medical / therapeutic
    if (!(wordHit || (typeHit && r.type !== "Room"))) return { ok: false, score: 0, reasons };
    score += 25; reasons.push(`fits ${c.kind}`);
  }
  if (c.unit) {
    const units = [r.offered, r.priceUnit];
    if (!units.includes(c.unit)) return { ok: false, score: 0, reasons };
    score += 15; reasons.push(`by the ${c.unit}`);
  }
  if (c.attendees && c.attendees > 1) {
    if (r.capacity !== null && r.capacity < c.attendees) return { ok: false, score: 0, reasons };
    if (r.capacity !== null) { score += 10; reasons.push(`fits ${c.attendees} people`); }
  }
  if (c.maxPrice && r.price !== null && (!c.unit || r.priceUnit === c.unit)) {
    if (r.price > c.maxPrice) return { ok: false, score: 0, reasons };
    score += 10; reasons.push("within budget");
  }
  for (const f of c.features ?? []) if ([...l.features, ...r.amenities].some((x) => has(x, f))) { score += 5; reasons.push(`has ${f}`); }
  if (r.price !== null) score += 2;
  return { ok: true, score, reasons };
}

/** Best matches for a need: hard filters (place, kind, unit, capacity, budget), then fit, verified, popularity, freshness. */
export function searchListings(rows: ListingRow[], c: SearchCriteria, now = Date.now(), limit = 5): Match[] {
  const out: Match[] = [];
  for (const l of rows) {
    if (c.exclude?.includes(l.id)) continue;
    if (c.country && !(has(l.country, c.country) || has(c.country, l.country))) continue;
    let base = 0;
    const reasons: string[] = [];
    if (c.city) {
      if (![l.city, l.cityEn, l.area, l.title].some((x) => has(x, c.city!) || (!!x && has(c.city!, x)))) continue;
      base += 30; reasons.push(`in ${l.city || c.city}`);
    }
    let best: { room: RoomRow; score: number; reasons: string[] } | null = null;
    for (const r of l.rooms) {
      const fit = roomFits(l, r, c);
      if (fit.ok && (!best || fit.score > best.score)) best = { room: r, score: fit.score, reasons: fit.reasons };
    }
    if (!best) continue;
    const popularity = Math.min(10, l.bookings * 2 + Math.min(5, l.favorites) + l.inquiries * 0.5 + l.views * 0.02);
    const fresh = l.createdAt && now - l.createdAt < 60 * 86_400_000 ? 2 : 0;
    out.push({ listing: l, room: best.room, score: base + best.score + (l.verified ? 5 : 0) + popularity + fresh, reasons: [...reasons, ...best.reasons] });
  }
  return out.sort((a, b) => b.score - a.score).slice(0, limit);
}

const UNIT_LABEL: Record<PriceUnit, string> = { hour: "hour", shift: "shift", day: "day", month: "month", person: "person" };

/** The trusted LISTING RESULTS block for the model (prices exactly as stored, USD). */
export function formatMatches(matches: Match[], c: SearchCriteria): string {
  const wanted = Object.entries(c).filter(([k, v]) => k !== "exclude" && v !== undefined && v !== "" && !(Array.isArray(v) && !v.length)).map(([k, v]) => `${k}=${Array.isArray(v) ? v.join("/") : v}`).join(", ") || "nothing specific";
  if (!matches.length) {
    return `LISTING RESULTS (trusted, live ProHost catalogue)\nSearched for: ${wanted}.\nNO MATCH. Say so honestly, suggest relaxing ONE thing (area, unit or budget), offer the Explore map in the app (download button). Never invent a listing.`;
  }
  const lines = matches.map((m, i) => {
    const l = m.listing, r = m.room;
    const price = r.price !== null && r.priceUnit ? `from $${r.price} per ${UNIT_LABEL[r.priceUnit]}` : "price shown in the app";
    const usage = [l.bookings ? `${l.bookings} booking${l.bookings === 1 ? "" : "s"}` : "", l.favorites >= 3 ? `${l.favorites} saved` : "", l.views ? `${l.views} views` : ""].filter(Boolean).join(", ");
    return `${i + 1}. id=${l.id} | "${l.title}" (${l.category}) | ${[l.area, l.city, l.country].filter(Boolean).join(", ")} | room "${r.name}" (${r.type}${r.capacity ? `, up to ${r.capacity} people` : ""}, booked by the ${UNIT_LABEL[r.offered]}) | ${price}${l.verified ? " | verified" : ""}${usage ? ` | ${usage}` : ""}${l.features.length ? ` | has: ${l.features.slice(0, 5).join(", ")}` : ""} | why: ${m.reasons.join("; ") || "closest match"}`;
  });
  return `LISTING RESULTS (trusted, live ProHost catalogue)\nSearched for: ${wanted}.\n${lines.join("\n")}\nPresent the best 1-3 in your own words (why each fits THEM, price exactly as written), put their ids in "listings" in the order you mention them (the server adds a "View" button per listing that opens it in the ProHost app), and ask ONE follow-up question (e.g. which one they like, or a date).`;
}

/** Short overview so the agent knows what exists before asking (cities, kinds, units). */
export function catalogOverview(rows: ListingRow[]): string {
  if (!rows.length) return "LIVE LISTINGS: none published right now. If they want a space, say listings are being added and offer to notify them / the download button; never invent one.";
  const count = (vals: string[]) => [...vals.reduce((m, v) => m.set(v, (m.get(v) ?? 0) + 1), new Map<string, number>())].sort((a, b) => b[1] - a[1]).slice(0, 12).map(([k, n]) => `${k} (${n})`).join(", ");
  const rooms = rows.flatMap((r) => r.rooms);
  return `LIVE LISTINGS (trusted): ${rows.length} published listing${rows.length === 1 ? "" : "s"}, ${rooms.length} bookable room${rooms.length === 1 ? "" : "s"}. ` +
    `Places: ${count(rows.map((r) => [r.city, r.country].filter(Boolean).join(", ") || "unknown"))}. Kinds: ${count(rows.map((r) => r.category))}. ` +
    `Room types: ${count(rooms.map((r) => r.type))}. Booked by: ${count(rooms.map((r) => r.offered))}.`;
}

const LISTING_BASE = "https://pro-host.tech/listing/";
/** The link that opens the app on that listing (and room); the landing page hands over to the app from Instagram's browser. */
export function listingUrl(listingId: string, roomId = ""): string {
  const q = [roomId ? `room=${encodeURIComponent(roomId)}` : "", "utm_source=instagram", "utm_medium=dm", "utm_campaign=ai_agent"].filter(Boolean).join("&");
  return `${LISTING_BASE}${encodeURIComponent(listingId)}?${q}`;
}

/** Up to 3 "View …" buttons for the listings the model chose — only ids that were really in the results. */
export function listingButtons(matches: Match[], ids: string[]): Array<{ label: string; url: string }> {
  const byId = new Map(matches.map((m) => [m.listing.id, m]));
  const chosen = [...new Set(ids)].map((id) => byId.get(id)).filter((m): m is Match => !!m);
  const picks = chosen.length ? chosen : matches.slice(0, 1);
  return picks.slice(0, 3).map((m) => {
    const t = m.listing.title.trim();
    return { label: t.length > 20 ? t.slice(0, 19).trimEnd() + "…" : t, url: listingUrl(m.listing.id, m.room.id) };
  });
}
