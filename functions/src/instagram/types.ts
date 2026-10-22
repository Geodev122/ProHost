/** Shared shapes for the Instagram automation (webhook → engine → Graph API). See docs/INSTAGRAM.md. */

export type EventKind = "comment" | "story_reply" | "story_mention" | "dm" | "postback";

/** One thing a person did to @prohost.app, normalized from Meta's webhook payload. */
export interface IgEvent {
  /** Stable id used to dedupe Meta's retries (comment id, message id, or a hash of the postback). */
  id: string;
  kind: EventKind;
  /** Instagram-scoped id of the person (recipient of our reply). */
  senderId: string;
  username?: string;
  text: string;
  /** Post the comment is on (comments only). */
  mediaId?: string;
  commentId?: string;
  /** Quick-reply / postback payload. */
  payload?: string;
  timestamp: number;
}

export type Trigger = "comment" | "story_reply" | "story_mention" | "dm_keyword" | "dm_welcome" | "dm_default";
export const TRIGGERS: readonly Trigger[] = ["comment", "story_reply", "story_mention", "dm_keyword", "dm_welcome", "dm_default"];

export type MatchStyle = "any" | "word" | "contains";

export interface Rule {
  id: string;
  name: string;
  enabled: boolean;
  trigger: Trigger;
  /** Empty = matches every event of the trigger (e.g. any story reply). */
  keywords: string[];
  /** word = whole word/phrase, contains = substring, any = same as contains (kept for sheet readability). */
  match: MatchStyle;
  /** Restrict a comment rule to one post; blank = any post. */
  mediaId?: string;
  /** Public replies under the comment; one is picked at random. */
  commentReplies: string[];
  /** Private reply to the comment (Instagram "private reply"): sent as the first DM. */
  privateReply?: string;
  /** DM text (story replies / DMs; for comments it follows the private reply when there is no private reply). */
  dm?: string;
  buttonLabel?: string;
  buttonUrl?: string;
  quickReplies: string[];
  /** Same person + same rule at most once per this many hours (0 = no limit). */
  cooldownHours: number;
  tags: string[];
  /** Higher wins when several rules match. */
  priority: number;
}

export interface ContactState {
  optedOut?: boolean;
  /** Epoch ms until which automation is paused for this person (human handoff). */
  handoffUntil?: number;
  /** ruleId → epoch ms of the last time it replied to this person. */
  lastRuleAt?: Record<string, number>;
  /** True once we have ever DM'd or been DM'd by this person. */
  seenBefore?: boolean;
}

export interface EngineSettings {
  optOutWords: string[];
  handoffWords: string[];
  handoffHours: number;
  hourlyCap: number;
  /** Replies already sent in the current hour. */
  sentThisHour: number;
  /** Inclusive local-hour window [from, to) in the timezone below; undefined = always. */
  activeFromHour?: number;
  activeToHour?: number;
  utcOffsetMinutes: number;
  vars: Record<string, string>;
  optOutReply: string;
  handoffReply: string;
}

export type Step =
  | { type: "comment_reply"; commentId: string; text: string }
  | { type: "private_reply"; commentId: string; text: string; buttonLabel?: string; buttonUrl?: string }
  | { type: "dm"; recipientId: string; text: string; buttonLabel?: string; buttonUrl?: string; buttons?: Array<{ label: string; url: string }>; quickReplies?: string[] };

export type DecisionKind = "ignored" | "opt_out" | "handoff" | "replied";

export interface Decision {
  kind: DecisionKind;
  /** Plain-language reason, shown in the Live log. */
  reason: string;
  ruleId?: string;
  ruleName?: string;
  steps: Step[];
  tags: string[];
  /** Set on comments we deliberately do not answer: "negative" alerts the owner, "spam" is only logged. */
  flag?: "negative" | "spam";
  /** Pause between consecutive steps so a multi-message answer reads like a person typing (ms). */
  pauseMs?: number;
}
