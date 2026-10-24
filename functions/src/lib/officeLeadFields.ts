/**
 * Office-lead fields shared by the website form (website/officeLeads.ts) and the Instagram DM agent's intake
 * (instagram/aiAgent.ts cleanLead). Pure; tested through officeLeads.test.ts and aiAgent.test.ts.
 */
export const OFFICE_LEAD_TAGS = ["insta-leads-story", "prospect", "office-owner"] as const;
export const PROFESSIONS = ["Psychologist", "Life coach", "Therapist", "Nutritionist", "Physiotherapist", "Other"] as const;

const clip = (v: unknown, max: number): string => String(v ?? "").replace(/\s+/g, " ").trim().slice(0, max);

/** "+961 76 701 865" / "0096176701865" → "+96176701865"; null when it can't be an international number. */
export function normalizeWhatsApp(raw: unknown): string | null {
  let s = String(raw ?? "").trim().replace(/[\s().-]/g, "");
  if (s.startsWith("00")) s = "+" + s.slice(2);
  if (!/^\+[1-9]\d{7,14}$/.test(s)) return null;
  return s;
}

/** A known profession (case-insensitive), "Other" for anything else, "" when blank. */
export function profession(v: unknown): string {
  const p = clip(v, 60);
  const hit = PROFESSIONS.find((x) => x.toLowerCase() === p.toLowerCase());
  return hit ?? (p ? "Other" : "");
}

const slug = (s: string) => s.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");

/** Address-book / sheet tags: the fixed three plus the profession ("life-coach"). */
export function leadTags(p: string): string[] {
  return [...OFFICE_LEAD_TAGS, ...(p ? [slug(p)] : [])];
}
