import { test } from "node:test";
import assert from "node:assert/strict";
import { leadFromDoc, needsRecording } from "./officeLeadSync";

const dm = { kind: "office_owner", name: "Karim", email: "Karim@Clinic.com", whatsapp: "+961 70 123 456", profession: "psychologist", city: "Beirut", source: "instagram_dm", instagram: "karim.psy", lang: "ar" };

test("an office lead is recorded once it has an email and a WhatsApp number", () => {
  assert.equal(needsRecording(dm), "karim@clinic.com|+96170123456");
  assert.equal(needsRecording({ ...dm, whatsapp: "" }), null, "WhatsApp still missing");
  assert.equal(needsRecording({ ...dm, email: "" }), null, "email still missing");
  assert.equal(needsRecording({ ...dm, kind: undefined }), null, "ordinary DM leads keep the email alert path");
  assert.equal(needsRecording(undefined), null);
});

test("the same lead is never recorded twice, a changed number records again", () => {
  const key = needsRecording(dm)!;
  assert.equal(needsRecording({ ...dm, recordedFor: key, synced: true }), null);
  assert.equal(needsRecording({ ...dm, recordedFor: key, whatsapp: "+96176701865" }), "karim@clinic.com|+96176701865");
});

test("a DM-agent lead reads as a DM lead for the email and the contact book", () => {
  const l = leadFromDoc(dm);
  assert.equal(l.origin, "dm");
  assert.equal(l.email, "karim@clinic.com");
  assert.equal(l.whatsapp, "+96170123456");
  assert.equal(l.profession, "Psychologist");
  assert.equal(l.lang, "ar");
  assert.equal(l.instagram, "karim.psy");
  assert.equal(leadFromDoc({ ...dm, source: "insta_story_form" }).origin, "form");
});
