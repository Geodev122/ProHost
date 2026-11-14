/**
 * Report / block (Apple Guideline 1.2), pure rules shared by the callables and their tests.
 * Spec approved by the owner 14 Nov 2026: docs/migration/mockups/report-block.html.
 */
export const REPORT_REASONS = [
  "MISLEADING", // Misleading or false information
  "SCAM", // Scam or fraud
  "OFFENSIVE", // Offensive or inappropriate content
  "UNSAFE", // Unsafe space
  "HARASSMENT", // Harassment or abusive behaviour
  "OTHER", // Something else
] as const;
export type ReportReason = (typeof REPORT_REASONS)[number];

export const REPORT_TARGETS = ["listing", "user"] as const;
export type ReportTarget = (typeof REPORT_TARGETS)[number];

export const MAX_DETAILS = 500;
export const MAX_REPORTS_PER_DAY = 10;
export const MAX_BLOCKED = 500;
/** Apple asks for action within 24 hours; the admin queue counts down to this. */
export const REVIEW_WINDOW_MS = 24 * 60 * 60 * 1000;

export interface ReportInput {
  targetType?: unknown;
  listingId?: unknown;
  targetUid?: unknown;
  bookingId?: unknown;
  reason?: unknown;
  details?: unknown;
  alsoBlock?: unknown;
}

export interface CleanReport {
  targetType: ReportTarget;
  listingId: string | null;
  targetUid: string | null;
  bookingId: string | null;
  reason: ReportReason;
  details: string;
  alsoBlock: boolean;
}

const ID = /^[A-Za-z0-9_-]{1,128}$/;
const id = (v: unknown): string | null => (typeof v === "string" && ID.test(v) ? v : null);

/** Returns the cleaned report, or a message describing what is wrong with the input. */
export function cleanReport(input: ReportInput | null | undefined): CleanReport | string {
  const data = input ?? {};
  if (!REPORT_TARGETS.includes(data.targetType as ReportTarget)) return "Choose what you are reporting.";
  if (!REPORT_REASONS.includes(data.reason as ReportReason)) return "Choose a reason.";
  const targetType = data.targetType as ReportTarget;
  const listingId = id(data.listingId);
  const targetUid = id(data.targetUid);
  if (targetType === "listing" && !listingId) return "The listing is missing.";
  if (targetType === "user" && !targetUid) return "The person is missing.";
  const details = typeof data.details === "string" ? data.details.trim() : "";
  if (details.length > MAX_DETAILS) return `Details can be at most ${MAX_DETAILS} characters.`;
  return {
    targetType,
    listingId,
    targetUid,
    bookingId: id(data.bookingId),
    reason: data.reason as ReportReason,
    details,
    alsoBlock: data.alsoBlock === true,
  };
}

/** True when either person blocked the other: such a pair never exchanges booking requests. */
export function isBlockedPair(aUid: string, aBlocked: unknown, bUid: string, bBlocked: unknown): boolean {
  const list = (v: unknown): string[] => (Array.isArray(v) ? v.filter((x): x is string => typeof x === "string") : []);
  return list(aBlocked).includes(bUid) || list(bBlocked).includes(aUid);
}

export const ADMIN_REPORT_ACTIONS = ["dismiss", "hide_listing", "suspend_user", "reopen"] as const;
export type AdminReportAction = (typeof ADMIN_REPORT_ACTIONS)[number];

/** The status a report takes after an admin action. */
export function statusAfter(action: AdminReportAction): "NEW" | "DISMISSED" | "ACTIONED" {
  if (action === "reopen") return "NEW";
  return action === "dismiss" ? "DISMISSED" : "ACTIONED";
}
