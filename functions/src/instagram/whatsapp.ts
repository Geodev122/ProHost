import { IgEvent, Step } from "./types";

/**
 * WhatsApp Business Cloud API channel. Inbound messages arrive at `waWebhook` (whatsappWebhook.ts) and go through the same
 * pipeline as Instagram DMs (rules → AI agent → reply); this file is only the wire format.
 * People are keyed as `wa:<wa_id>` so a WhatsApp number can never collide with an Instagram-scoped id.
 */
export const WA_GRAPH = "https://graph.facebook.com/v23.0";
export const WA_PREFIX = "wa:";
export const isWhatsAppId = (id: string): boolean => id.startsWith(WA_PREFIX);

type Obj = Record<string, unknown>;
const obj = (v: unknown): Obj => (v && typeof v === "object" ? (v as Obj) : {});
const str = (v: unknown): string => (typeof v === "string" ? v : typeof v === "number" ? String(v) : "");

/** The phone number id an entry belongs to (routing key). */
export function entryPhoneNumberId(entry: unknown): string {
  for (const c of Array.isArray(obj(entry).changes) ? (obj(entry).changes as unknown[]) : []) {
    const id = str(obj(obj(obj(c).value).metadata).phone_number_id);
    if (id) return id;
  }
  return "";
}

/** Meta webhook entry → events. Statuses (delivered / read), reactions and unsupported types are ignored. */
export function parseWhatsAppEntry(entry: unknown, ownPhoneNumberId: string): IgEvent[] {
  const out: IgEvent[] = [];
  for (const c of Array.isArray(obj(entry).changes) ? (obj(entry).changes as unknown[]) : []) {
    const change = obj(c);
    if (change.field !== "messages") continue;
    const v = obj(change.value);
    if (str(obj(v.metadata).phone_number_id) !== ownPhoneNumberId) continue;
    const names = new Map<string, string>();
    for (const k of Array.isArray(v.contacts) ? (v.contacts as unknown[]) : []) names.set(str(obj(k).wa_id), str(obj(obj(k).profile).name));
    for (const m of Array.isArray(v.messages) ? (v.messages as unknown[]) : []) {
      const msg = obj(m);
      const id = str(msg.id);
      const from = str(msg.from);
      if (!id || !from || from === ownPhoneNumberId) continue;
      const type = str(msg.type);
      const ts = (Number(msg.timestamp) || Math.floor(Date.now() / 1000)) * 1000;
      let text = "";
      let payload: string | undefined;
      if (type === "text") text = str(obj(msg.text).body);
      else if (type === "button") { text = str(obj(msg.button).text); payload = str(obj(msg.button).payload) || undefined; }
      else if (type === "interactive") {
        const i = obj(msg.interactive);
        const r = obj(i.button_reply ?? i.list_reply);
        text = str(r.title);
        payload = str(r.id) || undefined;
      } else continue;
      if (!text.trim()) continue;
      out.push({
        id: `wm_${id}`, kind: payload ? "postback" : "dm", channel: "whatsapp", senderId: WA_PREFIX + from,
        username: names.get(from) || undefined, text, payload, timestamp: ts,
      });
    }
  }
  return out;
}

export interface WaCreds { phoneNumberId: string; token: string }
export class WaError extends Error {
  constructor(public status: number, public code: number | undefined, message: string) { super(message); }
}

/** The JSON body for one step: plain text, text + one link button (cta_url), text + reply buttons, or text with extra links listed. */
export function whatsappPayload(step: Extract<Step, { type: "dm" | "private_reply" | "comment_reply" }>, to: string): Record<string, unknown> {
  const base = { messaging_product: "whatsapp", recipient_type: "individual", to };
  const text = step.text.slice(0, 1000);
  if (step.type === "dm" && step.buttons?.length) {
    if (step.buttons.length === 1) {
      return { ...base, type: "interactive", interactive: { type: "cta_url", body: { text }, action: { name: "cta_url", parameters: { display_text: step.buttons[0].label.slice(0, 20), url: step.buttons[0].url } } } };
    }
    // WhatsApp allows one URL button per message: list the links in the text instead.
    const links = step.buttons.slice(0, 3).map((b) => `${b.label}: ${b.url}`).join("\n");
    return { ...base, type: "text", text: { body: `${text}\n\n${links}`.slice(0, 4000), preview_url: true } };
  }
  if ("buttonLabel" in step && step.buttonLabel && step.buttonUrl) {
    return { ...base, type: "interactive", interactive: { type: "cta_url", body: { text }, action: { name: "cta_url", parameters: { display_text: step.buttonLabel.slice(0, 20), url: step.buttonUrl } } } };
  }
  if (step.type === "dm" && step.quickReplies?.length) {
    return { ...base, type: "interactive", interactive: { type: "button", body: { text }, action: { buttons: step.quickReplies.slice(0, 3).map((t) => ({ type: "reply", reply: { id: t.toUpperCase().replace(/\s+/g, "_").slice(0, 256), title: t.slice(0, 20) } })) } } };
  }
  return { ...base, type: "text", text: { body: text, preview_url: true } };
}

/** Sends a DM step to a `wa:` recipient. Comment-type steps do not exist on WhatsApp and are skipped. */
export async function sendWhatsApp(creds: WaCreds, step: Step, f: typeof fetch = fetch): Promise<string> {
  if (step.type !== "dm") return "";
  const to = step.recipientId.replace(/^wa:/, "");
  const res = await f(`${WA_GRAPH}/${encodeURIComponent(creds.phoneNumberId)}/messages`, {
    method: "POST",
    headers: { Authorization: `Bearer ${creds.token}`, "Content-Type": "application/json" },
    body: JSON.stringify(whatsappPayload(step, to)),
  });
  const j = (await res.json().catch(() => ({}))) as { error?: { message?: string; code?: number }; messages?: Array<{ id?: string }> };
  if (!res.ok || j.error) throw new WaError(res.status, j.error?.code, j.error?.message ?? `HTTP ${res.status}`);
  return String(j.messages?.[0]?.id ?? "");
}

/** Template message for contacting someone outside the 24 h window (the template must be approved in WhatsApp Manager). */
export async function sendWhatsAppTemplate(creds: WaCreds, to: string, template: { name: string; lang: string; params?: string[] }, f: typeof fetch = fetch): Promise<string> {
  const res = await f(`${WA_GRAPH}/${encodeURIComponent(creds.phoneNumberId)}/messages`, {
    method: "POST",
    headers: { Authorization: `Bearer ${creds.token}`, "Content-Type": "application/json" },
    body: JSON.stringify({ messaging_product: "whatsapp", to: to.replace(/^wa:/, ""), type: "template", template: { name: template.name, language: { code: template.lang }, ...(template.params?.length ? { components: [{ type: "body", parameters: template.params.map((t) => ({ type: "text", text: t.slice(0, 500) })) }] } : {}) } }),
  });
  const j = (await res.json().catch(() => ({}))) as { error?: { message?: string; code?: number }; messages?: Array<{ id?: string }> };
  if (!res.ok || j.error) throw new WaError(res.status, j.error?.code, j.error?.message ?? `HTTP ${res.status}`);
  return String(j.messages?.[0]?.id ?? "");
}
