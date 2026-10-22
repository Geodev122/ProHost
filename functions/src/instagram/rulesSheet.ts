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

/**
 * Status-tab keys the artifact's Settings page writes → engine settings overrides (merged into app_config/instagram.settings).
 * Blank / invalid values are left out so the engine defaults apply.
 */
export function settingsFromStatus(status: Map<string, string>): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  const num = (k: string, min: number, max: number): number | undefined => {
    const raw = (status.get(k) ?? "").trim();
    const n = Number(raw);
    return raw !== "" && Number.isFinite(n) && n >= min && n <= max ? Math.round(n) : undefined;
  };
  const list = (k: string): string[] | undefined => {
    const l = split(status.get(k) ?? "", /\s*,\s*/);
    return l.length ? l : undefined;
  };
  const text = (k: string): string | undefined => (status.get(k) ?? "").trim() || undefined;
  const set = (key: string, v: unknown) => { if (v !== undefined) out[key] = v; };
  set("hourlyCap", num("hourly_cap", 1, 1000));
  set("handoffHours", num("handoff_hours", 1, 168));
  set("activeFromHour", num("active_from", 0, 23));
  set("activeToHour", num("active_to", 0, 23));
  set("utcOffsetMinutes", num("utc_offset_minutes", -720, 840));
  set("optOutWords", list("opt_out_words"));
  set("handoffWords", list("handoff_words"));
  set("optOutReply", text("opt_out_reply"));
  set("handoffReply", text("handoff_reply"));
  const vars: Record<string, string> = {};
  for (const [k, v] of status) if (k.startsWith("var_") && /^[a-z_]+$/.test(k.slice(4)) && v.trim()) vars[k.slice(4)] = v.trim();
  if (Object.keys(vars).length) out.vars = vars;
  return out;
}

/** Status keys for the DM agent (ai_enabled, ai_provider, ai_model, ai_region, ai_daily_cap, ai_per_person_per_hour, ai_comments, ai_comment_dm). */
export function aiFromStatus(status: Map<string, string>): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  const t = (k: string) => (status.get(k) ?? "").trim();
  out.enabled = /^(true|yes|1|on)$/i.test(t("ai_enabled"));
  if (["anthropic", "vertex", "gemini"].includes(t("ai_provider"))) out.provider = t("ai_provider");
  if (/^[A-Za-z0-9@._-]{3,80}$/.test(t("ai_model"))) out.model = t("ai_model");
  if (/^[a-z0-9-]{3,30}$/.test(t("ai_region"))) out.region = t("ai_region");
  const cap = Number(t("ai_daily_cap")); if (t("ai_daily_cap") && Number.isFinite(cap) && cap >= 1 && cap <= 5000) out.dailyCap = Math.round(cap);
  const pp = Number(t("ai_per_person_per_hour")); if (t("ai_per_person_per_hour") && Number.isFinite(pp) && pp >= 1 && pp <= 60) out.perPersonPerHour = Math.round(pp);
  const on = (k: string) => !/^(false|no|0|off)$/i.test(t(k));
  out.comments = on("ai_comments");
  out.commentDm = on("ai_comment_dm");
  return out;
}
