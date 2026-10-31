import { CollectionReference, DocumentReference, getFirestore } from "firebase-admin/firestore";
import "../lib/admin";

/**
 * Where each brand keeps its data. ProHost (the first brand) keeps its original top-level `ig_*` collections and
 * `app_config/instagram*` documents, so nothing was migrated when brands were added; every other brand lives in
 * sub-collections of its registry document `ig_brands/{brandId}`. All of it is server-only (firestore.rules).
 */
export const LEGACY_BRAND = "prohost";
export const isLegacyBrand = (brandId: string | undefined): boolean => !brandId || brandId === LEGACY_BRAND;

export type BrandCollection = "rules" | "events" | "contacts" | "stats" | "threads" | "learn" | "leads";

export function brandRef(brandId: string): DocumentReference {
  return getFirestore().collection("ig_brands").doc(brandId);
}

export function brandCol(brandId: string | undefined, name: BrandCollection): CollectionReference {
  const db = getFirestore();
  if (!isLegacyBrand(brandId)) return brandRef(brandId as string).collection(name);
  switch (name) {
    case "rules": return db.collection("ig_rules");
    case "events": return db.collection("ig_events");
    case "contacts": return db.collection("ig_contacts");
    case "stats": return db.collection("ig_stats");
    case "threads": return db.collection("ig_threads");
    case "learn": return db.collection("ig_learn");
    default: return db.collection("ig_leads");
  }
}

/** Credentials and operating settings (ProHost: the original flat document). */
export function brandConfigRef(brandId: string | undefined): DocumentReference {
  return isLegacyBrand(brandId) ? getFirestore().collection("app_config").doc("instagram") : brandRef(brandId as string);
}

/** Secrets of a non-legacy brand: tokens, app secrets, AI keys. Never copied to the sheet. */
export function brandSecretsRef(brandId: string): DocumentReference {
  return brandRef(brandId).collection("private").doc("secrets");
}

export function brandKnowledgeRef(brandId: string | undefined): DocumentReference {
  return isLegacyBrand(brandId) ? getFirestore().collection("app_config").doc("instagram_knowledge") : brandRef(brandId as string).collection("private").doc("knowledge");
}

/** Brand ids that exist in the registry (ProHost is always one). */
export async function listBrandIds(): Promise<string[]> {
  const snap = await getFirestore().collection("ig_brands").select().get();
  return [...new Set([LEGACY_BRAND, ...snap.docs.map((d) => d.id)])];
}
