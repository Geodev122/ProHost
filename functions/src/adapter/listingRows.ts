import { getFirestore } from "firebase-admin/firestore";
import "../lib/admin";
import { isPublicListing } from "../website/sitemapXml";
import { listingPlace } from "../listings/listingLocation";
import { listingPricing, lowestPriceFor, roomPrice, unitOfStrategy } from "../listings/listingPrices";
import { OCCUPANCY_COLLECTION } from "../bookings/occupancyProjection";
import { ListingRow, RoomRow } from "../instagram/appAdapter";

/** ProHost side of AppAdapter.loadListings: listing documents become the catalogue rows the AI agent reads (moved out of instagram/aiListings.ts, P9 step 2). */
type Data = Record<string, unknown>;

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

/** Every publicly browsable listing (ACTIVE and isPublicListing) as rows, with accepted-booking counts from the occupancy projection. */
export async function loadListingRows(): Promise<ListingRow[]> {
  const db = getFirestore();
  const [snap, occ] = await Promise.all([
    db.collection("workspace_listings").where("status", "==", "ACTIVE").limit(2000).get(),
    db.collection(OCCUPANCY_COLLECTION).select("spaceId").limit(5000).get(),
  ]);
  const bookings = new Map<string, number>();
  occ.docs.forEach((o) => { const s = String(o.get("spaceId") ?? ""); if (s) bookings.set(s, (bookings.get(s) ?? 0) + 1); });
  return snap.docs.filter((d) => isPublicListing(d.data())).map((d) => toListingRow(d.id, d.data(), bookings.get(d.id) ?? 0));
}
