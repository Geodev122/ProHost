import { test } from "node:test";
import assert from "node:assert/strict";
import { DEFAULT_SETTINGS, decide, matchesKeyword, normalize, render } from "./engine";
import { IgEvent, Rule } from "./types";

const NOW = Date.UTC(2026, 9, 21, 12, 0, 0);
const rule = (o: Partial<Rule>): Rule => ({
  id: "r1", name: "Link", enabled: true, trigger: "comment", keywords: ["link"], match: "word", commentReplies: ["Sent you a DM!"],
  privateReply: "Here: {link_play}", quickReplies: [], cooldownHours: 24, tags: ["lead"], priority: 0, ...o,
});
const ev = (o: Partial<IgEvent>): IgEvent => ({ id: "c_1", kind: "comment", senderId: "u1", username: "rana", text: "Link please", mediaId: "m1", commentId: "c1", timestamp: NOW, ...o });
const S = { ...DEFAULT_SETTINGS, vars: { link_play: "https://pro-host.tech/ig" } };

test("normalize is case, accent and Arabic-mark insensitive", () => {
  assert.equal(normalize("  CAFÉ  Link "), "cafe link");
  assert.equal(normalize("مَرْحَبًا"), normalize("مرحبا"));
});

test("word match respects boundaries; contains does not", () => {
  assert.ok(matchesKeyword("send me the LINK!", "link", "word"));
  assert.ok(!matchesKeyword("linkedin", "link", "word"));
  assert.ok(matchesKeyword("linkedin", "link", "contains"));
  assert.ok(matchesKeyword("how much 💰?", "💰", "word"));
  assert.ok(matchesKeyword("send me the app link", "app link", "word"));
});

test("comment rule: public reply + private reply with variables", () => {
  const d = decide(ev({}), [rule({})], {}, S, NOW, () => 0);
  assert.equal(d.kind, "replied");
  assert.deepEqual(d.steps.map((s) => s.type), ["comment_reply", "private_reply"]);
  assert.equal((d.steps[1] as { text: string }).text, "Here: https://pro-host.tech/ig");
  assert.deepEqual(d.tags, ["lead"]);
});

test("comment rule restricted to a post ignores other posts", () => {
  const d = decide(ev({ mediaId: "m2" }), [rule({ mediaId: "m1" })], {}, S, NOW);
  assert.equal(d.kind, "ignored");
});

test("cooldown blocks a repeat, another rule may still answer", () => {
  const contact = { lastRuleAt: { r1: NOW - 3_600_000 } };
  assert.equal(decide(ev({}), [rule({})], contact, S, NOW).kind, "ignored");
  const d = decide(ev({}), [rule({}), rule({ id: "r2", name: "Fallback", keywords: [], priority: -1 })], contact, S, NOW);
  assert.equal(d.ruleId, "r2");
});

test("opt-out and handoff words win, then silence", () => {
  const dm = rule({ id: "d", trigger: "dm_keyword", keywords: ["price"], dm: "Free for 4 months", commentReplies: [], privateReply: undefined });
  assert.equal(decide(ev({ kind: "dm", text: "STOP" }), [dm], {}, S, NOW).kind, "opt_out");
  assert.equal(decide(ev({ kind: "dm", text: "can I talk to a human" }), [dm], {}, S, NOW).kind, "handoff");
  assert.equal(decide(ev({ kind: "dm", text: "price?" }), [dm], { optedOut: true }, S, NOW).kind, "ignored");
  assert.equal(decide(ev({ kind: "dm", text: "price?" }), [dm], { handoffUntil: NOW + 1000 }, S, NOW).kind, "ignored");
  assert.equal(decide(ev({ kind: "dm", text: "price?" }), [dm], {}, S, NOW).kind, "replied");
});

test("first DM uses the welcome rule, later ones the default", () => {
  const welcome = rule({ id: "w", trigger: "dm_welcome", keywords: [], dm: "Welcome", commentReplies: [], privateReply: undefined });
  const dflt = rule({ id: "f", trigger: "dm_default", keywords: [], dm: "Default", commentReplies: [], privateReply: undefined });
  assert.equal(decide(ev({ kind: "dm", text: "hello" }), [welcome, dflt], {}, S, NOW).ruleId, "w");
  assert.equal(decide(ev({ kind: "dm", text: "hello" }), [welcome, dflt], { seenBefore: true }, S, NOW).ruleId, "f");
});

test("story reply and mention rules answer by DM to the sender", () => {
  const sr = rule({ id: "s", trigger: "story_reply", keywords: [], dm: "Thanks for the story reply!", commentReplies: [], privateReply: undefined });
  const d = decide(ev({ kind: "story_reply", text: "🔥" }), [sr], {}, S, NOW);
  assert.deepEqual(d.steps, [{ type: "dm", recipientId: "u1", text: "Thanks for the story reply!" }]);
  const sm = rule({ id: "m", trigger: "story_mention", keywords: [], dm: "Thanks for the mention", commentReplies: [], privateReply: undefined });
  assert.equal(decide(ev({ kind: "story_mention", text: "" }), [sm], {}, S, NOW).kind, "replied");
});

test("higher priority wins; keyword rules beat catch-alls", () => {
  const all = rule({ id: "a", keywords: [] });
  const kw = rule({ id: "k" });
  assert.equal(decide(ev({}), [all, kw], {}, S, NOW).ruleId, "k");
  assert.equal(decide(ev({}), [kw, rule({ id: "p", keywords: [], priority: 5 })], {}, S, NOW).ruleId, "p");
});

test("hourly cap, active hours and disabled rules", () => {
  assert.equal(decide(ev({}), [rule({})], {}, { ...S, sentThisHour: 60 }, NOW).kind, "ignored");
  assert.equal(decide(ev({}), [rule({})], {}, { ...S, activeFromHour: 9, activeToHour: 11 }, NOW).kind, "ignored");
  assert.equal(decide(ev({}), [rule({})], {}, { ...S, activeFromHour: 9, activeToHour: 17 }, NOW).kind, "replied");
  assert.equal(decide(ev({}), [rule({ enabled: false })], {}, S, NOW).kind, "ignored");
});

test("buttons and quick replies are carried on DM steps", () => {
  const r = rule({ trigger: "dm_keyword", dm: "Get it", buttonLabel: "Open", buttonUrl: "https://x.y", quickReplies: ["Host", "Professional"], commentReplies: [], privateReply: undefined });
  const d = decide(ev({ kind: "dm", text: "link" }), [r], {}, S, NOW);
  assert.deepEqual(d.steps[0], { type: "dm", recipientId: "u1", text: "Get it", buttonLabel: "Open", buttonUrl: "https://x.y", quickReplies: ["Host", "Professional"] });
});

test("render fills known variables and drops unknown ones", () => {
  assert.equal(render("Hi {first_name} {nope}!", { first_name: "Rana" }), "Hi Rana !");
});

test("a text-less story reaction is answered, an empty DM or comment is not", () => {
  const sr = rule({ id: "s", trigger: "story_reply", keywords: [], dm: "Thanks!", commentReplies: [], privateReply: undefined });
  assert.equal(decide(ev({ kind: "story_reply", text: "" }), [sr], {}, S, NOW).kind, "replied");
  assert.equal(decide(ev({ kind: "dm", text: "  " }), [rule({ trigger: "dm_default", keywords: [], dm: "x", commentReplies: [], privateReply: undefined })], { seenBefore: true }, S, NOW).kind, "ignored");
  assert.equal(decide(ev({ text: "" }), [rule({})], {}, S, NOW).kind, "ignored");
});
