import { test } from "node:test";
import assert from "node:assert/strict";
import { CLOSE_AFTER_MS, NUDGE_AFTER_MS, closingThanks, detectLang, findEmail, isExitIntent, isNo, nextFollowUp } from "./aiFlow";

test("wrapping-up messages are recognised in four languages, real questions are not", () => {
  for (const t of ["thanks", "Thank you so much!", "ok", "Okay 👍", "bye", "got it, thanks", "merci beaucoup", "gracias", "شكراً", "تمام شكرا", "اوكي", "مع السلامة"]) assert.equal(isExitIntent(t), true, t);
  for (const t of ["thanks but how much is it?", "ok and the app?", "how can I list spaces", "", "can you send the link"]) assert.equal(isExitIntent(t), false, t);
});

test("'no' answers and emails are found", () => {
  for (const t of ["no", "No.", "nope", "no thanks", "لا", "I'm new"]) assert.equal(isNo(t), true, t);
  for (const t of ["nobody@example.com", "not sure what you mean really", "yes"]) assert.equal(isNo(t), false, t);
  assert.equal(findEmail("sure it's Sam.Doe+x@Mail.co.uk."), "sam.doe+x@mail.co.uk");
  assert.equal(findEmail("no email here"), null);
});

test("language follows the script, then the thread's language", () => {
  assert.equal(detectLang("كيف أنزل التطبيق", "en"), "ar");
  assert.equal(detectLang("ok", "fr"), "fr");
  assert.equal(detectLang("ok", "zz"), "en");
});

test("silence: one nudge after 2 minutes, then a goodbye, never twice and never outside the window", () => {
  const t0 = 1_000_000;
  const w = { dueAt: t0 + NUDGE_AFTER_MS, nudges: 0, lang: "en", name: "Sam", lastUserAt: t0 };
  assert.equal(nextFollowUp(w, t0 + NUDGE_AFTER_MS - 1), null);
  const nudge = nextFollowUp(w, t0 + NUDGE_AFTER_MS)!;
  assert.equal(nudge.action, "nudge"); assert.ok(/Sam/.test(nudge.text));
  const close = nextFollowUp({ ...w, nudges: 1, dueAt: t0 + NUDGE_AFTER_MS + CLOSE_AFTER_MS }, t0 + NUDGE_AFTER_MS + CLOSE_AFTER_MS)!;
  assert.equal(close.action, "close"); assert.ok(/busy/.test(close.text) && /another time/.test(close.text));
  assert.equal(nextFollowUp({ ...w, closed: true }, t0 + 10 * NUDGE_AFTER_MS), null);
  assert.equal(nextFollowUp({ ...w, lastUserAt: t0 - 24 * 3_600_000 }, t0 + NUDGE_AFTER_MS), null);
  assert.ok(/تعتني|اعتنِ/.test(nextFollowUp({ ...w, nudges: 1, lang: "ar" }, t0 + NUDGE_AFTER_MS)!.text));
});

test("thank-you goodbye is localized and names the person", () => {
  assert.ok(/Sam/.test(closingThanks("en", "Sam Doe")) && !/Doe/.test(closingThanks("en", "Sam Doe")));
  assert.ok(/[؀-ۿ]/.test(closingThanks("ar")));
});
