import { test } from "node:test";
import assert from "node:assert/strict";
import { NUDGE_AFTER_MS, closingThanks, detectLang, findEmail, isExitIntent, isNo, nextFollowUp, nudgeTopic } from "./aiFlow";

test("wrapping-up messages are recognised in four languages; acknowledgements and real questions are not", () => {
  for (const t of ["thanks", "Thank you so much!", "bye", "got it, thanks", "merci beaucoup", "gracias", "شكراً", "تمام شكرا", "مع السلامة"]) assert.equal(isExitIntent(t), true, t);
  for (const t of ["ok", "Okay 👍", "sure", "cool", "ok sure", "great", "اوكي", "thanks but how much is it?", "ok and the app?", "how can I list spaces", "", "can you send the link"]) assert.equal(isExitIntent(t), false, t);
});

test("right after our question, only an explicit goodbye ends the chat", () => {
  assert.equal(isExitIntent("thanks", "reply"), false);
  assert.equal(isExitIntent("great thanks", "reply"), false);
  assert.equal(isExitIntent("thanks, bye", "reply"), true);
  assert.equal(isExitIntent("شكرا باي", "reply"), true);
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

test("silence: one nudge 3 h later about what they were looking for, never twice, never outside the window, no goodbye", () => {
  const t0 = 1_000_000_000;
  const w = { dueAt: t0 + NUDGE_AFTER_MS, nudges: 0, lang: "en", name: "Sam Doe", lastUserAt: t0, state: { search: { kind: "clinic", city: "Jounieh" } } };
  assert.equal(NUDGE_AFTER_MS, 3 * 3_600_000);
  assert.equal(nextFollowUp(w, t0 + NUDGE_AFTER_MS - 1), null);
  const nudge = nextFollowUp(w, t0 + NUDGE_AFTER_MS)!;
  assert.equal(nudge.action, "nudge");
  assert.ok(/Sam/.test(nudge.text) && !/Doe/.test(nudge.text) && /a clinic room in Jounieh/.test(nudge.text), nudge.text);
  assert.equal(nextFollowUp({ ...w, nudges: 1 }, t0 + 2 * NUDGE_AFTER_MS), null);
  assert.equal(nextFollowUp({ ...w, closed: true }, t0 + 10 * NUDGE_AFTER_MS), null);
  assert.equal(nextFollowUp({ ...w, lastUserAt: t0 - 24 * 3_600_000 }, t0 + NUDGE_AFTER_MS), null);
  assert.ok(/[؀-ۿ]/.test(nextFollowUp({ ...w, lang: "ar" }, t0 + NUDGE_AFTER_MS)!.text));
  assert.ok(/Anything else/.test(nextFollowUp({ ...w, state: undefined }, t0 + NUDGE_AFTER_MS)!.text));
  assert.equal(nudgeTopic("es", "office", "Beirut"), "una oficina en Beirut");
  assert.equal(nudgeTopic("en"), "");
});

test("thank-you goodbye is localized and names the person", () => {
  assert.ok(/Sam/.test(closingThanks("en", "Sam Doe")) && !/Doe/.test(closingThanks("en", "Sam Doe")));
  assert.ok(/[؀-ۿ]/.test(closingThanks("ar")));
});
