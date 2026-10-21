import { createHash } from "node:crypto";
import { IgEvent } from "./types";

type Obj = Record<string, unknown>;
const obj = (v: unknown): Obj => (v && typeof v === "object" ? (v as Obj) : {});
const str = (v: unknown): string => (typeof v === "string" ? v : typeof v === "number" ? String(v) : "");

/**
 * Meta webhook body → normalized events. Ignores everything we must not answer:
 * our own messages (echoes), our own comments, read receipts and reactions, empty payloads.
 * `entry[].id` is our own Instagram user id.
 */
export function parseWebhook(body: unknown): IgEvent[] {
  const out: IgEvent[] = [];
  const entries = obj(body).entry;
  if (!Array.isArray(entries)) return out;
  for (const e of entries) {
    const entry = obj(e);
    const selfId = str(entry.id);
    const when = Number(entry.time) || Date.now();

    for (const c of Array.isArray(entry.changes) ? entry.changes : []) {
      const change = obj(c);
      if (change.field !== "comments" && change.field !== "live_comments") continue;
      const v = obj(change.value);
      const from = obj(v.from);
      const id = str(v.id);
      const senderId = str(from.id);
      if (!id || !senderId || senderId === selfId) continue;
      out.push({
        id: `c_${id}`, kind: "comment", senderId, username: str(from.username) || undefined,
        text: str(v.text), mediaId: str(obj(v.media).id) || undefined, commentId: id, timestamp: when * 1000,
      });
    }

    for (const m of Array.isArray(entry.messaging) ? entry.messaging : []) {
      const msg = obj(m);
      const senderId = str(obj(msg.sender).id);
      if (!senderId || senderId === selfId) continue;
      const ts = Number(msg.timestamp) || when * 1000;
      const message = obj(msg.message);
      const postback = obj(msg.postback);

      if (Object.keys(postback).length) {
        const payload = str(postback.payload);
        out.push({
          id: `p_${str(postback.mid) || hash(`${senderId}|${payload}|${ts}`)}`, kind: "postback", senderId,
          text: str(postback.title), payload, timestamp: ts,
        });
        continue;
      }
      if (!Object.keys(message).length || message.is_echo === true || message.is_deleted === true) continue;
      const mid = str(message.mid);
      if (!mid) continue;

      const attachments = Array.isArray(message.attachments) ? message.attachments.map(obj) : [];
      const quick = str(obj(message.quick_reply).payload);
      const isStoryReply = Object.keys(obj(obj(message.reply_to).story)).length > 0;
      const isStoryMention = attachments.some((a) => a.type === "story_mention");

      out.push({
        id: `m_${mid}`,
        kind: isStoryMention ? "story_mention" : isStoryReply ? "story_reply" : "dm",
        senderId, text: str(message.text), payload: quick || undefined, timestamp: ts,
      });
    }
  }
  return out;
}

function hash(s: string): string {
  return createHash("sha256").update(s).digest("hex").slice(0, 20);
}
