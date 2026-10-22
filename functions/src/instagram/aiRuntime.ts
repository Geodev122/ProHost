import { FieldValue, getFirestore } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import "../lib/admin";
import { AiConfig, LlmError } from "./aiLlm";
import { formatInsight, insightTag, lookupInsight } from "./aiAccountInsight";
import { ConvState, NEW_STATE, Turn, advance, runAgent, toStepButtons } from "./aiAgent";
import { NUDGE_AFTER_MS, closingThanks, detectLang, findEmail, isExitIntent, isNo } from "./aiFlow";
import { liveOffersText, offersContext } from "./aiOffers";
import { getProfile } from "./graph";
import { commentModeContext, dmHook, publicSeeDm, publicThanks } from "./aiComment";
import { classifyComment, commentLang } from "./commentClass";
import { Step } from "./types";
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
  const lang0 = detectLang(event.text, String(thread.lang ?? "en"));
  const talked = fresh && turns.length > 0;

  // The person is wrapping up (thanks / ok / bye): one warm goodbye, no model call, no more questions.
  if (talked && prior.awaiting !== "email" && isExitIntent(event.text)) {
    const text = closingThanks(lang0, String(thread.name ?? prior.name ?? ""));
    await threadRef.set({ at: now, turns: [...turns, { r: "u", t: event.text.slice(0, 600) }, { r: "a", t: text }].slice(-12), state: { ...prior, awaiting: "" }, dueAt: FieldValue.delete(), nudges: 0, closed: true, lang: lang0, lastUserAt: now }, { merge: true }).catch(() => undefined);
    return { kind: "replied", reason: "AI agent · closing (the person is wrapping up)", ruleName: "AI agent", steps: [{ type: "dm", recipientId: event.senderId, text }], tags: ["ai"] };
  }

  // First name from their Instagram profile (only once per thread; Meta may not return it before the app is live).
  let name = String(thread.name ?? prior.name ?? "");
  if (!name && !turns.length && config.accessToken && config.igUserId) {
    const profile = await getProfile({ igUserId: config.igUserId, token: config.accessToken }, event.senderId).catch(() => null);
    name = (profile?.name ?? "").trim().split(/\s+/)[0]?.replace(/[^\p{L}\p{M}'-]/gu, "").slice(0, 30) ?? "";
  }

  // Waiting for their registration email: an email in the reply is the permission for ONE read-only account lookup.
  let extra = `LANGUAGE OF THEIR LAST MESSAGE (from the server): ${/[\u0600-\u06FF]/.test(event.text) ? "Arabic script, reply in Arabic" : "Latin script, reply in the language of their words (English unless they clearly write French, Spanish or Arabic in Latin letters); never switch to Arabic because of the name or the country"}.\n` + offersContext(await liveOffersText(now).catch(() => ""));
  let insightTagValue = String(thread.insight ?? "");
  const mail = prior.awaiting === "email" ? findEmail(event.text) : null;
  if (mail) {
    const insight = await lookupInsight(mail, now);
    insightTagValue = insightTag(insight);
    extra = `${formatInsight(insight)}\n${extra}`;
  } else if (prior.awaiting === "email" && isNo(event.text)) {
    extra = `ACCOUNT LOOKUP: the person said they are new (or declined). No lookup was made. Continue with download, the Premium offers and the steps.\n${extra}`;
  }
  let run;
  try {
    run = await runAgent(cfg, await loadKnowledge(now), turns, event.text, f, { ...prior, ...(name ? { name } : {}) }, extra);
  } catch (e) {
    const status = e instanceof LlmError ? e.status : 0;
    logger.warn("ig ai agent failed", { status, message: (e as Error).message.slice(0, 200) });
    if (status === 401 || status === 403) await alertAdmin(config, "Instagram AI agent cannot reach Claude", `${status}: ${(e as Error).message}`).catch(() => undefined);
    return ignored(`AI agent unavailable (${status || "error"}: ${(e as Error).message.slice(0, 120)})`);
  }
  const { out, usage } = run;

  const { state: next, persist } = advance({ ...prior, ...(name ? { name } : {}) }, out);
  const state = next;
  const closing = out.stage === "close";
  const waiting = !closing && !out.handoff && (out.awaitReply || out.askEmail);
  await Promise.all([
    threadRef.set({ at: now, turns: [...turns, { r: "u", t: event.text.slice(0, 600) }, { r: "a", t: out.reply.slice(0, 600) }].slice(-12), aiTimes: [...times, now], state, name, lang: out.lang.slice(0, 2), lastUserAt: now, nudges: 0, closed: closing, insight: insightTagValue,
      dueAt: waiting ? now + NUDGE_AFTER_MS : FieldValue.delete() }, { merge: true }),
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
    steps: out.messages.map((text, i) => ({ type: "dm" as const, recipientId: event.senderId, text, ...(i === out.messages.length - 1 && buttons.length ? { buttons } : {}) })),
    pauseMs: 1100,
    tags: out.handoff ? ["handoff", "ai"] : ["ai"],
  };
}

const DAY = 24 * HOUR;

/**
 * Public comments no keyword rule handled, answered by intent (see commentClass.ts):
 * positive -> public thanks (+ ONE opening DM per person per day), question -> "see your DMs" + the answer as the private reply,
 * negative -> nothing sent, flagged for the owner, neutral/spam -> logged only. Returns null when the comment agent is off.
 */
export async function commentDecide(config: IgConfig, event: IgEvent, now: number, f?: typeof fetch): Promise<Decision | null> {
  const ai = config.ai;
  if (!ai.enabled || !ai.comments || event.kind !== "comment" || !event.commentId) return null;
  const kind = classifyComment(event.text);
  const lang = commentLang(event.text);
  const seed = event.commentId;
  if (kind === "negative") return { kind: "ignored", reason: "negative comment: not answered or liked, flagged for the owner", steps: [], tags: ["flagged"], flag: "negative" };
  if (kind === "spam") return { kind: "ignored", reason: "promotional or link comment: not answered", steps: [], tags: ["spam"], flag: "spam" };
  if (kind === "neutral") return ignored("comment needs no answer (neutral)");

  const db = getFirestore();
  const threadRef = db.collection("ig_threads").doc(event.senderId);
  const thread = (await threadRef.get()).data() ?? {};
  const user = event.username;

  if (kind === "positive") {
    const steps: Step[] = [{ type: "comment_reply", commentId: event.commentId, text: publicThanks(lang, user, seed) }];
    const hookDue = ai.commentDm && !(thread.hookAt && now - Number(thread.hookAt) < DAY);
    if (hookDue) {
      const hook = dmHook(lang, user);
      steps.push({ type: "private_reply", commentId: event.commentId, text: hook });
      await threadRef.set({ at: now, turns: [{ r: "u", t: event.text.slice(0, 300) }, { r: "a", t: hook }], state: { ...NEW_STATE, exchanges: 1, awaiting: "reply" }, lang, hookAt: now, nudges: 0, closed: false }, { merge: true }).catch(() => undefined);
    }
    return { kind: "replied", reason: `AI comment · positive${hookDue ? " · opening DM" : " · thanks only (DM already sent today)"}`, ruleName: "AI comment", steps, tags: ["ai", "comment-positive"] };
  }

  // question: the answer goes to DMs as the one private reply Instagram allows per comment
  const apiKey = ai.provider === "gemini" ? config.geminiKey : ai.provider === "anthropic" ? config.anthropicKey : "";
  if (ai.provider !== "vertex" && !apiKey) return ignored(`AI agent: no ${ai.provider} key configured`);
  const times = (Array.isArray(thread.aiTimes) ? (thread.aiTimes as number[]) : []).filter((t) => now - t < HOUR);
  if (times.length >= ai.perPersonPerHour) return ignored(`AI agent: limit of ${ai.perPersonPerHour} replies per person per hour reached`);
  const dayRef = db.collection("ig_stats").doc("ai_" + dayKey(now));
  if ((Number((await dayRef.get()).data()?.calls) || 0) >= ai.dailyCap) return ignored(`AI agent: daily cap of ${ai.dailyCap} replies reached`);
  const cfg: AiConfig = { provider: ai.provider, model: ai.model, region: ai.region, apiKey };
  const extra = `LANGUAGE OF THEIR COMMENT: ${lang === "ar" ? "Arabic, reply in Arabic" : lang === "fr" ? "French, reply in French" : lang === "es" ? "Spanish, reply in Spanish" : "English, reply in English"}.\n${commentModeContext(event.text)}\n${offersContext(await liveOffersText(now).catch(() => ""))}`;
  let run;
  try {
    run = await runAgent(cfg, await loadKnowledge(now), [], event.text, f, NEW_STATE, extra);
  } catch (e) {
    const status = e instanceof LlmError ? e.status : 0;
    logger.warn("ig comment agent failed", { status, message: (e as Error).message.slice(0, 200) });
    return ignored(`AI agent unavailable (${status || "error"}: ${(e as Error).message.slice(0, 120)})`);
  }
  const { out, usage } = run;
  const text = out.messages.join(" ").slice(0, 600);
  const button = toStepButtons(out)[0];
  const { state } = advance(NEW_STATE, out);
  await Promise.all([
    threadRef.set({ at: now, turns: [{ r: "u", t: event.text.slice(0, 300) }, { r: "a", t: text }], aiTimes: [...times, now], state: { ...state, awaiting: out.awaitReply ? "reply" : "" }, lang: out.lang.slice(0, 2), lastUserAt: 0, nudges: 0, closed: false }, { merge: true }),
    dayRef.set({ calls: FieldValue.increment(1), inputTokens: FieldValue.increment(usage.inputTokens), outputTokens: FieldValue.increment(usage.outputTokens), cacheReadTokens: FieldValue.increment(usage.cacheReadTokens), day: dayKey(now) }, { merge: true }),
  ]).catch(() => undefined);
  if (out.handoff) await alertAdmin(config, "Instagram: a commenter needs a human", `${user || event.senderId} commented: "${event.text.slice(0, 300)}".`).catch(() => undefined);
  const steps: Step[] = [
    { type: "comment_reply", commentId: event.commentId, text: publicSeeDm(lang, user, seed) },
    { type: "private_reply", commentId: event.commentId, text, ...(button ? { buttonLabel: button.label, buttonUrl: button.url } : {}) },
  ];
  return { kind: out.handoff ? "handoff" : "replied", reason: `AI comment · question · answered in DM (conf ${out.confidence.toFixed(2)}, ${out.lang}, ${usage.inputTokens}+${usage.outputTokens} tok)`, ruleName: "AI comment", steps, tags: out.handoff ? ["handoff", "ai"] : ["ai", "comment-question"] };
}
