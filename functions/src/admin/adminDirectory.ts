import { HttpsError, CallableRequest } from "firebase-functions/v2/https";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import {
  DocumentReference,
  DocumentSnapshot,
  FieldPath,
  GeoPoint,
  Query,
  Timestamp,
  getFirestore,
} from "firebase-admin/firestore";
import { getAuth } from "firebase-admin/auth";
import { onCall } from "../lib/callable";
import { DISPLAY_CODES_COLLECTION } from "../lib/displayCode";
import { classifyAdminQuery, normalizeSearchName } from "./adminQuery";
import "../lib/admin";

/**
 * Admin Console data, server-side and on demand: the console never loads whole collections.
 *  - adminCounts       real totals (count() aggregations) for the metric tiles
 *  - adminSearch       smart search by name / email / U-L-D-B code / UID or doc id
 *  - adminUserDossier  everything about one user (listings + rooms, subscriptions, rentals)
 *  - adminExportUsers  paged export of every user for the CSV directory
 *  - searchName        lower-cased name/title kept on profiles and listings for prefix search
 */

const USERS = "user_profiles";
const LISTINGS = "workspace_listings";
const BOOKINGS = "booking_requests";
const SUBSCRIPTIONS = "subscriptions";
const RESULT_LIMIT = 20;
const EXPORT_PAGE = 500;

type Data = Record<string, unknown>;

function requireAdmin(request: CallableRequest<unknown>): void {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in required.");
  if (request.auth.token.role !== "ADMIN") throw new HttpsError("permission-denied", "Admins only.");
}

/** Firestore values → plain JSON the app's fromFirestoreMap parsers read (timestamps → millis). */
function plain(value: unknown): unknown {
  if (value instanceof Timestamp) return value.toMillis();
  if (value instanceof GeoPoint) return { latitude: value.latitude, longitude: value.longitude };
  if (value instanceof DocumentReference) return value.path;
  if (Array.isArray(value)) return value.map(plain);
  if (value && typeof value === "object") {
    const out: Data = {};
    for (const [k, v] of Object.entries(value as Data)) out[k] = plain(v);
    return out;
  }
  return value;
}

function docOut(snap: DocumentSnapshot): Data | null {
  if (!snap.exists) return null;
  return { id: snap.id, ...(plain(snap.data()) as Data) };
}

async function countOf(query: Query): Promise<number> {
  return (await query.count().get()).data().count;
}

// ---------------------------------------------------------------------------------------
// Counts
// ---------------------------------------------------------------------------------------

export const adminCounts = onCall(async (request) => {
  requireAdmin(request);
  const db = getFirestore();
  const users = db.collection(USERS);
  const listings = db.collection(LISTINGS);
  const bookings = db.collection(BOOKINGS);
  const thirtyDaysAgo = Date.now() - 30 * 24 * 60 * 60 * 1000;
  const [
    totalUsers, specialists, proHosts, admins, newUsers30d,
    totalListings, activeListings, draftListings, pendingReview,
    totalBookings, pendingBookings, acceptedBookings,
    demoUsers, demoListings, demoBookings,
  ] = await Promise.all([
    countOf(users),
    countOf(users.where("role", "==", "SPECIALIST")),
    countOf(users.where("role", "==", "PRO_HOST")),
    countOf(users.where("role", "==", "ADMIN")),
    countOf(users.where("createdAtMillis", ">=", thirtyDaysAgo)),
    countOf(listings),
    countOf(listings.where("status", "==", "ACTIVE")),
    countOf(listings.where("status", "==", "DRAFT")),
    countOf(listings.where("isVerified", "==", false).where("verificationRequestedAt", ">", 0)),
    countOf(bookings),
    countOf(bookings.where("status", "==", "PENDING")),
    countOf(bookings.where("status", "==", "ACCEPTED")),
    countOf(users.where("isDemo", "==", true)),
    countOf(listings.where("isDemo", "==", true)),
    countOf(bookings.where("isDemo", "==", true)),
  ]);
  return {
    totalUsers, specialists, proHosts, admins, newUsers30d,
    totalListings, activeListings, draftListings, pendingReview,
    totalBookings, pendingBookings, acceptedBookings,
    demoUsers, demoListings, demoBookings,
  };
});

// ---------------------------------------------------------------------------------------
// Search
// ---------------------------------------------------------------------------------------

type Kind = "users" | "listings" | "bookings" | "all";

interface SearchResult {
  users: Data[];
  listings: Data[];
  bookings: Data[];
}

function pushUnique(list: Data[], item: Data | null): void {
  if (item && !list.some((x) => x.id === item.id) && list.length < RESULT_LIMIT) list.push(item);
}

async function byPrefix(collection: string, prefix: string): Promise<Data[]> {
  if (!prefix) return [];
  const snap = await getFirestore()
    .collection(collection)
    .where("searchName", ">=", prefix)
    .where("searchName", "<", prefix + "")
    .limit(RESULT_LIMIT)
    .get();
  return snap.docs.map((d) => docOut(d)).filter((d): d is Data => d !== null);
}

async function usersByEmail(email: string): Promise<Data[]> {
  const db = getFirestore();
  const found: Data[] = [];
  const snap = await db.collection(USERS).where("email", "==", email).limit(RESULT_LIMIT).get();
  snap.docs.forEach((d) => pushUnique(found, docOut(d)));
  if (found.length === 0) {
    // Profiles may store the address with its original casing: resolve through Auth.
    try {
      const user = await getAuth().getUserByEmail(email);
      pushUnique(found, docOut(await db.collection(USERS).doc(user.uid).get()));
    } catch {
      // No such account.
    }
  }
  return found;
}

async function listingsOfOwners(ownerIds: string[]): Promise<Data[]> {
  const db = getFirestore();
  const out: Data[] = [];
  for (const uid of ownerIds.slice(0, 10)) {
    const snap = await db.collection(LISTINGS).where("ownerId", "==", uid).limit(RESULT_LIMIT).get();
    snap.docs.forEach((d) => pushUnique(out, docOut(d)));
  }
  return out;
}

async function bookingsOfPeople(uids: string[]): Promise<Data[]> {
  const db = getFirestore();
  const out: Data[] = [];
  for (const uid of uids.slice(0, 10)) {
    for (const field of ["practitionerId", "ownerId"]) {
      const snap = await db.collection(BOOKINGS).where(field, "==", uid).limit(RESULT_LIMIT).get();
      snap.docs.forEach((d) => pushUnique(out, docOut(d)));
    }
  }
  return out;
}

export async function runAdminSearch(rawQuery: unknown, kind: Kind): Promise<SearchResult> {
  const db = getFirestore();
  const result: SearchResult = { users: [], listings: [], bookings: [] };
  const want = (k: Exclude<Kind, "all">) => kind === "all" || kind === k;
  const q = classifyAdminQuery(rawQuery);

  if (q.type === "empty") return result;

  if (q.type === "code") {
    const reg = await db.collection(DISPLAY_CODES_COLLECTION).doc(q.code).get();
    const targetPath = reg.data()?.targetPath;
    if (typeof targetPath !== "string") return result;
    const docPath = targetPath.split("#")[0]; // D- codes point at "listing#roomId"
    const doc = docOut(await db.doc(docPath).get());
    if (!doc) return result;
    if (q.prefix === "U") pushUnique(result.users, doc);
    if (q.prefix === "L" || q.prefix === "D") pushUnique(result.listings, doc);
    if (q.prefix === "B") pushUnique(result.bookings, doc);
    return result;
  }

  if (q.type === "email") {
    const users = await usersByEmail(q.email);
    if (want("users")) users.forEach((u) => pushUnique(result.users, u));
    const uids = users.map((u) => String(u.id));
    if (want("listings")) (await listingsOfOwners(uids)).forEach((l) => pushUnique(result.listings, l));
    if (want("bookings")) (await bookingsOfPeople(uids)).forEach((b) => pushUnique(result.bookings, b));
    return result;
  }

  // Free text: an exact UID / doc id first, then name / title prefixes.
  if (q.maybeId) {
    const [u, l, b] = await Promise.all([
      db.collection(USERS).doc(q.maybeId).get(),
      db.collection(LISTINGS).doc(q.maybeId).get(),
      db.collection(BOOKINGS).doc(q.maybeId).get(),
    ]);
    if (want("users")) pushUnique(result.users, docOut(u));
    if (want("listings")) pushUnique(result.listings, docOut(l));
    if (want("bookings")) pushUnique(result.bookings, docOut(b));
  }
  if (want("users") || want("bookings")) {
    const people = await byPrefix(USERS, q.prefix);
    if (want("users")) people.forEach((p) => pushUnique(result.users, p));
    if (want("bookings") && result.bookings.length === 0) {
      (await bookingsOfPeople(people.map((p) => String(p.id)))).forEach((b) => pushUnique(result.bookings, b));
    }
  }
  if (want("listings")) (await byPrefix(LISTINGS, q.prefix)).forEach((l) => pushUnique(result.listings, l));
  return result;
}

export const adminSearch = onCall<{ query?: string; kind?: Kind }>(async (request) => {
  requireAdmin(request);
  const kind: Kind = ["users", "listings", "bookings"].includes(String(request.data?.kind))
    ? (request.data?.kind as Kind)
    : "all";
  return runAdminSearch(request.data?.query, kind);
});

// ---------------------------------------------------------------------------------------
// Dossier
// ---------------------------------------------------------------------------------------

export const adminUserDossier = onCall<{ uid?: string }>(async (request) => {
  requireAdmin(request);
  const uid = request.data?.uid?.trim();
  if (!uid) throw new HttpsError("invalid-argument", "uid is required.");
  const db = getFirestore();

  const profile = docOut(await db.collection(USERS).doc(uid).get());
  if (!profile) throw new HttpsError("not-found", "No profile for that account.");

  let authInfo: Data = {};
  try {
    const user = await getAuth().getUser(uid);
    authInfo = {
      email: user.email ?? null,
      emailVerified: user.emailVerified,
      phoneNumber: user.phoneNumber ?? null,
      providers: user.providerData.map((p) => p.providerId),
      disabled: user.disabled,
      createdAt: Date.parse(user.metadata.creationTime) || null,
      lastSignInAt: Date.parse(user.metadata.lastSignInTime) || null,
    };
  } catch {
    authInfo = { missing: true };
  }

  const [listingSnap, subsSnap, rentalsSnap, hostBookingsCount] = await Promise.all([
    db.collection(LISTINGS).where("ownerId", "==", uid).get(),
    db.collection(SUBSCRIPTIONS).where("userId", "==", uid).get(),
    db.collection(BOOKINGS).where("practitionerId", "==", uid).limit(200).get(),
    countOf(db.collection(BOOKINGS).where("ownerId", "==", uid)),
  ]);

  const listings = listingSnap.docs.map((d) => {
    const data = d.data();
    const subs = Array.isArray(data.subdivisions) ? (data.subdivisions as Data[]) : [];
    return {
      id: d.id,
      displayCode: data.displayCode ?? "",
      title: data.title ?? "",
      status: data.status ?? "",
      isVerified: data.isVerified === true,
      rooms: subs.map((s) => ({ id: s.id ?? "", name: s.name ?? "", displayCode: s.displayCode ?? "" })),
    };
  });

  // Play is the billing authority: history shows plans, dates, states and Play order ids —
  // amounts are looked up in Play Console.
  const subscriptions = subsSnap.docs
    .map((d) => {
      const s = d.data();
      return {
        id: d.id,
        basePlanId: s.basePlanId ?? s.productId ?? "",
        status: s.status ?? "",
        orderId: s.orderId ?? "",
        startDate: (plain(s.startDate ?? s.createdAt) as number | null) ?? null,
        expiryDate: (plain(s.expiryDate) as number | null) ?? null,
        autoRenewing: s.autoRenewing === true,
        platform: s.platform ?? "android",
      };
    })
    .sort((a, b) => (b.startDate ?? 0) - (a.startDate ?? 0));

  const rentals = rentalsSnap.docs
    .map((d) => docOut(d))
    .filter((d): d is Data => d !== null)
    .sort((a, b) => Number(b.createdAt ?? 0) - Number(a.createdAt ?? 0));

  return { profile, auth: authInfo, listings, subscriptions, rentals, hostBookingsCount };
});

// ---------------------------------------------------------------------------------------
// Export
// ---------------------------------------------------------------------------------------

export const adminExportUsers = onCall<{ cursor?: string }>({ timeoutSeconds: 300 }, async (request) => {
  requireAdmin(request);
  const db = getFirestore();
  let query = db.collection(USERS).orderBy(FieldPath.documentId()).limit(EXPORT_PAGE);
  const cursor = request.data?.cursor;
  if (cursor) query = query.startAfter(cursor);
  const snap = await query.get();

  const rows = await Promise.all(
    snap.docs.map(async (d) => {
      const u = d.data();
      const isHost = u.role === "PRO_HOST";
      let listingCodes: string[] = [];
      if (isHost) {
        const owned = await db.collection(LISTINGS).where("ownerId", "==", d.id).select("displayCode").get();
        listingCodes = owned.docs.map((l) => String(l.get("displayCode") ?? l.id));
      }
      return {
        uid: d.id,
        displayCode: u.displayCode ?? "",
        fullName: u.fullName ?? "",
        email: u.email ?? "",
        phone: u.phone ?? "",
        role: u.role ?? "",
        country: u.country ?? "",
        city: u.city ?? "",
        packageId: isHost ? u.ownerPackageId ?? "" : "",
        packageExpiryMillis: isHost ? plain(u.ownerPackageExpiryMillis) ?? null : null,
        billingStatus: isHost ? u.billingStatus ?? "" : "",
        entitlementSource: isHost ? u.entitlementSource ?? "" : "",
        listingCodes,
        createdAtMillis: plain(u.createdAtMillis) ?? null,
      };
    })
  );
  const nextCursor = snap.size === EXPORT_PAGE ? snap.docs[snap.docs.length - 1].id : null;
  return { rows, nextCursor };
});

// ---------------------------------------------------------------------------------------
// searchName upkeep
// ---------------------------------------------------------------------------------------

async function syncSearchName(ref: DocumentReference, data: Data | undefined, source: string): Promise<boolean> {
  if (!data) return false;
  const desired = normalizeSearchName(data[source]);
  if (data.searchName === desired) return false;
  try {
    await ref.update({ searchName: desired });
    return true;
  } catch {
    return false; // deleted meanwhile — never recreate it
  }
}

export const onUserProfileWrittenSearchName = onDocumentWritten(`${USERS}/{uid}`, async (event) => {
  const after = event.data?.after;
  if (after?.exists) await syncSearchName(after.ref, after.data(), "fullName");
});

export const onListingWrittenSearchName = onDocumentWritten(`${LISTINGS}/{spaceId}`, async (event) => {
  const after = event.data?.after;
  if (after?.exists && after.data()?.ownerId) await syncSearchName(after.ref, after.data(), "title");
});

/** One-time (idempotent) fill of searchName on existing profiles and listings. */
export const backfillSearchNames = onCall({ timeoutSeconds: 540 }, async (request) => {
  requireAdmin(request);
  const db = getFirestore();
  let updated = 0;
  for (const [collection, source] of [[USERS, "fullName"], [LISTINGS, "title"]] as const) {
    let last: string | null = null;
    for (;;) {
      let q = db.collection(collection).orderBy(FieldPath.documentId()).limit(500);
      if (last) q = q.startAfter(last);
      const snap = await q.get();
      for (const d of snap.docs) {
        if (collection === LISTINGS && !d.get("ownerId")) continue; // ghost docs
        if (await syncSearchName(d.ref, d.data(), source)) updated++;
      }
      if (snap.size < 500) break;
      last = snap.docs[snap.docs.length - 1].id;
    }
  }
  return { updated };
});
