import { IgMessage } from "./graph";

/**
 * What the DM agent remembers about one person, across every chat with us (pure helpers; aiRuntime.ts does the I/O).
 * History is kept for good (the last STORED_TURNS), the model sees the last MODEL_TURNS plus a rolling `memory` summary.
 * Only the flow state (what the last message waited for, nudges) goes stale after FLOW_TTL_MS.
 */

export interface Turn { r: "u" | "a"; t: string; /** epoch ms, when known */ at?: number }

export const STORED_TURNS = 40;
export const MODEL_TURNS = 16;
export const FLOW_TTL_MS = 48 * 3_600_000;
/** Meta's standard messaging window: we may write only within 24 h of the person's last message. */
export const ACTIVE_MS = 24 * 3_600_000;
export const MEMORY_MAX = 700;

/** Appends turns and keeps the last STORED_TURNS (texts capped at 600 characters). */
export function appendTurns(prev: Turn[], add: Turn[]): Turn[] {
  return [...prev, ...add.map((t) => ({ ...t, t: t.t.slice(0, 600) }))].filter((t) => t.t.trim()).slice(-STORED_TURNS);
}

/** The model's running summary of the person: one paragraph, no newlines, capped. Empty input keeps nothing. */
export function cleanMemory(raw: unknown): string {
  if (typeof raw !== "string") return "";
  const s = raw.replace(/\s+/g, " ").trim();
  return s.length > MEMORY_MAX ? s.slice(0, MEMORY_MAX - 1).trimEnd() + "…" : s;
}

/**
 * Earlier messages of the Instagram conversation (from the Conversations API, newest first) as turns, oldest first.
 * Our own messages (the team, the agent, ManyChat) become assistant turns. `skipIds` drops the message being answered.
 */
export function importHistory(msgs: IgMessage[], selfId: string, selfUsername: string, skipIds: string[] = []): Turn[] {
  const self = (m: IgMessage) => (selfId && m.fromId === selfId) || (!!selfUsername && m.fromUsername.toLowerCase() === selfUsername.toLowerCase());
  return [...msgs]
    .filter((m) => m.text.trim() && !skipIds.includes(m.id))
    .sort((a, b) => a.created - b.created)
    .map((m) => ({ r: self(m) ? "a" as const : "u" as const, t: m.text.slice(0, 600), at: m.created || undefined }))
    .slice(-STORED_TURNS);
}

/** "5 minutes", "3 hours", "2 days" (for "[2 days later]" markers between chat sessions). */
export function ago(ms: number): string {
  const m = Math.round(ms / 60_000);
  if (m < 60) return `${Math.max(1, m)} minute${m === 1 ? "" : "s"}`;
  const h = Math.round(m / 60);
  if (h < 48) return `${h} hour${h === 1 ? "" : "s"}`;
  const d = Math.round(h / 24);
  return `${d} day${d === 1 ? "" : "s"}`;
}

export interface ThreadLike { closed?: boolean; at?: number; lastAgentAt?: number }

/**
 * True while the person is mid-conversation with the agent: it wrote to them within Meta's 24 h window and the chat was
 * not closed. Their DM then goes to the agent before any keyword rule.
 */
export function inConversation(t: ThreadLike | undefined, now: number): boolean {
  if (!t || t.closed) return false;
  const last = Number(t.lastAgentAt ?? t.at ?? 0);
  return last > 0 && now - last <= ACTIVE_MS;
}

/** A private reply to a comment opens the chat: the comment and our reply become turns, and the agent waits for an answer. */
export function seedFromComment(prevTurns: Turn[], commentText: string, replyText: string, now: number): Turn[] {
  return appendTurns(prevTurns, [{ r: "u", t: `(public comment under our post) ${commentText}`, at: now }, { r: "a", t: replyText, at: now }]);
}

/** Thread id for a commenter Instagram did not identify (the poller's `u:<username>`), so a later DM can relink it. */
export const aliasId = (username: string): string => `u:${username.toLowerCase()}`;
