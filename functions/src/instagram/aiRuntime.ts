import { FieldValue, getFirestore } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import "../lib/admin";
import { AiConfig, LlmError } from "./aiLlm";
import { ConvState, NEW_STATE, Turn, advance, runAgent, toStepButtons } from "./aiAgent";
import { KnowledgeItem, buildKnowledge } from "./aiKnowledge";
import { IgConfig } from "./config";
import { alertAdmin } from "./notify";
import { Decision, IgEvent } from "./types";

const HOUR = 3_600_000;
const THREAD_TTL = 48 * HOUR;
let kCache: { text: string; at: number } | null = null;

async function loadKnowledge(now: number): Promise<string> {
  if (kCache && now - kCache.at < 60_000) return kCache.text;
  let items: KnowledgeItem[] = [];
  try {
    const d = (await getFirestore().collection("app_config").doc("instagram_knowledge").get()).data();
    items = Array.isArray(d?.items) ? (d.items as KnowledgeItem[]) : [];
  } catch { /* base knowledge only */ }
  kCache = { text: buildKnowledge(items), at: now };
  return kCache.text;
}

const ignored = (reason: string): Decision => ({ kind: "ignored", reason, steps: [], tags: [] });
const dayKey = (now: number) => new Date(now).toISOString().slice(0, 10).replace(/-/g, "");

/**
 * The DM agent. Returns a Decision (replied / handoff / ignored with a reason) or null when the agent is off.
 * Per-person (6/h) and daily caps, provider errors and a missing key all end in "ignored" with a plain reason,
 * never in a message to the person.
 */
export async function aiDecide(config: IgConfig, event: IgEvent, now: number, f?: typeof fetch): Promise<Decision | null> {
  const ai = config.ai;
  if (!ai.enabled) return null;
  const apiKey = ai.provider === "gemini" ? config.geminiKey : ai.provider === "anthropic" ? config.anthropicKey : "";
  if (ai.provider !== "vertex" && !apiKey) return ignored(`AI agent: no ${ai.provider} key configured (run igsetup with the GitHub secret)`);
  const db = getFirestore();
  const threadRef = db.collection("ig_threads").doc(event.senderId);
  const thread = (await threadRef.get()).data() ?? {};
  const fresh = thread.at && now - Number(thread.at) < THREAD_TTL;
  const turns: Turn[] = fresh && Array.isArray(thread.turns) ? (thread.turns as Turn[]) : [];
  const times = (fresh && Array.isArray(thread.aiTimes) ? (thread.aiTimes as number[]) : []).filter((t) => now - t < HOUR);
  if (times.length >= ai.perPersonPerHour) return ignored(`AI agent: limit of ${ai.perPersonPerHour} replies per person per hour reached`);
  const dayRef = db.collection("ig_stats").doc("ai_" + dayKey(now));
  if ((Number((await dayRef.get()).data()?.calls) || 0) >= ai.dailyCap) return ignored(`AI agent: daily cap of ${ai.dailyCap} replies reached`);

  const cfg: AiConfig = { provider: ai.provider, model: ai.model, region: ai.region, apiKey };
  const prior: ConvState = fresh && thread.state ? { ...NEW_STATE, ...(thread.state as Partial<ConvState>), lead: { ...((thread.state as ConvState).lead ?? {}) } } : NEW_STATE;
  let run;
  try {
    run = await runAgent(cfg, await loadKnowledge(now), turns, event.text, f, prior);
  } catch (e) {
    const status = e instanceof LlmError ? e.status : 0;
    logger.warn("ig ai agent failed", { status, message: (e as Error).message.slice(0, 200) });
    if (status === 401 || status === 403) await alertAdmin(config, "Instagram AI agent cannot reach Claude", `${status}: ${(e as Error).message}`).catch(() => undefined);
    return ignored(`AI agent unavailable (${status || "error"}: ${(e as Error).message.slice(0, 120)})`);
  }
  const { out, usage } = run;

  const { state, persist } = advance(prior, out);
  await Promise.all([
    threadRef.set({ at: now, turns: [...turns, { r: "u", t: event.text.slice(0, 600) }, { r: "a", t: out.reply.slice(0, 600) }].slice(-12), aiTimes: [...times, now], state }),
    dayRef.set({ calls: FieldValue.increment(1), inputTokens: FieldValue.increment(usage.inputTokens), outputTokens: FieldValue.increment(usage.outputTokens), cacheReadTokens: FieldValue.increment(usage.cacheReadTokens), day: dayKey(now) }, { merge: true }),
  ]).catch(() => undefined);

  // A lead is stored only after the person said yes to the consent question (see advance()).
  if (persist) {
    const leadRef = db.collection("ig_leads").doc(event.senderId);
    const before = (await leadRef.get()).data();
    await leadRef.set({ ...persist, lang: out.lang, instagram: event.username ?? "", consentAt: before?.consentAt ?? now, source: "instagram_dm", updatedAt: now, synced: false }, { merge: true }).catch(() => undefined);
    if (persist.email && before?.email !== persist.email) {
      await alertAdmin(config, "New Instagram lead", `${persist.name ?? "(no name)"} <${persist.email}> — ${persist.role ?? "role unknown"}; need: ${persist.need ?? "unknown"}. They agreed to be contacted. @${event.username ?? event.senderId}`).catch(() => undefined);
    }
  }
  if (out.learn || out.confidence < 0.5) {
    const id = `${dayKey(now)}_${event.id.slice(-10)}`;
    await db.collection("ig_learn").doc(id).set({ question: event.text.slice(0, 400), lang: out.lang, at: now, asked: 1, synced: false }).catch(() => undefined);
  }
  if (out.handoff) {
    await alertAdmin(config, "Instagram: someone needs a human", `${event.username || event.senderId} wrote: "${event.text.slice(0, 300)}". The agent answered and paused itself for this person for ${config.settings.handoffHours} h.`).catch(() => undefined);
  }
  const buttons = toStepButtons(out);
  return {
    kind: out.handoff ? "handoff" : "replied",
    reason: `AI agent · ${out.stage} · exchange ${state.exchanges}${persist ? " · lead saved" : ""} (conf ${out.confidence.toFixed(2)}, ${out.lang}, ${usage.inputTokens}+${usage.outputTokens} tok)`,
    ruleName: "AI agent",
    steps: [{ type: "dm", recipientId: event.senderId, text: out.reply, ...(buttons.length ? { buttons } : {}) }],
    tags: out.handoff ? ["handoff", "ai"] : ["ai"],
  };
}
