import * as logger from "firebase-functions/logger";
import { onSchedule } from "firebase-functions/v2/scheduler";
import "../lib/admin";
import { IgConfig, loadAllConfigs } from "./config";
import { brandCol } from "./store";
import { listComments, listRecentMedia } from "./graph";
import { freshComments, toEvent } from "./commentFeed";
import { handleEvent } from "./webhook";

/**
 * Fetches new comments straight from Instagram and runs them through the same pipeline as webhook comments
 * (ig_events dedupes by comment id, so a comment seen by both is answered once). The first run only sets a
 * baseline: old comments are never answered. Returns how many comments were processed.
 */
export async function pollComments(config: IgConfig, now: number, deps = { listRecentMedia, listComments, handleEvent }): Promise<number> {
  const ref = brandCol(config.brandId, "stats").doc("comment_poll");
  const state = (await ref.get()).data();
  if (!state?.since) { await ref.set({ since: now, lastRun: now, lastError: "", processed: 0 }); return 0; }
  const creds = { igUserId: config.igUserId, token: config.accessToken };
  let newest = Number(state.since);
  let processed = 0;
  try {
    for (const mediaId of await deps.listRecentMedia(creds, 5)) {
      const comments = freshComments(await deps.listComments(creds, mediaId), Number(state.since), { id: config.igUserId, username: config.username });
      for (const c of comments) {
        await deps.handleEvent(config, toEvent(c, mediaId), now);
        newest = Math.max(newest, c.timestamp);
        processed++;
      }
    }
    await ref.set({ since: newest, lastRun: now, lastError: "", processed: Number(state.processed || 0) + processed }, { merge: true });
  } catch (e) {
    await ref.set({ lastRun: now, lastError: (e as Error).message.slice(0, 300) }, { merge: true });
    throw e;
  }
  return processed;
}

export const igCommentPoll = onSchedule({ schedule: "every 1 minutes", timeoutSeconds: 120, memory: "256MiB" }, async () => {
  for (const config of await loadAllConfigs()) {
    if (!config.accessToken || !config.igUserId) continue;
    try {
      const n = await pollComments(config, Date.now());
      if (n) logger.info("igCommentPoll", { brand: config.brandId, processed: n });
    } catch (e) {
      logger.error("igCommentPoll failed", { brand: config.brandId, message: (e as Error).message });
    }
  }
});
