import { test } from "node:test";
import assert from "node:assert/strict";
import { RULE_COLUMNS, parseRulesSheet, ruleToRow, logRow } from "./rulesSheet";
import { Rule } from "./types";

const base: Rule = {
  id: "play-link", name: "Play link", enabled: true, trigger: "comment", keywords: ["link", "app"], match: "word",
  commentReplies: ["Sent! 📩", "Check your DMs"], privateReply: "Get it: {link_play}", quickReplies: [], cooldownHours: 24, tags: ["lead"], priority: 1,
};

test("rule round-trips through the sheet row format", () => {
  const { rules, errors } = parseRulesSheet([[...RULE_COLUMNS], ruleToRow(base)]);
  assert.deepEqual(errors, []);
  assert.deepEqual(rules[0], { ...base, mediaId: undefined, dm: undefined, buttonLabel: undefined, buttonUrl: undefined });
});

test("columns are matched by header name, blank rows skipped", () => {
  const { rules } = parseRulesSheet([["trigger", "id", "dm_text", "enabled"], ["story_reply", "s1", "Thanks!", "yes"], ["", "", "", ""]]);
  assert.equal(rules.length, 1);
  assert.equal(rules[0].trigger, "story_reply");
  assert.equal(rules[0].enabled, true);
  assert.equal(rules[0].cooldownHours, 24);
});

test("bad rows are reported with their row number and never loaded", () => {
  const { rules, errors } = parseRulesSheet([[...RULE_COLUMNS],
    ["a", "TRUE", "A", "nonsense", "", "", "", "x", "", "", "", "", "", "", "", ""],
    ["", "TRUE", "B", "comment", "", "", "", "x", "", "", "", "", "", "", "", ""],
    ["c", "TRUE", "C", "comment", "", "", "", "", "", "", "", "", "", "", "", ""],
    ["d", "TRUE", "D", "dm_keyword", "k", "", "", "", "", "hi", "Open", "http://insecure", "", "", "", ""],
    ["e", "TRUE", "E", "comment", "k", "", "", "ok", "", "", "", "", "", "", "", ""],
    ["e", "TRUE", "E2", "comment", "k", "", "", "ok", "", "", "", "", "", "", "", ""]]);
  assert.deepEqual(rules.map((r) => r.id), ["e"]);
  assert.equal(errors.length, 5);
  assert.ok(errors[0].startsWith("row 2"));
  assert.ok(errors.some((e) => e.includes("duplicate")));
  assert.ok(errors.some((e) => e.includes("https://")));
});

test("log row has fixed order and falls back to the sender id", () => {
  const row = logRow("c_1", { createdAt: Date.UTC(2026, 9, 21, 12, 0, 0), kind: "comment", senderId: "u1", text: "link", status: "sent" });
  assert.deepEqual(row.slice(0, 3), ["2026-10-21 12:00:00", "comment", "u1"]);
  assert.equal(row[9], "c_1");
});

import { settingsFromStatus } from "./rulesSheet";
test("status keys become engine settings; blanks and junk are dropped", () => {
  const m = new Map([["hourly_cap", "30"], ["active_from", "9"], ["active_to", "22"], ["utc_offset_minutes", "180"], ["opt_out_words", "stop, بطل"],
    ["var_link_play", "https://pro-host.tech/ig"], ["var_bad key", "x"], ["handoff_hours", ""], ["hourly_cap_x", "1"], ["handoff_words", "agent"]]);
  assert.deepEqual(settingsFromStatus(m), { hourlyCap: 30, activeFromHour: 9, activeToHour: 22, utcOffsetMinutes: 180, optOutWords: ["stop", "بطل"], handoffWords: ["agent"], vars: { link_play: "https://pro-host.tech/ig" } });
  assert.deepEqual(settingsFromStatus(new Map([["hourly_cap", "0"], ["active_from", "99"]])), {});
});

import { aiFromStatus } from "./rulesSheet";
test("ai status keys: enabled flag, validated overrides, junk dropped", () => {
  assert.deepEqual(aiFromStatus(new Map([["ai_workspace_id", "wrkspc_01DSL6bmyXvCcMjKGci1VyXq"]])).workspaceId, "wrkspc_01DSL6bmyXvCcMjKGci1VyXq");
  assert.equal(aiFromStatus(new Map([["ai_workspace_id", "nonsense"]])).workspaceId, undefined);
  assert.deepEqual(aiFromStatus(new Map()), { enabled: false, comments: true, commentDm: true });
  assert.deepEqual(aiFromStatus(new Map([["ai_enabled", "TRUE"], ["ai_provider", "anthropic"], ["ai_model", "claude-haiku-5-5"], ["ai_region", "europe-west1"], ["ai_daily_cap", "100"]])),
    { enabled: true, provider: "anthropic", model: "claude-haiku-5-5", region: "europe-west1", dailyCap: 100, comments: true, commentDm: true });
  assert.deepEqual(aiFromStatus(new Map([["ai_enabled", "yes"], ["ai_provider", "evil"], ["ai_model", "bad model!"], ["ai_daily_cap", "999999"]])), { enabled: true, comments: true, commentDm: true });
  assert.deepEqual(aiFromStatus(new Map([["ai_enabled", "true"], ["ai_comments", "off"], ["ai_comment_dm", "FALSE"]])), { enabled: true, comments: false, commentDm: false });
});
