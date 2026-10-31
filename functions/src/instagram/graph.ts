import { Step } from "./types";

/** Instagram API with Instagram login. Fixed version so a Meta default change can't break us silently. */
export const GRAPH = "https://graph.instagram.com/v23.0";

export interface GraphCreds { igUserId: string; token: string }
export class GraphError extends Error {
  constructor(public status: number, public code: number | undefined, message: string) { super(message); }
  /** 4xx other than rate limits won't succeed on retry. */
  get retryable(): boolean { return this.status >= 500 || this.status === 429 || this.code === 4 || this.code === 17 || this.code === 32; }
}

type Fetch = typeof fetch;

async function call(f: Fetch, method: "GET" | "POST", url: string, token: string, body?: unknown): Promise<Record<string, unknown>> {
  let last: GraphError | undefined;
  for (let attempt = 0; attempt < 3; attempt++) {
    const res = await f(url, {
      method,
      headers: { Authorization: `Bearer ${token}`, ...(body ? { "Content-Type": "application/json" } : {}) },
      body: body ? JSON.stringify(body) : undefined,
    });
    const json = (await res.json().catch(() => ({}))) as { error?: { message?: string; code?: number } } & Record<string, unknown>;
    if (res.ok && !json.error) return json;
    last = new GraphError(res.status, json.error?.code, json.error?.message ?? `HTTP ${res.status}`);
    if (!last.retryable) break;
    await new Promise((r) => setTimeout(r, 400 * 2 ** attempt));
  }
  throw last ?? new GraphError(0, undefined, "unknown");
}

/** Message body for a DM / private reply: plain text, text + link button, or text + quick replies. */
export function messagePayload(step: Extract<Step, { type: "dm" | "private_reply" }>): Record<string, unknown> {
  if (step.type === "dm" && step.buttons?.length) {
    return { attachment: { type: "template", payload: { template_type: "button", text: step.text.slice(0, 640),
      buttons: step.buttons.slice(0, 3).map((b) => ({ type: "web_url", url: b.url, title: b.label.slice(0, 20) })) } } };
  }
  if (step.buttonLabel && step.buttonUrl) {
    return { attachment: { type: "template", payload: { template_type: "button", text: step.text.slice(0, 640),
      buttons: [{ type: "web_url", url: step.buttonUrl, title: step.buttonLabel.slice(0, 20) }] } } };
  }
  if (step.type === "dm" && step.quickReplies?.length) {
    return { text: step.text.slice(0, 1000), quick_replies: step.quickReplies.slice(0, 13).map((t) => ({ content_type: "text", title: t.slice(0, 20), payload: t.toUpperCase().replace(/\s+/g, "_").slice(0, 1000) })) };
  }
  return { text: step.text.slice(0, 1000) };
}

export async function sendStep(creds: GraphCreds, step: Step, f: Fetch = fetch): Promise<string> {
  if (step.type === "comment_reply") {
    const r = await call(f, "POST", `${GRAPH}/${encodeURIComponent(step.commentId)}/replies`, creds.token, { message: step.text.slice(0, 2200) });
    return String(r.id ?? "");
  }
  const recipient = step.type === "private_reply" ? { comment_id: step.commentId } : { id: step.recipientId };
  const r = await call(f, "POST", `${GRAPH}/${encodeURIComponent(creds.igUserId)}/messages`, creds.token, { recipient, message: messagePayload(step) });
  return String(r.message_id ?? r.id ?? "");
}

export async function whoAmI(token: string, f: Fetch = fetch): Promise<{ id: string; username: string }> {
  const r = await call(f, "GET", `${GRAPH}/me?fields=user_id,username`, token);
  return { id: String(r.user_id ?? r.id ?? ""), username: String(r.username ?? "") };
}

/** Name and handle of a person who messaged us (needs the person to have written first). Null when Meta doesn't return it. */
export async function getProfile(creds: GraphCreds, userId: string, f: Fetch = fetch): Promise<{ name: string; username: string } | null> {
  try {
    const r = await call(f, "GET", `${GRAPH}/${encodeURIComponent(userId)}?fields=name,username`, creds.token);
    return { name: String(r.name ?? ""), username: String(r.username ?? "") };
  } catch {
    return null;
  }
}

export interface IgComment { id: string; text: string; username: string; fromId: string; timestamp: number; parentId: string }

/** Latest media ids of the account (posts, reels). */
export async function listRecentMedia(creds: GraphCreds, limit = 5, f: Fetch = fetch): Promise<string[]> {
  const r = await call(f, "GET", `${GRAPH}/${encodeURIComponent(creds.igUserId)}/media?fields=id,timestamp&limit=${limit}`, creds.token);
  return (Array.isArray(r.data) ? (r.data as Array<{ id?: string }>) : []).map((m) => String(m.id ?? "")).filter(Boolean);
}

/** Top-level comments of one media, newest first as Meta returns them. */
export async function listComments(creds: GraphCreds, mediaId: string, limit = 50, f: Fetch = fetch): Promise<IgComment[]> {
  const r = await call(f, "GET", `${GRAPH}/${encodeURIComponent(mediaId)}/comments?fields=id,text,username,timestamp,from,parent_id&limit=${limit}`, creds.token);
  const rows = Array.isArray(r.data) ? (r.data as Array<Record<string, unknown>>) : [];
  return rows.map((c) => ({
    id: String(c.id ?? ""), text: String(c.text ?? ""), username: String(c.username ?? ""),
    fromId: String(((c.from ?? {}) as Record<string, unknown>).id ?? ""), timestamp: Date.parse(String(c.timestamp ?? "")) || 0, parentId: String(c.parent_id ?? ""),
  })).filter((c) => c.id);
}

export interface IgConversation { id: string; updated: number; participants: Array<{ id: string; username: string }> }
export interface IgMessage { id: string; fromId: string; fromUsername: string; text: string; created: number }

const rows = (v: unknown): Array<Record<string, unknown>> =>
  (v && typeof v === "object" && Array.isArray((v as Record<string, unknown>).data) ? ((v as Record<string, unknown>).data as Array<Record<string, unknown>>) : []);

/** Conversations API answer → conversations (pure). */
export function parseConversations(r: Record<string, unknown>): IgConversation[] {
  return rows(r).map((c) => ({
    id: String(c.id ?? ""), updated: Date.parse(String(c.updated_time ?? "")) || 0,
    participants: rows(c.participants).map((p) => ({ id: String(p.id ?? ""), username: String(p.username ?? "") })),
  })).filter((c) => c.id);
}

/** One conversation's `messages` edge → messages, newest first as Meta returns them (pure). */
export function parseMessages(r: Record<string, unknown>): IgMessage[] {
  return rows(r.messages).map((m) => {
    const from = (m.from ?? {}) as Record<string, unknown>;
    return { id: String(m.id ?? ""), fromId: String(from.id ?? ""), fromUsername: String(from.username ?? ""), text: String(m.message ?? ""), created: Date.parse(String(m.created_time ?? "")) || 0 };
  }).filter((m) => m.id);
}

/**
 * Our latest DM conversations (Conversations API). An unpublished Meta app gets no webhook for people without an app role,
 * so the DM poller reads replies from here. `userId` narrows it to one person's conversation.
 */
export async function listConversations(creds: GraphCreds, limit = 20, f: Fetch = fetch, userId?: string): Promise<IgConversation[]> {
  const q = `platform=instagram&fields=id,updated_time,participants&limit=${limit}${userId ? `&user_id=${encodeURIComponent(userId)}` : ""}`;
  return parseConversations(await call(f, "GET", `${GRAPH}/me/conversations?${q}`, creds.token));
}

/** The last messages of one conversation (Meta returns details for the 20 most recent only). */
export async function listMessages(creds: GraphCreds, conversationId: string, limit = 20, f: Fetch = fetch): Promise<IgMessage[]> {
  const fields = `messages.limit(${Math.min(20, limit)}){id,created_time,from,to,message}`;
  return parseMessages(await call(f, "GET", `${GRAPH}/${encodeURIComponent(conversationId)}?fields=${encodeURIComponent(fields)}`, creds.token));
}

/** Subscribes the Instagram account to our app's webhook fields. */
export async function subscribeApp(creds: GraphCreds, fields = "comments,messages,messaging_postbacks,message_reactions", f: Fetch = fetch): Promise<boolean> {
  const r = await call(f, "POST", `${GRAPH}/${encodeURIComponent(creds.igUserId)}/subscribed_apps?subscribed_fields=${fields}`, creds.token);
  return r.success === true;
}

/** Long-lived tokens last 60 days and refresh once they are 24 h old. */
export async function refreshToken(token: string, f: Fetch = fetch): Promise<{ token: string; expiresInSec: number }> {
  const r = await call(f, "GET", `https://graph.instagram.com/refresh_access_token?grant_type=ig_refresh_token`, token);
  return { token: String(r.access_token ?? ""), expiresInSec: Number(r.expires_in) || 0 };
}
