/**
 * Where a listing is, for its public share page (/listing/{id}: title, subtitle, structured data).
 * ProHost is open to every market, so nothing here assumes a country: the listing's own stored country and city
 * win. Listings created before worldwide geocoding store neither; like the app (DiscoveryViewModel.effectiveCountry)
 * they count as Lebanon, and only Lebanese listings show a Lebanese governorate (the app's governorate enum is
 * Lebanon-only, so a listing elsewhere carries a meaningless one).
 */
export interface ListingPlace {
  /** Country name as stored; "Lebanon" for legacy or in-Lebanon listings; "" when unknown. */
  country: string;
  /** ISO 3166-1 alpha-2 code when it can be resolved (omitted from structured data otherwise). */
  countryCode?: string;
  /** District, or the city when there is no district. */
  locality: string;
  /** Lebanese governorate for Lebanese listings only. */
  region: string;
  /** Parts joined for titles and subtitles, most specific first ("Achrafieh, Beirut"). May be empty. */
  text: string;
}

const str = (v: unknown): string => (typeof v === "string" ? v.trim() : "");
const titleCase = (s: string): string => s.replace(/_/g, " ").toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase());

export function isInLebanon(lat: unknown, lng: unknown): boolean {
  return typeof lat === "number" && typeof lng === "number" && lat >= 33.0 && lat <= 34.7 && lng >= 35.1 && lng <= 36.7;
}

/** The 249 officially assigned ISO 3166-1 alpha-2 codes (not the reserved ones such as FX or UK, which ICU also names). */
export const ISO_CODES = "AD AE AF AG AI AL AM AO AQ AR AS AT AU AW AX AZ BA BB BD BE BF BG BH BI BJ BL BM BN BO BQ BR BS BT BV BW BY BZ " +
  "CA CC CD CF CG CH CI CK CL CM CN CO CR CU CV CW CX CY CZ DE DJ DK DM DO DZ EC EE EG EH ER ES ET FI FJ FK FM FO FR " +
  "GA GB GD GE GF GG GH GI GL GM GN GP GQ GR GS GT GU GW GY HK HM HN HR HT HU ID IE IL IM IN IO IQ IR IS IT JE JM JO JP " +
  "KE KG KH KI KM KN KP KR KW KY KZ LA LB LC LI LK LR LS LT LU LV LY MA MC MD ME MF MG MH MK ML MM MN MO MP MQ MR MS MT MU MV MW MX MY MZ " +
  "NA NC NE NF NG NI NL NO NP NR NU NZ OM PA PE PF PG PH PK PL PM PN PR PS PT PW PY QA RE RO RS RU RW " +
  "SA SB SC SD SE SG SH SI SJ SK SL SM SN SO SR SS ST SV SX SY SZ TC TD TF TG TH TJ TK TL TM TN TO TR TT TV TW TZ " +
  "UA UG UM US UY UZ VA VC VE VG VI VN VU WF WS YE YT ZA ZM ZW";

let codeByName: Map<string, string> | null = null;
/** "France" → "FR", "United States" → "US"; a two-letter code is returned upper-cased; unknown names → undefined. */
export function countryCode(name: string): string | undefined {
  const n = name.trim().toLowerCase();
  if (!n) return undefined;
  if (/^[a-z]{2}$/.test(n)) return n.toUpperCase();
  if (!codeByName) {
    codeByName = new Map();
    const names = new Intl.DisplayNames(["en"], { type: "region" });
    for (const code of ISO_CODES.split(" ")) {
      const label = names.of(code);
      if (label && label !== code && !codeByName.has(label.toLowerCase())) codeByName.set(label.toLowerCase(), code);
    }
  }
  return codeByName.get(n);
}

export function listingPlace(d: Record<string, unknown>): ListingPlace {
  const stored = str(d.country);
  const named = stored ? countryCode(stored) : undefined;
  // An explicit country wins (Damascus sits inside Lebanon's bounding box); coordinates only decide when the stored
  // name is blank (legacy) or not one we can resolve (for example the Arabic spelling).
  const lebanese = named === "LB" || (!named && isInLebanon(d.lat, d.lng));
  const district = str(d.district);
  const city = str(d.city);
  const region = lebanese && typeof d.governorate === "string" ? titleCase(d.governorate) : "";
  const parts: string[] = [];
  for (const p of [district, lebanese ? region : city]) {
    if (p && !parts.some((x) => x.toLowerCase() === p.toLowerCase())) parts.push(p);
  }
  return {
    country: stored || (lebanese ? "Lebanon" : ""),
    countryCode: lebanese ? "LB" : named,
    locality: district || city,
    region,
    text: parts.join(", "),
  };
}
