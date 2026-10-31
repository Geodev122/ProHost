import { FieldValue, getFirestore } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { onSchedule } from "firebase-functions/v2/scheduler";
import "../lib/admin";
import { WaitingThread, nextFollowUp } from "./aiFlow";
import { IgConfig, canSend, loadAllConfigs } from "./config";
import { brandCol } from "./store";
import { senderFor } from "./sender";
import { Channel, Step } from "./types";
import { liveOffersText } from "./aiOffers";

/**
 * Silence handling for the DM agent: when the person went quiet after a question, send ONE nudge 3 h later about what they were
 * looking for; no goodbye, the chat stays open. Only threads the agent itself is waiting on (`dueAt`), always inside Meta's 24 h window,
 * never for opted-out or handed-over people.
 */
export async function runFollowUps(config: IgConfig, now: number, send?: (creds: unknown, step: Step) => Promise<unknown>): Promise<number> {
  const db = getFirestore();
  const due = await brandCol(config.brandId, "threads").where("dueAt", "<=", now).limit(50).get();
  let done = 0;
  for (const doc of due.docs) {
    const t = doc.data() as WaitingThread;
    const fu = nextFollowUp(t, now);
    if (!fu) { if (!t.closed) await doc.ref.update({ dueAt: FieldValue.delete() }).catch(() => undefined); continue; }
    // claim the follow-up in a transaction so two scheduler runs never both send it
    const claimed = await db.runTransaction(async (tx) => {
      const cur = (await tx.get(doc.ref)).data() as WaitingThread | undefined;
      if (!cur || cur.dueAt !== t.dueAt) return false;
      tx.update(doc.ref, { nudges: 1, dueAt: FieldValue.delete() });
      return true;
    });
    if (!claimed) continue;
    const contact = (await brandCol(config.brandId, "contacts").doc(doc.id).get()).data() ?? {};
    const blocked = contact.optedOut === true || Number(contact.handoffUntil ?? 0) > now;
    const channel: Channel = doc.id.startsWith("wa:") ? "whatsapp" : "instagram";
    let status = blocked ? "ignored" : canSend(config, channel) ? "sent" : "dry-run";
    let error = "";
    if (status === "sent") {
      try { const step: Step = { type: "dm", recipientId: doc.id, text: fu.text }; if (send) await send({ igUserId: config.igUserId, token: config.accessToken }, step); else await senderFor(config, channel)(step); } catch (e) { status = "failed"; error = (e as Error).message.slice(0, 300); }
    }
    await brandCol(config.brandId, "events").doc(`fu_${doc.id}_${now}`).set({
      kind: "dm", senderId: doc.id, username: String(contact.username ?? ""), text: "(silence: nudge)", status,
      reason: blocked ? "follow-up skipped: opted out or handed to a human" : "AI agent · silence nudge", ruleName: "AI agent", replies: fu.text, error, createdAt: now, processedAt: now, synced: false,
    }).catch(() => undefined);
    done++;
  }
  return done;
}

export const igFollowUps = onSchedule({ schedule: "every 1 minutes", timeoutSeconds: 60, memory: "256MiB" }, async () => {
  for (const config of await loadAllConfigs()) {
    if (!config.ai.enabled) continue;
    try {
      const n = await runFollowUps(config, Date.now());
      if (n) logger.info("igFollowUps", { brand: config.brandId, sent: n });
    } catch (e) {
      logger.error("igFollowUps failed", { brand: config.brandId, message: (e as Error).message });
    }
  }
  await liveOffersText(Date.now()).catch(() => undefined); // keeps ProHost's offers cache fresh even when nobody writes
});
