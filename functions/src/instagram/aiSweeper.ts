import { FieldValue, getFirestore } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { onSchedule } from "firebase-functions/v2/scheduler";
import "../lib/admin";
import { CLOSE_AFTER_MS, WaitingThread, nextFollowUp } from "./aiFlow";
import { IgConfig, canSend, loadConfig } from "./config";
import { sendStep } from "./graph";
import { liveOffersText } from "./aiOffers";

/**
 * Silence handling for the DM agent: when the person went quiet after a question, send ONE nudge after 2 minutes and a polite goodbye
 * 2 minutes later, then close the chat. Only threads the agent itself is waiting on (`dueAt`), always inside Meta's 24 h window,
 * never for opted-out or handed-over people.
 */
export async function runFollowUps(config: IgConfig, now: number, send = sendStep): Promise<number> {
  const db = getFirestore();
  const due = await db.collection("ig_threads").where("dueAt", "<=", now).limit(50).get();
  let done = 0;
  for (const doc of due.docs) {
    const t = doc.data() as WaitingThread;
    const fu = nextFollowUp(t, now);
    if (!fu) { if (!t.closed) await doc.ref.update({ dueAt: FieldValue.delete() }).catch(() => undefined); continue; }
    // claim the follow-up in a transaction so two scheduler runs never both send it
    const claimed = await db.runTransaction(async (tx) => {
      const cur = (await tx.get(doc.ref)).data() as WaitingThread | undefined;
      if (!cur || cur.dueAt !== t.dueAt) return false;
      tx.update(doc.ref, fu.action === "nudge" ? { nudges: 1, dueAt: now + CLOSE_AFTER_MS } : { closed: true, dueAt: FieldValue.delete() });
      return true;
    });
    if (!claimed) continue;
    const contact = (await db.collection("ig_contacts").doc(doc.id).get()).data() ?? {};
    const blocked = contact.optedOut === true || Number(contact.handoffUntil ?? 0) > now;
    let status = blocked ? "ignored" : canSend(config) ? "sent" : "dry-run";
    let error = "";
    if (status === "sent") {
      try { await send({ igUserId: config.igUserId, token: config.accessToken }, { type: "dm", recipientId: doc.id, text: fu.text }); } catch (e) { status = "failed"; error = (e as Error).message.slice(0, 300); }
    }
    await db.collection("ig_events").doc(`fu_${doc.id}_${now}`).set({
      kind: "dm", senderId: doc.id, username: String(contact.username ?? ""), text: fu.action === "nudge" ? "(silence: nudge)" : "(silence: closing)", status,
      reason: blocked ? "follow-up skipped: opted out or handed to a human" : `AI agent · silence ${fu.action}`, ruleName: "AI agent", replies: fu.text, error, createdAt: now, processedAt: now, synced: false,
    }).catch(() => undefined);
    done++;
  }
  return done;
}

export const igFollowUps = onSchedule({ schedule: "every 1 minutes", timeoutSeconds: 60, memory: "256MiB" }, async () => {
  try {
    const config = await loadConfig();
    if (!config.ai.enabled) return;
    const n = await runFollowUps(config, Date.now());
    if (n) logger.info("igFollowUps", { sent: n });
    await liveOffersText(Date.now()).catch(() => undefined); // keeps the offers cache fresh even when nobody writes
  } catch (e) {
    logger.error("igFollowUps failed", { message: (e as Error).message });
  }
});
