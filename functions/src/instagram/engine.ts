import { ContactState, Decision, EngineSettings, IgEvent, Rule, Step, Trigger } from "./types";

/**
 * The rules engine: pure, no I/O. Given one event, the rules, what we know about the person and the
 * safety settings, it returns what to send (or why nothing is sent). The artifact's Simulator runs a
 * JS copy of `normalize`/`matchesKeywords`/`decide` and is checked against the same cases (engine.test.ts).
 */

export const DEFAULT_SETTINGS: EngineSettings = {
  optOutWords: ["stop", "unsubscribe", "cancel"],
  handoffWords: ["agent", "human", "support", "help me"],
  handoffHours: 24,
  hourlyCap: 60,
  sentThisHour: 0,
  utcOffsetMinutes: 0,
  vars: { link_play: "https://pro-host.tech/ig", link_ig: "https://www.instagram.com/prohost.app/" },
  optOutReply: "Done, you won't get any more automatic messages from us. Write to us any time if you change your mind.",
  handoffReply: "Thanks! A real person from the ProHost team will reply here soon.",
};

/** Lowercase, accent-insensitive (Arabic marks and Latin accents), single spaces. */
export function normalize(s: string): string {
  return s.normalize("NFD").replace(/[̀-ًͯ-ٰٟ]/g, "").toLowerCase().replace(/\s+/g, " ").trim();
}

const words = (s: string): string => normalize(s).replace(/[^\p{L}\p{N}]+/gu, " ").trim();

/** True when `text` contains the keyword as a whole word/phrase (word) or as a substring (contains/any). */
export function matchesKeyword(text: string, keyword: string, style: Rule["match"]): boolean {
  const kw = normalize(keyword);
  if (!kw) return false;
  if (style === "word") {
    const k = words(keyword);
    // Pure emoji / symbol keywords have no letters: substring on the normalized text.
    if (!k) return normalize(text).includes(kw);
    return ` ${words(text)} `.includes(` ${k} `);
  }
  return normalize(text).includes(kw);
}

export function matchesRule(rule: Rule, text: string): boolean {
  if (rule.keywords.length === 0) return true;
  return rule.keywords.some((k) => matchesKeyword(text, k, rule.match));
}

export function render(template: string, vars: Record<string, string>): string {
  return template.replace(/\{([a-z_]+)\}/g, (_m, k: string) => vars[k] ?? "").replace(/[ \t]+\n/g, "\n").trim();
}

function localHour(now: number, offsetMinutes: number): number {
  return new Date(now + offsetMinutes * 60_000).getUTCHours();
}

function inActiveHours(now: number, s: EngineSettings): boolean {
  if (s.activeFromHour === undefined || s.activeToHour === undefined || s.activeFromHour === s.activeToHour) return true;
  const h = localHour(now, s.utcOffsetMinutes);
  return s.activeFromHour < s.activeToHour ? h >= s.activeFromHour && h < s.activeToHour : h >= s.activeFromHour || h < s.activeToHour;
}

function ignored(reason: string): Decision {
  return { kind: "ignored", reason, steps: [], tags: [] };
}

function triggersFor(event: IgEvent, contact: ContactState): Trigger[] {
  switch (event.kind) {
    case "comment": return ["comment"];
    case "story_reply": return ["story_reply"];
    case "story_mention": return ["story_mention"];
    default: return contact.seenBefore ? ["dm_keyword", "dm_default"] : ["dm_keyword", "dm_welcome"];
  }
}

export function decide(
  event: IgEvent, rules: Rule[], contact: ContactState, settings: EngineSettings, now: number, rand: () => number = Math.random,
): Decision {
  const text = event.kind === "postback" ? event.payload || event.text : event.text;
  const isMessage = event.kind === "dm" || event.kind === "postback" || event.kind === "story_reply";

  if (contact.optedOut) return ignored("person opted out");
  if (contact.handoffUntil && contact.handoffUntil > now) return ignored("paused for a human reply");
  if (settings.sentThisHour >= settings.hourlyCap) return ignored(`hourly cap of ${settings.hourlyCap} replies reached`);
  if (!inActiveHours(now, settings)) return ignored("outside active hours");
  if (!text.trim() && event.kind !== "story_mention") return ignored("empty message");

  if (isMessage) {
    if (settings.optOutWords.some((w) => matchesKeyword(text, w, "word"))) {
      return { kind: "opt_out", reason: "opt-out word", steps: [{ type: "dm", recipientId: event.senderId, text: settings.optOutReply }], tags: ["opted-out"] };
    }
    if (settings.handoffWords.some((w) => matchesKeyword(text, w, "word"))) {
      return { kind: "handoff", reason: "asked for a human", steps: [{ type: "dm", recipientId: event.senderId, text: settings.handoffReply }], tags: ["handoff"] };
    }
  }

  const triggers = triggersFor(event, contact);
  const candidates = rules
    .filter((r) => r.enabled && triggers.includes(r.trigger))
    .filter((r) => event.kind !== "comment" || !r.mediaId || r.mediaId === event.mediaId)
    .filter((r) => r.trigger === "dm_welcome" || r.trigger === "dm_default" || matchesRule(r, text))
    .sort((a, b) =>
      b.priority - a.priority ||
      Number(b.keywords.length > 0) - Number(a.keywords.length > 0) ||
      triggers.indexOf(a.trigger) - triggers.indexOf(b.trigger));

  if (candidates.length === 0) return ignored("no rule matched");

  const rule = candidates.find((r) => {
    const last = contact.lastRuleAt?.[r.id];
    return !(r.cooldownHours > 0 && last && now - last < r.cooldownHours * 3_600_000);
  });
  if (!rule) return ignored(`"${candidates[0].name}" already replied to this person within its cooldown`);

  const vars: Record<string, string> = { ...settings.vars, username: event.username ?? "", first_name: event.username || "there" };
  const steps = buildSteps(rule, event, vars, rand);
  if (steps.length === 0) return ignored(`"${rule.name}" matched but has no reply text`);
  return { kind: "replied", reason: `rule "${rule.name}"`, ruleId: rule.id, ruleName: rule.name, steps, tags: rule.tags };
}

function buildSteps(rule: Rule, event: IgEvent, vars: Record<string, string>, rand: () => number): Step[] {
  const steps: Step[] = [];
  const button = rule.buttonLabel && rule.buttonUrl ? { buttonLabel: rule.buttonLabel, buttonUrl: rule.buttonUrl } : {};
  if (event.kind === "comment" && event.commentId) {
    if (rule.commentReplies.length) {
      const pick = rule.commentReplies[Math.min(rule.commentReplies.length - 1, Math.floor(rand() * rule.commentReplies.length))];
      const t = render(pick, vars);
      if (t) steps.push({ type: "comment_reply", commentId: event.commentId, text: t });
    }
    const priv = render(rule.privateReply || rule.dm || "", vars);
    if (priv) steps.push({ type: "private_reply", commentId: event.commentId, text: priv, ...button });
    return steps;
  }
  const dm = render(rule.dm || rule.privateReply || "", vars);
  if (dm) steps.push({ type: "dm", recipientId: event.senderId, text: dm, ...button, ...(rule.quickReplies.length ? { quickReplies: rule.quickReplies.slice(0, 13) } : {}) });
  return steps;
}
