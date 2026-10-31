import { IgComment } from "./graph";
import { IgEvent } from "./types";
import { aliasId } from "./aiMemory";

/** Comments to process: newer than the cursor, not ours, not a reply to a thread (handled by webhook only), with text. Pure. */
export function freshComments(list: IgComment[], since: number, self: { id: string; username: string }): IgComment[] {
  return list
    .filter((c) => c.timestamp > since && c.text.trim() && !c.parentId)
    .filter((c) => !(self.id && c.fromId === self.id) && !(self.username && c.username.toLowerCase() === self.username.toLowerCase()))
    .sort((a, b) => a.timestamp - b.timestamp);
}

export const toEvent = (c: IgComment, mediaId: string): IgEvent => ({
  id: `c_${c.id}`, kind: "comment", senderId: c.fromId || aliasId(c.username), username: c.username || undefined,
  text: c.text, mediaId, commentId: c.id, timestamp: c.timestamp,
});

