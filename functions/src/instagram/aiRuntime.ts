import { FieldValue } from "firebase-admin/firestore";
import { brandCol, brandKnowledgeRef } from "./store";
import * as logger from "firebase-functions/logger";
import "../lib/admin";
import { AiConfig, LlmError } from "./aiLlm";
import { formatInsight, insightTag, lookupInsight } from "./aiAccountInsight";
import { AgentRun, ConvState, NEW_STATE, advance, runAgent, toStepButtons } from "./aiAgent";
import { NUDGE_AFTER_MS, closingThanks, detectLang, findEmail, isExitIntent, isNo } from "./aiFlow";
import { liveOffersText, offersContext } from "./aiOffers";
import { getProfile, listConversations, listMessages } from "./graph";
import { commentModeContext, dmHook, publicSeeDm, publicThanks } from "./aiComment";
import { classifyComment, commentLang } from "./commentClass";
import { Step } from "./types";
import { KnowledgeItem, buildKnowledge } from "./aiKnowledge";
import { IgConfig } from "./config";
import { alertAdmin } from "./notify";
import { leadTags as officeLeadTags } from "../lib/officeLeadFields";
import { Decision, IgEvent } from "./types";
import { FLOW_TTL_MS, Turn, aliasId, appendTurns, importHistory } from "./aiMemory";
import { ListingRow, catalogOverview, cleanCriteria, formatMatches, listingButtons, listingsCatalog, searchListings } from "./aiListings";

const HOUR = 3_600_000;
const kCache = new Map<string, { text: string; at: number }>();

/** The brand's knowledge: its business text (+ ProHost's release notes where that integration is on) and the owner-approved Q/A. */
async function loadKnowledge(config: IgConfig, now: number): Promise<string> {
  const hit = kCache.get(config.brandId);
  if (hit && now - hit.at < 60_000) return hit.text;
  let items: KnowledgeItem[] = [];
  try {
    const d = (await brandKnowledgeRef(config.brandId).get()).data();
    items = Array.isArray(d?.items) ? (d.items as KnowledgeItem[]) : [];
  } catch { /* base knowledge only */ }
  const text = buildKnowledge(items, config.brand.knowledgeBase, config.brand.integrations.release ? undefined : "");
  kCache.set(config.brandId, { text, at: now });
  return text;
}

const ignored = (reason: string): Decision => ({ kind: "ignored", reason, steps: [], tags: [] });
const dayKey = (now: number) => new Date(now).toISOString().slice(0, 10).replace(/-/g, "");

/** The person's stored state, minus the flow part (what we waited for) once the chat went quiet for FLOW_TTL_MS. */
export function priorState(thread: Record<string, unknown>, now: number): ConvState {
  const st = (thread.state ?? null) as Partial<ConvState> | null;
  if (!st) return NEW_STATE;
  const stale = !thread.at || now - Number(thread.at) >= FLOW_TTL_MS;
  return { ...NEW_STATE, ...st, lead: { ...(st.lead ?? {}) }, ...(stale ? { awaiting: "" as const } : {}) };
}

/**
 * The thread of a commenter Instagram did not identify (`u:<username>`, see commentFeed.ts) is moved onto their real id
 * the first time they DM us, so the agent remembers the comment it answered.
 */
async function relinkAlias(bid: string, senderId: string, username: string | undefined, thread: Record<string, unknown>): Promise<Record<string, unknown>> {
  if (!username || senderId.startsWith("u:")) return thread;
  const aliasRef = brandCol(bid, "threads").doc(aliasId(username));
  const alias = (await aliasRef.get()).data();
  if (!alias) return thread;
  const turns = appendTurns(Array.isArray(alias.turns) ? alias.turns as Turn[] : [], Array.isArray(thread.turns) ? thread.turns as Turn[] : []);
  const merged = { ...alias, ...thread, turns };
  await brandCol(bid, "threads").doc(senderId).set(merged, { merge: true });
  await aliasRef.delete().catch(() => undefined);
  return merged;
}

/** Earlier messages of this person's Instagram conversation (team, ManyChat, older chats), for a thread we have no turns of. */
async function instagramHistory(config: IgConfig, event: IgEvent): Promise<Turn[]> {
  if (event.channel === "whatsapp" || !config.accessToken || !config.igUserId) return [];
  try {
    const creds = { igUserId: config.igUserId, token: config.accessToken };
    const conv = (await listConversations(creds, 1, fetch, event.senderId))[0];
    if (!conv) return [];
    const msgs = await listMessages(creds, conv.id, 20);
    return importHistory(msgs, config.igUserId, config.username, [event.id.replace(/^m_/, "")]).filter((t) => t.t !== event.text);
  } catch (e) {
    logger.info("ig ai: no Instagram history", { message: (e as Error).message.slice(0, 120) });
    return [];
  }
}

async function catalog(now: number): Promise<ListingRow[] | null> {
  try { return await listingsCatalog(now); } catch (e) {
    logger.warn("ig ai: listings catalogue unavailable", { message: (e as Error).message.slice(0, 160) });
    return null;
  }
}

/**
 * The DM agent. Returns a Decision (replied / handoff / ignored with a reason) or null when the agent is off.
 * It reads the person's whole history with us (stored turns, the rolling memory, earlier Instagram messages) and, when
 * they look for a space, searches the live catalogue and sends listing buttons that open the app.
 * Per-person (6/h) and daily caps, provider errors and a missing key all end in "ignored" with a plain reason,
 * never in a message to the person.
 */
export async function aiDecide(config: IgConfig, event: IgEvent, now: number, f?: typeof fetch): Promise<Decision | null> {
  const ai = config.ai;
  if (!ai.enabled) return null;
  const apiKey = ai.provider === "gemini" ? config.geminiKey : ai.provider === "anthropic" ? config.anthropicKey : "";
  if (ai.provider !== "vertex" && !apiKey) return ignored(`AI agent: no ${ai.provider} key configured (run igsetup with the GitHub secret)`);
  const bid = config.brandId;
  const threadRef = brandCol(bid, "threads").doc(event.senderId);
  let thread: Record<string, unknown> = (await threadRef.get()).data() ?? {};
  thread = await relinkAlias(bid, event.senderId, event.username, thread).catch(() => thread);
  let turns: Turn[] = Array.isArray(thread.turns) ? (thread.turns as Turn[]) : [];
  if (!turns.length) turns = await instagramHistory(config, event);
  const times = (Array.isArray(thread.aiTimes) ? (thread.aiTimes as number[]) : []).filter((t) => now - t < HOUR);
  if (times.length >= ai.perPersonPerHour) return ignored(`AI agent: limit of ${ai.perPersonPerHour} replies per person per hour reached`);
  const dayRef = brandCol(bid, "stats").doc("ai_" + dayKey(now));
  if ((Number((await dayRef.get()).data()?.calls) || 0) >= ai.dailyCap) return ignored(`AI agent: daily cap of ${ai.dailyCap} replies reached`);

  const cfg: AiConfig = { provider: ai.provider, model: ai.model, region: ai.region, apiKey, workspaceId: ai.workspaceId };
  const prior = priorState(thread, now);
  const lang0 = detectLang(event.text, String(thread.lang ?? "en"));
  const lastAt = Number(thread.lastUserAt ?? thread.at ?? 0) || (turns.length ? Number(turns[turns.length - 1].at ?? 0) : 0);
  const lastContactMs = lastAt ? now - lastAt : 0;
  const userTurn: Turn = { r: "u", t: event.text.slice(0, 600), at: now };

  // The person is wrapping up (thanks, bye): one warm goodbye, no model call, no more questions.
  if (turns.length && prior.awaiting !== "email" && isExitIntent(event.text, prior.awaiting)) {
    const text = closingThanks(lang0, String(thread.name ?? prior.name ?? ""));
    await threadRef.set({ at: now, lastAgentAt: now, turns: appendTurns(turns, [userTurn, { r: "a", t: text, at: now }]), state: { ...prior, awaiting: "" }, dueAt: FieldValue.delete(), nudges: 0, closed: true, lang: lang0, lastUserAt: now }, { merge: true }).catch(() => undefined);
    return { kind: "replied", reason: "AI agent · closing (the person is wrapping up)", ruleName: "AI agent", steps: [{ type: "dm", recipientId: event.senderId, text }], tags: ["ai"] };
  }

  // First name from their Instagram profile, once (also for chats a comment opened).
  let name = String(thread.name ?? prior.name ?? "");
  if (!name && event.channel === "whatsapp") name = (event.username ?? "").trim().split(/\s+/)[0]?.replace(/[^\p{L}\p{M}'-]/gu, "").slice(0, 30) ?? "";
  else if (!name && config.accessToken && config.igUserId) {
    const profile = await getProfile({ igUserId: config.igUserId, token: config.accessToken }, event.senderId).catch(() => null);
    name = (profile?.name ?? "").trim().split(/\s+/)[0]?.replace(/[^\p{L}\p{M}'-]/gu, "").slice(0, 30) ?? "";
  }

  const integ = config.brand.integrations;
  const rows = integ.listings ? await catalog(now) : null;
  let extra = `LANGUAGE OF THEIR LAST MESSAGE (from the server): ${/[\u0600-\u06FF]/.test(event.text) ? "Arabic script, reply in Arabic" : "Latin script, reply in the language of their words (English unless they clearly write French, Spanish or Arabic in Latin letters); never switch to Arabic because of the name or the country"}.\n` +
    (integ.offers ? offersContext(await liveOffersText(now).catch(() => "")) + "\n" : "") +
    (integ.listings ? (rows ? catalogOverview(rows) : "LIVE LISTINGS: unavailable right now; do not set searchReady, point them to Explore in the app.") : "");
  let insightTagValue = String(thread.insight ?? "");
  if (insightTagValue) extra += `\nACCOUNT (coarse, from an earlier consented lookup; never recite it): ${insightTagValue}.`;
  const mail = prior.awaiting === "email" && integ.accountLookup ? findEmail(event.text) : null;
  if (mail) {
    const insight = await lookupInsight(mail, now);
    insightTagValue = insightTag(insight);
    extra = `${formatInsight(insight)}\n${extra}`;
  } else if (prior.awaiting === "email" && isNo(event.text)) {
    extra = `ACCOUNT LOOKUP: the person said they are new (or declined). No lookup was made. Continue with download, the Premium offers and the steps.\n${extra}`;
  }
  const state0: ConvState = { ...prior, ...(name ? { name } : {}) };
  const knowledge = await loadKnowledge(config, now);
  let run: AgentRun;
  let calls = 1;
  let search = prior.search;
  let matches: ReturnType<typeof searchListings> = [];
  try {
    run = await runAgent(cfg, knowledge, turns, event.text, f, state0, extra, now, lastContactMs, config.brand);
    if (run.out.search) search = cleanCriteria(run.out.search, prior.search);
    // Enough is known about the space they want: search the live catalogue, then let the agent present the results.
    if (run.out.searchReady && rows && search) {
      matches = searchListings(rows, { ...search, exclude: prior.sent }, now);
      const first = run.usage;
      run = await runAgent(cfg, knowledge, turns, event.text, f, { ...state0, search }, `${extra}\n${formatMatches(matches, search)}`, now, lastContactMs, config.brand);
      run.usage = { ...run.usage, inputTokens: run.usage.inputTokens + first.inputTokens, outputTokens: run.usage.outputTokens + first.outputTokens, cacheReadTokens: run.usage.cacheReadTokens + first.cacheReadTokens };
      calls = 2;
      if (!matches.length) {
        await brandCol(bid, "learn").doc(`${dayKey(now)}_nomatch_${event.id.slice(-10)}`).set({ question: `No listing for: ${JSON.stringify(search).slice(0, 300)}`, lang: run.out.lang, at: now, asked: 1, synced: false }).catch(() => undefined);
      }
    }
  } catch (e) {
    const status = e instanceof LlmError ? e.status : 0;
    logger.warn("ig ai agent failed", { status, message: (e as Error).message.slice(0, 200) });
    if (status === 401 || status === 403) await alertAdmin(config, "Instagram AI agent cannot reach Claude", `${status}: ${(e as Error).message}`).catch(() => undefined);
    return ignored(`AI agent unavailable (${status || "error"}: ${(e as Error).message.slice(0, 120)})`);
  }
  const { out, usage } = run;
  const linkButtons = matches.length ? listingButtons(matches, out.listings) : [];
  if (linkButtons.length) out.listings = linkButtons.map((b) => decodeURIComponent(b.url.split("/listing/")[1].split("?")[0]));
  if (out.confidence === 0 && out.learn) {
    // The model's answer was unusable twice: the fallback text went out; tell the owner once a day.
    const flagRef = brandCol(bid, "stats").doc("ai_" + dayKey(now));
    const flagged = (await flagRef.get()).data()?.fallbackAlerted === true;
    if (!flagged) {
      await flagRef.set({ fallbackAlerted: true }, { merge: true }).catch(() => undefined);
      await alertAdmin(config, "Instagram AI agent sent its fallback reply", `The model returned unusable output twice for @${event.username ?? event.senderId}. The person got the generic reply; the chat is not paused.`).catch(() => undefined);
    }
  }

  const { state, persist } = advance(state0, out, search);
  const closing = out.stage === "close";
  const waiting = !closing && !out.handoff && (out.awaitReply || out.askEmail);
  await Promise.all([
    threadRef.set({ at: now, lastAgentAt: now, turns: appendTurns(turns, [userTurn, { r: "a", t: out.reply.slice(0, 600), at: now }]), aiTimes: [...times, now], state, name, lang: out.lang.slice(0, 2), lastUserAt: now, nudges: 0, closed: closing, insight: insightTagValue,
      ...(event.username ? { username: event.username } : {}),
      dueAt: waiting ? now + NUDGE_AFTER_MS : FieldValue.delete() }, { merge: true }),
    dayRef.set({ calls: FieldValue.increment(calls), inputTokens: FieldValue.increment(usage.inputTokens), outputTokens: FieldValue.increment(usage.outputTokens), cacheReadTokens: FieldValue.increment(usage.cacheReadTokens), day: dayKey(now) }, { merge: true }),
  ]).catch(() => undefined);

  // A lead is stored only after the person said yes to the consent question (see advance()).
  if (persist) {
    const leadRef = brandCol(bid, "leads").doc(event.senderId);
    const before = (await leadRef.get()).data();
    // Office owners (intake mode F) are recorded in the contact book and alerted by website/officeLeadSync.ts (onOfficeLeadSaved).
    const office = persist.kind === "office_owner" ? { tags: officeLeadTags(persist.profession ?? "") } : {};
    await leadRef.set({ ...persist, ...office, lang: out.lang, instagram: event.username ?? "", consentAt: before?.consentAt ?? now, source: "instagram_dm", updatedAt: now, synced: false }, { merge: true }).catch(() => undefined);
    if (persist.kind !== "office_owner" && persist.email && before?.email !== persist.email) {
      await alertAdmin(config, "New Instagram lead", `${persist.name ?? "(no name)"} <${persist.email}> — ${persist.role ?? "role unknown"}; need: ${persist.need ?? "unknown"}. They agreed to be contacted. @${event.username ?? event.senderId}`).catch(() => undefined);
    }
  }
  if (out.learn || out.confidence < 0.5) {
    const id = `${dayKey(now)}_${event.id.slice(-10)}`;
    await brandCol(bid, "learn").doc(id).set({ question: event.text.slice(0, 400), lang: out.lang, at: now, asked: 1, synced: false }).catch(() => undefined);
  }
  if (out.handoff) {
    await alertAdmin(config, "Instagram: someone needs a human", `${event.username || event.senderId} wrote: "${event.text.slice(0, 300)}". The agent answered and paused itself for this person for ${config.settings.handoffHours} h.`).catch(() => undefined);
  }
  // Listing buttons first (they are the answer), then the agent's own buttons; Instagram allows 3.
  const buttons = [...linkButtons, ...toStepButtons(out, config.brand.buttons)].slice(0, 3);
  return {
    kind: out.handoff ? "handoff" : "replied",
    reason: `AI agent · ${out.stage} · exchange ${state.exchanges}${matches.length ? ` · ${linkButtons.length} listing link(s)` : calls === 2 ? " · no listing matched" : ""}${persist ? " · lead saved" : ""} (conf ${out.confidence.toFixed(2)}, ${out.lang}, ${usage.inputTokens}+${usage.outputTokens} tok)`,
    ruleName: "AI agent",
    steps: out.messages.map((text, i) => ({ type: "dm" as const, recipientId: event.senderId, text, ...(i === out.messages.length - 1 && buttons.length ? { buttons } : {}) })),
    pauseMs: 1100,
    tags: out.handoff ? ["handoff", "ai"] : matches.length ? ["ai", "listing-links"] : ["ai"],
  };
}

const DAY = 24 * HOUR;

/**
 * Public comments no keyword rule handled, answered by intent (see commentClass.ts):
 * positive -> public thanks (+ ONE opening DM per person per day), question -> "see your DMs" + the answer as the private reply,
 * negative -> nothing sent, flagged for the owner, neutral/spam -> logged only. Returns null when the comment agent is off.
 * The private reply is added to the person's history (never replacing it), so the DM agent continues from it.
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

  const bid = config.brandId;
  const threadRef = brandCol(bid, "threads").doc(event.senderId);
  const thread = (await threadRef.get()).data() ?? {};
  const turns: Turn[] = Array.isArray(thread.turns) ? (thread.turns as Turn[]) : [];
  const prior = priorState(thread, now);
  const user = event.username;
  const commentTurn: Turn = { r: "u", t: `(public comment under our post) ${event.text.slice(0, 300)}`, at: now };

  if (kind === "positive") {
    const steps: Step[] = [{ type: "comment_reply", commentId: event.commentId, text: publicThanks(lang, user, seed) }];
    const hookDue = ai.commentDm && !(thread.hookAt && now - Number(thread.hookAt) < DAY);
    if (hookDue) {
      const hook = dmHook(lang, user);
      steps.push({ type: "private_reply", commentId: event.commentId, text: hook });
      await threadRef.set({ at: now, lastAgentAt: now, turns: appendTurns(turns, [commentTurn, { r: "a", t: hook, at: now }]), state: { ...prior, exchanges: prior.exchanges + 1, awaiting: "reply" }, lang, hookAt: now, nudges: 0, closed: false, ...(user ? { username: user } : {}) }, { merge: true }).catch(() => undefined);
    }
    return { kind: "replied", reason: `AI comment · positive${hookDue ? " · opening DM" : " · thanks only (DM already sent today)"}`, ruleName: "AI comment", steps, tags: ["ai", "comment-positive"] };
  }

  // question: the answer goes to DMs as the one private reply Instagram allows per comment
  const apiKey = ai.provider === "gemini" ? config.geminiKey : ai.provider === "anthropic" ? config.anthropicKey : "";
  if (ai.provider !== "vertex" && !apiKey) return ignored(`AI agent: no ${ai.provider} key configured`);
  const times = (Array.isArray(thread.aiTimes) ? (thread.aiTimes as number[]) : []).filter((t) => now - t < HOUR);
  if (times.length >= ai.perPersonPerHour) return ignored(`AI agent: limit of ${ai.perPersonPerHour} replies per person per hour reached`);
  const dayRef = brandCol(bid, "stats").doc("ai_" + dayKey(now));
  if ((Number((await dayRef.get()).data()?.calls) || 0) >= ai.dailyCap) return ignored(`AI agent: daily cap of ${ai.dailyCap} replies reached`);
  const cfg: AiConfig = { provider: ai.provider, model: ai.model, region: ai.region, apiKey, workspaceId: ai.workspaceId };
  const integ = config.brand.integrations;
  const rows = integ.listings ? await catalog(now) : null;
  const extra = `LANGUAGE OF THEIR COMMENT: ${lang === "ar" ? "Arabic, reply in Arabic" : lang === "fr" ? "French, reply in French" : lang === "es" ? "Spanish, reply in Spanish" : "English, reply in English"}.\n${commentModeContext(event.text)}\n${integ.offers ? offersContext(await liveOffersText(now).catch(() => "")) : ""}\n${rows ? catalogOverview(rows) : ""}\nDo not set searchReady in comment mode: ask what they need instead, the search happens in the DM chat.`;
  let run: AgentRun;
  try {
    run = await runAgent(cfg, await loadKnowledge(config, now), turns, event.text, f, prior, extra, now, 0, config.brand);
  } catch (e) {
    const status = e instanceof LlmError ? e.status : 0;
    logger.warn("ig comment agent failed", { status, message: (e as Error).message.slice(0, 200) });
    return ignored(`AI agent unavailable (${status || "error"}: ${(e as Error).message.slice(0, 120)})`);
  }
  const { out, usage } = run;
  const text = out.messages.join(" ").slice(0, 600);
  const button = toStepButtons(out, config.brand.buttons)[0];
  const search = out.search ? cleanCriteria(out.search, prior.search) : prior.search;
  const { state } = advance(prior, { ...out, listings: [] }, search);
  await Promise.all([
    threadRef.set({ at: now, lastAgentAt: now, turns: appendTurns(turns, [commentTurn, { r: "a", t: text, at: now }]), aiTimes: [...times, now], state: { ...state, awaiting: out.awaitReply ? "reply" : "" }, lang: out.lang.slice(0, 2), lastUserAt: 0, nudges: 0, closed: false, ...(user ? { username: user } : {}) }, { merge: true }),
    dayRef.set({ calls: FieldValue.increment(1), inputTokens: FieldValue.increment(usage.inputTokens), outputTokens: FieldValue.increment(usage.outputTokens), cacheReadTokens: FieldValue.increment(usage.cacheReadTokens), day: dayKey(now) }, { merge: true }),
  ]).catch(() => undefined);
  if (out.handoff) await alertAdmin(config, "Instagram: a commenter needs a human", `${user || event.senderId} commented: "${event.text.slice(0, 300)}".`).catch(() => undefined);
  const steps: Step[] = [
    { type: "comment_reply", commentId: event.commentId, text: publicSeeDm(lang, user, seed) },
    { type: "private_reply", commentId: event.commentId, text, ...(button ? { buttonLabel: button.label, buttonUrl: button.url } : {}) },
  ];
  return { kind: out.handoff ? "handoff" : "replied", reason: `AI comment · question · answered in DM (conf ${out.confidence.toFixed(2)}, ${out.lang}, ${usage.inputTokens}+${usage.outputTokens} tok)`, ruleName: "AI comment", steps, tags: out.handoff ? ["handoff", "ai"] : ["ai", "comment-question"] };
}
