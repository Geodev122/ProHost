/**
 * onOfficeLeadSaved: the one place an office lead is recorded and announced, whether it came from the website form
 * (website/officeLeads.ts websiteOfficeLead, `ig_leads/web_<hash>`) or from the Instagram DM agent's intake
 * (instagram/aiRuntime.ts, `ig_leads/<instagram id>`, after the person agreed). A lead is complete once it has an email
 * and a WhatsApp number; `recordedFor` (email|whatsapp) is claimed in a transaction first, so a retry, the sheet sync
 * marking it synced or a later unrelated update never records or alerts twice. A changed email or number records again.
 * The pure parts are unit-tested in officeLeadSync.test.ts.
 */
import * as logger from "firebase-functions/logger";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { getFirestore } from "firebase-admin/firestore";
import { isPlausibleEmail } from "../lib/emailRateLimit";
import { normalizeWhatsApp, profession } from "../lib/officeLeadFields";
import { OfficeLead, recordOfficeLead } from "./officeLeads";

const str = (v: unknown, n: number) => (typeof v === "string" ? v.replace(/\s+/g, " ").trim().slice(0, n) : "");

/** The recording key when this lead doc should be recorded now, else null. */
export function needsRecording(d: Record<string, unknown> | undefined): string | null {
  if (!d || d.kind !== "office_owner") return null;
  const email = str(d.email, 254).toLowerCase();
  const wa = normalizeWhatsApp(d.whatsapp);
  if (!isPlausibleEmail(email) || !wa) return null;
  const key = `${email}|${wa}`;
  return d.recordedFor === key ? null : key;
}

/** The lead doc as the OfficeLead the email/contact-book code takes. */
export function leadFromDoc(d: Record<string, unknown>): OfficeLead {
  const dm = d.source === "instagram_dm";
  return {
    name: str(d.name, 100) || "(no name)", email: str(d.email, 254).toLowerCase(), whatsapp: normalizeWhatsApp(d.whatsapp) ?? "",
    profession: profession(d.profession), city: str(d.city, 80), message: str(d.message, 600),
    lang: str(d.lang, 5).startsWith("ar") ? "ar" : "en", utm: str(d.utm, 200),
    origin: dm ? "dm" : "form", instagram: str(d.instagram, 60),
  };
}

export const onOfficeLeadSaved = onDocumentWritten("ig_leads/{id}", async (event) => {
  const after = event.data?.after?.data();
  if (!needsRecording(after)) return;
  const ref = event.data!.after.ref;
  const now = Date.now();
  // Claim first: only one invocation records a given email|whatsapp.
  const claimed = await getFirestore().runTransaction(async (tx) => {
    const cur = (await tx.get(ref)).data();
    const key = needsRecording(cur);
    if (!key) return null;
    tx.update(ref, { recordedFor: key, recordedAt: now });
    return cur!;
  });
  if (!claimed) return;
  try {
    await recordOfficeLead(leadFromDoc(claimed), now);
  } catch (e) {
    logger.error("office_lead_record_failed", { id: event.params.id, error: (e as Error).message.slice(0, 200) });
  }
});
