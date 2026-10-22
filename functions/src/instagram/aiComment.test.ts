import { test } from "node:test";
import assert from "node:assert/strict";
import { commentModeContext, dmHook, publicSeeDm, publicThanks } from "./aiComment";

test("public replies name the commenter, stay in their language and are deterministic per comment", () => {
  assert.match(publicThanks("en", "sam", "c1"), /@sam/);
  assert.equal(publicThanks("en", "sam", "c1"), publicThanks("en", "sam", "c1"));
  assert.match(publicSeeDm("en", "sam", "c1"), /DM/);
  assert.match(publicSeeDm("ar", "sam", "c1"), /[؀-ۿ]/);
  assert.ok(!/@\{u\}/.test(publicThanks("fr", undefined, "x")));
  assert.ok(!/ {2}| [!.,]/.test(publicThanks("es", undefined, "x")));
});

test("the opening DM asks one question and carries no link or price", () => {
  for (const l of ["en", "ar", "fr", "es"] as const) {
    const t = dmHook(l, "sam");
    assert.ok(!/https?:/.test(t) && !/\$|usd/i.test(t));
    assert.equal((t.match(/[?؟]/g) ?? []).length, 1);
  }
});

test("comment mode context quotes the comment safely and asks for ONE message", () => {
  const c = commentModeContext('How much "now"?');
  assert.match(c, /ONE single DM/);
  assert.ok(!c.includes('"now"'));
});
