import { MatchStyle, Rule, TRIGGERS, Trigger } from "./types";

/** Column order of the Rules tab in the "ProHost IG Automations" sheet (the artifact writes the same order). */
export const RULE_COLUMNS = [
  "id", "enabled", "name", "trigger", "keywords", "match", "post_id", "comment_reply", "private_reply", "dm_text",
  "button_label", "button_url", "quick_replies", "cooldown_hours", "tags", "priority",
] as const;

export const LOG_COLUMNS = ["time", "kind", "person", "text", "rule", "status", "reason", "reply", "error", "event_id"] as const;
export const CONTACT_COLUMNS = ["id", "username", "last_seen", "last_text", "tags", "opted_out", "handoff_until"] as const;

const split = (v: string, sep: RegExp) => v.split(sep).map((s) => s.trim()).filter(Boolean);
const truthy = (v: string) => /^(true|yes|y|1|on|✓|✔)$/i.test(v.trim());

export interface ParsedRules { rules: Rule[]; errors: string[] }

/** values[0] is the header row (matched by name, so columns may be reordered); the rest are rules. */
export function parseRulesSheet(values: string[][]): ParsedRules {
  const rules: Rule[] = [];
  const errors: string[] = [];
  if (!values.length) return { rules, errors: ["Rules tab is empty"] };
  const header = values[0].map((h) => String(h).trim().toLowerCase());
  const col = (row: string[], name: string) => String(row[header.indexOf(name)] ?? "").trim();
  const seen = new Set<string>();
  values.slice(1).forEach((row, i) => {
    const line = i + 2;
    if (row.every((c) => !String(c ?? "").trim())) return;
    const id = col(row, "id").replace(/[^A-Za-z0-9_-]/g, "");
    if (!id) { errors.push(`row ${line}: id is missing`); return; }
    if (seen.has(id)) { errors.push(`row ${line}: duplicate id "${id}"`); return; }
    const trigger = col(row, "trigger").toLowerCase() as Trigger;
    if (!TRIGGERS.includes(trigger)) { errors.push(`row ${line} (${id}): trigger must be one of ${TRIGGERS.join(", ")}`); return; }
    const matchRaw = col(row, "match").toLowerCase();
    const match: MatchStyle = matchRaw === "contains" || matchRaw === "any" ? matchRaw : "word";
    const commentReplies = split(col(row, "comment_reply"), /\s*\|\s*/);
    const privateReply = col(row, "private_reply") || undefined;
    const dm = col(row, "dm_text") || undefined;
    if (!commentReplies.length && !privateReply && !dm) { errors.push(`row ${line} (${id}): needs a comment reply, private reply or DM text`); return; }
    const url = col(row, "button_url");
    if (url && !/^https:\/\//i.test(url)) { errors.push(`row ${line} (${id}): button_url must start with https://`); return; }
    const cd = Number(col(row, "cooldown_hours"));
    seen.add(id);
    rules.push({
      id, name: col(row, "name") || id, enabled: truthy(col(row, "enabled")), trigger,
      keywords: split(col(row, "keywords"), /\s*,\s*/), match, mediaId: col(row, "post_id") || undefined,
      commentReplies, privateReply, dm, buttonLabel: col(row, "button_label") || undefined, buttonUrl: url || undefined,
      quickReplies: split(col(row, "quick_replies"), /\s*,\s*/),
      cooldownHours: Number.isFinite(cd) && col(row, "cooldown_hours") !== "" ? Math.max(0, cd) : 24,
      tags: split(col(row, "tags"), /\s*,\s*/), priority: Number(col(row, "priority")) || 0,
    });
  });
  return { rules, errors };
}

/** A rule as a sheet row, in RULE_COLUMNS order (used by seeding and tests). */
export function ruleToRow(r: Rule): string[] {
  return [
    r.id, r.enabled ? "TRUE" : "FALSE", r.name, r.trigger, r.keywords.join(", "), r.match, r.mediaId ?? "",
    r.commentReplies.join(" | "), r.privateReply ?? "", r.dm ?? "", r.buttonLabel ?? "", r.buttonUrl ?? "",
    r.quickReplies.join(", "), String(r.cooldownHours), r.tags.join(", "), String(r.priority),
  ];
}

export function isoTime(ms: number): string { return new Date(ms).toISOString().replace("T", " ").slice(0, 19); }

export function logRow(id: string, e: Record<string, unknown>): string[] {
  const s = (v: unknown) => (typeof v === "string" ? v : v == null ? "" : String(v));
  return [isoTime(Number(e.createdAt) || Date.now()), s(e.kind), s(e.username) || s(e.senderId), s(e.text), s(e.ruleName), s(e.status), s(e.reason), s(e.replies), s(e.error), id];
}
