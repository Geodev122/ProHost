import { getFirestore } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { onSchedule } from "firebase-functions/v2/scheduler";
import "../lib/admin";
import { IgConfig, loadConfig } from "./config";
import { IgConversation, IgMessage, listConversations, listMessages } from "./graph";
import { ACTIVE_MS } from "./aiMemory";
import { handleEvent } from "./webhook";
import { IgEvent } from "./types";

/**
 * The DM poller. An unpublished Meta app gets webhooks only for people with a role on the app, so a commenter's reply to
 * our private-reply DM never reached the agent and the conversation stopped. Every minute this reads our latest
 * conversations (Conversations API) and runs each person's new messages through the same pipeline as webhook DMs.
 * `ig_events` dedupes by message id (`m_<id>`, the webhook's id too), so a message seen both ways is answered once.
 */

const OVERLAP_MS = 60_000;
const MAX_CONVERSATIONS = 10;

export interface Self { id: string; username: string }
const isSelf = (m: IgMessage, self: Self) =>
  (!!self.id && m.fromId === self.id) || (!!self.username && m.fromUsername.toLowerCase() === self.username.toLowerCase());

/**
 * The person's messages to answer in one conversation (pure). Meta returns newest first. Skips: ours, empty ones (likes,
 * stickers), ones already handled (`seen`), anything before the cursor or outside Meta's 24 h window, and the whole batch
 * when our side already wrote after their last message (a team member answered by hand).
 */
export function newMessages(msgs: IgMessage[], since: number, now: number, self: Self, seen: Set<string>): IgMessage[] {
  const sorted = [...msgs].sort((a, b) => a.created - b.created);
  const lastTheirs = [...sorted].reverse().find((m) => !isSelf(m, self));
  const lastOurs = [...sorted].reverse().find((m) => isSelf(m, self));
  if (!lastTheirs || (lastOurs && lastOurs.created > lastTheirs.created)) return [];
  return sorted.filter((m) => !isSelf(m, self) && m.text.trim() && !seen.has(m.id) &&
    m.created >= since - OVERLAP_MS && now - m.created <= ACTIVE_MS);
}

/** Several quick messages from one person become ONE event (one answer), named after the newest message. */
export function toDmEvent(msgs: IgMessage[], conv: IgConversation): IgEvent {
  const last = msgs[msgs.length - 1];
  const username = conv.participants.find((p) => p.id === last.fromId)?.username || last.fromUsername || undefined;
  return { id: `m_${last.id}`, kind: "dm", senderId: last.fromId, username, text: msgs.map((m) => m.text).join("\n").slice(0, 1000), timestamp: last.created };
}

export interface DmPollDeps {
  listConversations: typeof listConversations;
  listMessages: typeof listMessages;
  handleEvent: typeof handleEvent;
  /** Ids already in ig_events (answered via webhook or an earlier run). */
  seen: (ids: string[]) => Promise<Set<string>>;
  /** Records the older messages merged into one answer, so the webhook never answers them again. */
  markMerged: (ids: string[], now: number) => Promise<void>;
}

async function seenInEvents(ids: string[]): Promise<Set<string>> {
  if (!ids.length) return new Set();
  const db = getFirestore();
  const snaps = await db.getAll(...ids.map((id) => db.collection("ig_events").doc(`m_${id}`)));
  return new Set(snaps.filter((s) => s.exists).map((s) => s.id.slice(2)));
}

async function markMergedEvents(ids: string[], now: number): Promise<void> {
  const db = getFirestore();
  await Promise.all(ids.map((id) => db.collection("ig_events").doc(`m_${id}`)
    .create({ kind: "dm", status: "merged", reason: "answered together with the person's next message (DM poller)", createdAt: now, synced: true })
    .catch(() => undefined)));
}

export const DEFAULT_DEPS: DmPollDeps = { listConversations, listMessages, handleEvent, seen: seenInEvents, markMerged: markMergedEvents };

/** One poll. The first run only sets a baseline (old messages are never answered). Returns how many messages were handled. */
export async function pollDms(config: IgConfig, now: number, deps: DmPollDeps = DEFAULT_DEPS): Promise<number> {
  const ref = getFirestore().collection("ig_stats").doc("dm_poll");
  const state = (await ref.get()).data();
  if (!state?.since) { await ref.set({ since: now, lastRun: now, lastError: "", processed: 0 }); return 0; }
  const creds = { igUserId: config.igUserId, token: config.accessToken };
  const self = { id: config.igUserId, username: config.username };
  const since = Number(state.since);
  let newest = since;
  let processed = 0;
  try {
    const convs = (await deps.listConversations(creds, 20)).filter((c) => c.updated >= since - OVERLAP_MS).slice(0, MAX_CONVERSATIONS);
    for (const conv of convs) {
      const msgs = await deps.listMessages(creds, conv.id, 10);
      const seen = await deps.seen(msgs.map((m) => m.id));
      const fresh = newMessages(msgs, since, now, self, seen);
      newest = Math.max(newest, ...msgs.map((m) => Math.min(m.created, now)));
      if (!fresh.length) continue;
      await deps.markMerged(fresh.slice(0, -1).map((m) => m.id), now);
      await deps.handleEvent(config, toDmEvent(fresh, conv), now);
      processed += fresh.length;
    }
    await ref.set({ since: newest, lastRun: now, lastError: "", processed: Number(state.processed || 0) + processed }, { merge: true });
  } catch (e) {
    await ref.set({ lastRun: now, lastError: (e as Error).message.slice(0, 300) }, { merge: true });
    throw e;
  }
  return processed;
}

export const igDmPoll = onSchedule({ schedule: "every 1 minutes", timeoutSeconds: 120, memory: "256MiB" }, async () => {
  try {
    const config = await loadConfig();
    if (!config.accessToken || !config.igUserId) return;
    const n = await pollDms(config, Date.now());
    if (n) logger.info("igDmPoll", { processed: n });
  } catch (e) {
    logger.error("igDmPoll failed", { message: (e as Error).message });
  }
});
