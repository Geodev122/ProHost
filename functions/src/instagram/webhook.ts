import { FieldValue, getFirestore } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { onRequest } from "firebase-functions/v2/https";
import "../lib/admin";
import { IgConfig, canSend, loadConfig } from "./config";
import { aiDecide, commentDecide } from "./aiRuntime";
import { IN_CONVERSATION, decide } from "./engine";
import { inConversation, seedFromComment } from "./aiMemory";
import { GraphError, sendStep } from "./graph";
import { alertAdmin } from "./notify";
import { parseWebhook } from "./parse";
import { verifySignature } from "./signature";
import { ContactState, Decision, IgEvent, Rule } from "./types";

const HOUR = 3_600_000;
const BREAKER_ERRORS = 5;

/** pro-host.tech/ig-webhook (hosting rewrite). GET = Meta's handshake, POST = events. */
export const igWebhook = onRequest({ cors: false, memory: "256MiB", timeoutSeconds: 60 }, async (req, res) => {
  const config = await loadConfig();
  if (req.method === "GET") {
    const ok = req.query["hub.mode"] === "subscribe" && !!config.verifyToken && req.query["hub.verify_token"] === config.verifyToken;
    if (ok) res.status(200).type("text/plain").send(String(req.query["hub.challenge"] ?? ""));
    else res.status(403).send("Forbidden");
    return;
  }
  if (req.method !== "POST") { res.status(405).send("Method not allowed"); return; }
  if (!verifySignature(req.rawBody, req.get("x-hub-signature-256"), config.appSecret)) {
    logger.warn("ig webhook: bad signature");
    res.status(403).send("Bad signature");
    return;
  }
  // Acknowledge-by-completing: events are processed inline (seconds); Meta retries on non-200.
  try {
    for (const event of parseWebhook(req.body)) await handleEvent(config, event);
  } catch (e) {
    logger.error("ig webhook: processing failed", { message: (e as Error).message });
  }
  res.status(200).send("EVENT_RECEIVED");
});

async function loadRules(): Promise<Rule[]> {
  const snap = await getFirestore().collection("ig_rules").get();
  return snap.docs.map((d) => ({ ...(d.data() as Rule), id: d.id }));
}

export async function handleEvent(config: IgConfig, event: IgEvent, now = Date.now()): Promise<Decision | null> {
  const db = getFirestore();
  const eventRef = db.collection("ig_events").doc(event.id);
  // Dedupe: Meta retries; create() fails if we already stored this event.
  try {
    await eventRef.create({ kind: event.kind, senderId: event.senderId, username: event.username ?? "", text: event.text.slice(0, 500), mediaId: event.mediaId ?? "", status: "processing", createdAt: now, synced: false });
  } catch {
    return null;
  }

  const contactRef = db.collection("ig_contacts").doc(event.senderId);
  const contact = ((await contactRef.get()).data() ?? {}) as ContactState & { tags?: string[]; username?: string };
  const stats = (await db.collection("ig_stats").doc("hour_" + Math.floor(now / HOUR)).get()).data();
  const settings = { ...config.settings, sentThisHour: Number(stats?.sent) || 0 };
  const rules = await loadRules();
  // Mid-conversation with the agent (it wrote within 24 h): the reply goes to the agent, not to a keyword rule.
  const conversation = event.kind === "dm" && config.ai.enabled &&
    inConversation((await db.collection("ig_threads").doc(event.senderId).get()).data(), now);
  let decision = decide(event, rules, contact, settings, now, Math.random, { conversation });
  // The AI agent answers DMs no keyword rule handled, replaces the generic welcome / catch-all DM rules, and owns live conversations.
  if (event.kind === "dm") {
    const generic = decision.kind === "replied" && rules.some((r) => r.id === decision.ruleId && (r.trigger === "dm_welcome" || r.trigger === "dm_default"));
    const live = decision.kind === "ignored" && decision.reason === IN_CONVERSATION;
    if (generic || live || (decision.kind === "ignored" && decision.reason === "no rule matched")) {
      const ai = await aiDecide(config, event, now);
      if (ai && (ai.kind !== "ignored" || decision.kind === "ignored")) decision = ai;
      // The agent could not answer (off, capped, provider down): the keyword rules answer as they would have.
      if (live && (!ai || ai.kind === "ignored")) {
        const fallback = decide(event, rules, contact, settings, now);
        if (fallback.kind !== "ignored") decision = fallback;
      }
    }
  }

  // Public comments no keyword rule handled: the comment agent answers by intent (thanks / question) or flags negatives.
  if (event.kind === "comment" && decision.kind === "ignored" && decision.reason === "no rule matched") {
    const ai = await commentDecide(config, event, now);
    if (ai && (ai.kind !== "ignored" || ai.flag)) decision = ai;
  }

  let status: string = decision.flag ? "flagged" : decision.kind === "ignored" ? "ignored" : canSend(config) ? "sent" : "dry-run";
  let error = "";
  if (decision.steps.length && status === "sent") {
    if (await breakerOpen(now)) {
      status = "paused";
      error = "circuit breaker open: too many recent API errors";
    } else {
      try {
        for (const [i, step] of decision.steps.entries()) {
          if (i > 0 && decision.pauseMs) await new Promise((r) => setTimeout(r, decision.pauseMs));
          await sendStep({ igUserId: config.igUserId, token: config.accessToken }, step);
        }
        await db.collection("ig_stats").doc("hour_" + Math.floor(now / HOUR)).set({ sent: FieldValue.increment(decision.steps.length), hour: Math.floor(now / HOUR) }, { merge: true });
      } catch (e) {
        status = "failed";
        error = (e as Error).message.slice(0, 300);
        await recordFailure(config, e, now);
      }
    }
  }

  // A keyword rule's private reply opens a chat too: remember it, so the agent can carry on when the person answers.
  const priv = decision.steps.find((st) => st.type === "private_reply");
  if (event.kind === "comment" && decision.ruleId && priv?.text && (status === "sent" || status === "dry-run")) {
    await seedRuleReply(event, priv.text, now).catch(() => undefined);
  }

  if (decision.flag === "negative") {
    await alertAdmin(config, "Negative Instagram comment", `@${event.username || event.senderId} wrote under a post: "${event.text.slice(0, 300)}". Nothing was sent or liked. Comment id ${event.commentId ?? ""}.`).catch(() => undefined);
  }
  const sentOrWould = status === "sent" || status === "dry-run";
  const update: Record<string, unknown> = {
    seenBefore: true, lastSeenAt: now, username: event.username ?? contact.username ?? "", lastText: event.text.slice(0, 200),
    ...(decision.kind === "opt_out" && sentOrWould ? { optedOut: true } : {}),
    ...(decision.kind === "handoff" && sentOrWould ? { handoffUntil: now + config.settings.handoffHours * HOUR } : {}),
    ...(decision.ruleId && sentOrWould ? { [`lastRuleAt.${decision.ruleId}`]: now } : {}),
    ...(decision.tags.length && sentOrWould ? { tags: FieldValue.arrayUnion(...decision.tags) } : {}),
  };
  await contactRef.set({ ...flatten(update) }, { merge: true }).catch(() => undefined);
  await eventRef.update({ status, reason: decision.reason, ruleId: decision.ruleId ?? "", ruleName: decision.ruleName ?? "", replies: decision.steps.map((s) => s.text ?? "").filter(Boolean).join(" ‖ ").slice(0, 500), error, synced: false, processedAt: Date.now() });
  return decision;
}

async function seedRuleReply(event: IgEvent, reply: string, now: number): Promise<void> {
  const ref = getFirestore().collection("ig_threads").doc(event.senderId);
  const t = (await ref.get()).data() ?? {};
  const turns = seedFromComment(Array.isArray(t.turns) ? t.turns : [], event.text, reply, now);
  await ref.set({ turns, at: now, lastAgentAt: now, closed: false, nudges: 0, state: { ...(t.state ?? {}), awaiting: "reply" },
    ...(event.username ? { username: event.username } : {}) }, { merge: true });
}

/** dotted keys in `update` (lastRuleAt.<id>) are written as nested maps so merge keeps siblings. */
function flatten(update: Record<string, unknown>): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(update)) {
    const [head, tail] = k.split(".");
    if (tail) out[head] = { ...((out[head] as object) ?? {}), [tail]: v };
    else out[k] = v;
  }
  return out;
}

async function breakerOpen(now: number): Promise<boolean> {
  const snap = await getFirestore().collection("ig_stats").doc("errors").get();
  const d = snap.data();
  return !!d && Number(d.count) >= BREAKER_ERRORS && now - Number(d.lastAt) < HOUR;
}

async function recordFailure(config: IgConfig, e: unknown, now: number): Promise<void> {
  const ref = getFirestore().collection("ig_stats").doc("errors");
  const d = (await ref.get()).data();
  const fresh = d && now - Number(d.lastAt) < HOUR;
  const count = (fresh ? Number(d?.count) : 0) + 1;
  await ref.set({ count, lastAt: now, lastError: (e as Error).message.slice(0, 300) });
  const permanent = e instanceof GraphError && (e.code === 190 || e.status === 401);
  if (count === BREAKER_ERRORS || permanent) await alertAdmin(config, permanent ? "Instagram token rejected" : "Instagram replies paused", (e as Error).message);
}
