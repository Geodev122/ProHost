import { test } from "node:test";
import assert from "node:assert/strict";
import { classifyComment, commentLang } from "./commentClass";

test("positive: emoji-only and short praise in four languages", () => {
  for (const t of ["❤️", "🔥🔥🔥", "👏👏", "😍", "Thank you!", "thanks 🙏", "Love it", "Great app", "nice one 👌", "شكرا", "يعطيك العافية", "رائع 👏", "حلو كتير", "Merci beaucoup", "Super projet !", "Gracias, excelente", "Congrats team 🎉"]) {
    assert.equal(classifyComment(t), "positive", t);
  }
});

test("questions and interest: ?, price words, link requests, Arabic", () => {
  for (const t of ["How can I list my space?", "price?", "how much", "Link please", "Interested", "dm me the details", "Where can I download it", "بكم الاشتراك", "كيف أحمل التطبيق؟", "شو الاسعار", "Comment ça marche ?", "¿Cuánto cuesta?", "Is it available in Dubai", "info"]) {
    assert.equal(classifyComment(t), "question", t);
  }
});

test("negative: hostile words and emoji win even with a question", () => {
  for (const t of ["scam", "this is fake", "Worst app ever", "😡", "👎", "نصب", "حرامي", "arnaque", "Is this a scam?", "terrible, don't trust them"]) {
    assert.equal(classifyComment(t), "negative", t);
  }
});

test("spam, links and neutral chatter are not answered", () => {
  assert.equal(classifyComment("follow me for crypto tips https://spam.example"), "spam");
  assert.equal(classifyComment("check out my page www.shop.com"), "spam");
  assert.equal(classifyComment("@sara look at this"), "neutral");
  assert.equal(classifyComment("I was in that city last summer with my cousins and it rained a lot"), "neutral");
  assert.equal(classifyComment(""), "neutral");
});

test("comment language", () => {
  assert.equal(commentLang("شكرا"), "ar");
  assert.equal(commentLang("Merci beaucoup"), "fr");
  assert.equal(commentLang("Gracias"), "es");
  assert.equal(commentLang("Thanks"), "en");
});
