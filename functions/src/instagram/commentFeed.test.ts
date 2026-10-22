import { test } from "node:test";
import assert from "node:assert/strict";
import { freshComments, toEvent } from "./commentFeed";

const c = (o: Partial<Parameters<typeof toEvent>[0]>) => ({ id: "1", text: "hi", username: "sam", fromId: "u1", timestamp: 2000, parentId: "", ...o });

test("only new, foreign, top-level comments with text are processed, oldest first", () => {
  const self = { id: "me", username: "prohost.app" };
  const out = freshComments([
    c({ id: "old", timestamp: 1000 }), c({ id: "new2", timestamp: 3000 }), c({ id: "new1", timestamp: 2500 }),
    c({ id: "mine", fromId: "me", timestamp: 2600 }), c({ id: "mine2", fromId: "", username: "ProHost.App", timestamp: 2600 }),
    c({ id: "reply", parentId: "p", timestamp: 2700 }), c({ id: "blank", text: "  ", timestamp: 2800 }),
  ], 1500, self);
  assert.deepEqual(out.map((x) => x.id), ["new1", "new2"]);
});

test("a polled comment becomes the same event id the webhook produces", () => {
  const e = toEvent(c({ id: "17900", fromId: "", username: "sam" }), "m1");
  assert.equal(e.id, "c_17900");
  assert.equal(e.kind, "comment");
  assert.equal(e.senderId, "u:sam");
  assert.equal(e.commentId, "17900");
  assert.equal(e.mediaId, "m1");
});
