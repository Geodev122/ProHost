/**
 * WhatsApp admin alerts through the WhatsApp Business Cloud API — OFF until the owner configures it in the code-locked
 * console (pro-host.tech/admin › WhatsApp alerts → website/mailingAdmin.ts mailingAdminWhatsApp), which writes the
 * server-only doc app_config/whatsapp { enabled, phoneNumberId, accessToken, adminNumber, templateName, templateLang }.
 * The token never goes into the IG sheet (Sheets keeps every revision) nor into logs.
 *
 * Meta only lets a business start a conversation with an approved template, so alerts use `templateName` (one body
 * parameter {{1}} = the alert text); without a template a plain text message works only within 24 h of the admin
 * writing to the business number. Pure payload builders are unit-tested in whatsappAlerts.test.ts.
 */
import * as logger from "firebase-functions/logger";
import { FieldValue, getFirestore } from "firebase-admin/firestore";

export const WA_GRAPH_VERSION = "v21.0";
export const WA_DOC = "whatsapp";

export interface WhatsAppConfig {
  enabled: boolean; phoneNumberId: string; accessToken: string; adminNumber: string; templateName: string; templateLang: string;
}

export function parseWhatsAppConfig(d: Record<string, unknown> | undefined): WhatsAppConfig {
  const s = (v: unknown) => (typeof v === "string" ? v.trim() : "");
  return {
    enabled: d?.enabled === true, phoneNumberId: s(d?.phoneNumberId).replace(/\D/g, ""), accessToken: s(d?.accessToken),
    adminNumber: s(d?.adminNumber).replace(/\D/g, ""), templateName: s(d?.templateName), templateLang: s(d?.templateLang) || "en",
  };
}

/** What is missing before alerts can go out ([] = ready). */
export function whatsAppMissing(c: WhatsAppConfig): string[] {
  return [
    ...(c.enabled ? [] : ["switched off"]),
    ...(c.phoneNumberId ? [] : ["phone number ID"]),
    ...(c.accessToken ? [] : ["access token"]),
    ...(c.adminNumber.length >= 8 ? [] : ["admin WhatsApp number"]),
  ];
}

/** Template parameters may not contain new lines, tabs or more than 4 spaces in a row; max 1024 characters. */
export function templateText(text: string): string {
  return text.replace(/[\r\n\t]+/g, " · ").replace(/ {2,}/g, " ").trim().slice(0, 1000);
}

/** The Cloud API request body: the approved template when one is set, else a plain text message. */
export function whatsAppPayload(c: WhatsAppConfig, text: string): Record<string, unknown> {
  if (c.templateName) {
    return {
      messaging_product: "whatsapp", to: c.adminNumber, type: "template",
      template: { name: c.templateName, language: { code: c.templateLang }, components: [{ type: "body", parameters: [{ type: "text", text: templateText(text) }] }] },
    };
  }
  return { messaging_product: "whatsapp", to: c.adminNumber, type: "text", text: { body: text.slice(0, 4000), preview_url: false } };
}

export async function loadWhatsAppConfig(): Promise<WhatsAppConfig> {
  return parseWhatsAppConfig((await getFirestore().collection("app_config").doc(WA_DOC).get()).data());
}

/** Sends one alert to the admin's WhatsApp. Never throws; returns "sent", "not configured: …" or "failed: …". */
export async function sendAdminWhatsApp(text: string, cfg?: WhatsAppConfig): Promise<string> {
  try {
    const c = cfg ?? (await loadWhatsAppConfig());
    const missing = whatsAppMissing(c);
    if (missing.length) return `not configured: ${missing.join(", ")}`;
    const r = await fetch(`https://graph.facebook.com/${WA_GRAPH_VERSION}/${c.phoneNumberId}/messages`, {
      method: "POST", headers: { Authorization: `Bearer ${c.accessToken}`, "Content-Type": "application/json" },
      body: JSON.stringify(whatsAppPayload(c, text)),
    });
    const j = (await r.json().catch(() => ({}))) as { error?: { message?: string; code?: number } };
    const outcome = r.ok ? "sent" : `failed: ${r.status} ${(j.error?.code ?? "")} ${(j.error?.message ?? "").slice(0, 160)}`.trim();
    await getFirestore().collection("app_config").doc(WA_DOC)
      .set({ lastResult: outcome, lastAt: FieldValue.serverTimestamp() }, { merge: true }).catch(() => undefined);
    if (!r.ok) logger.warn("whatsapp_alert_failed", { status: r.status, code: j.error?.code });
    return outcome;
  } catch (e) {
    return `failed: ${(e as Error).message.slice(0, 160)}`;
  }
}
