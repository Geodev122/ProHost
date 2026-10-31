import { test } from "node:test";
import assert from "node:assert/strict";
import { PROHOST_PROFILE } from "./brands";
import { maskedTail, parseBrands, parseButtonsTab, parsePlatforms, parsePlaybooksTab, parseProfileTab, parseSecrets, buttonRows, playbookRows, profileRows, BUTTON_COLUMNS, PLAYBOOK_COLUMNS } from "./registryTabs";
import { checkSecret } from "./registrySync";

test("Brands tab: valid rows, bad and duplicate ids reported", () => {
  const { brands, errors } = parseBrands([
    ["brand_id", "name", "enabled", "sheet_id", "note"],
    ["prohost", "ProHost", "TRUE", "", ""], ["acme", "Acme Dental", "no", "sheet123", ""], ["Bad Id", "x", "", "", ""], ["acme", "dup", "", "", ""], ["", "", "", "", ""],
  ]);
  assert.deepEqual(brands.map((b) => [b.id, b.enabled]), [["prohost", true], ["acme", false]]);
  assert.equal(errors.length, 2);
});

test("Platforms tab: platform names and numeric account ids are validated", () => {
  const { platforms, errors } = parsePlatforms([
    ["brand_id", "platform", "account_id", "app_id", "waba_id", "phone_number_id", "template_name", "template_lang", "enabled", "status"],
    ["acme", "instagram", "1784 1400", "999", "", "", "", "", "TRUE", ""], ["acme", "whatsapp", "", "", "555", "666", "hello", "ar", "TRUE", ""], ["acme", "telegram", "", "", "", "", "", "", "", ""], ["acme", "instagram", "abc", "", "", "", "", "", "", ""],
  ]);
  assert.equal(platforms.length, 2);
  assert.equal(platforms[0].accountId, "17841400");
  assert.equal(platforms[1].phoneNumberId, "666");
  assert.equal(platforms[1].templateLang, "ar");
  assert.equal(errors.length, 2);
});

test("Secrets tab: only rows with a value are taken in, with strict platform/key checks", () => {
  const { entries, errors } = parseSecrets([
    ["brand_id", "platform", "key", "value", "status"],
    ["acme", "instagram", "access_token", "IGQWRP-secret-token-value", "set …1234"], ["acme", "instagram", "app_secret", "", ""],
    ["acme", "ai", "gemini_key", "AIzaSyExampleKey1234", ""], ["acme", "instagram", "nonsense", "longenoughvalue", ""], ["acme", "sms", "access_token", "longenoughvalue", ""], ["acme", "whatsapp", "access_token", "short", ""],
  ]);
  assert.deepEqual(entries.map((e) => `${e.platform}/${e.key}`), ["instagram/access_token", "ai/gemini_key"]);
  assert.equal(errors.length, 3);
  assert.equal(maskedTail("abcdefghij1234"), "…1234");
  assert.ok(!JSON.stringify(errors).includes("longenoughvalue"), "errors never echo the value");
});

test("Profile, Buttons and Playbooks tabs round-trip ProHost's built-in profile", () => {
  const profile = parseProfileTab([["key", "value"], ...profileRows(PROHOST_PROFILE)]);
  assert.equal(profile.name, "ProHost");
  assert.equal(profile.persona, PROHOST_PROFILE.persona.trim());
  assert.deepEqual(profile.integrations, { offers: true, listings: true, accountLookup: true, release: true });
  const buttons = parseButtonsTab([[...BUTTON_COLUMNS], ...buttonRows(PROHOST_PROFILE.buttons)]);
  assert.deepEqual(buttons.errors, []);
  assert.deepEqual(Object.keys(buttons.buttons), Object.keys(PROHOST_PROFILE.buttons));
  assert.equal(buttons.buttons.download.labels.ar, "حمّل التطبيق");
  const books = parsePlaybooksTab([[...PLAYBOOK_COLUMNS], ...playbookRows(PROHOST_PROFILE.playbooks)]);
  assert.equal(books.length, PROHOST_PROFILE.playbooks.length);
  assert.equal(books[3].text, PROHOST_PROFILE.playbooks[3].text.trim());
  assert.equal(parseButtonsTab([["id", "url"], ["x", "http://no"]]).errors.length, 1);
});

test("an Instagram token is checked against Meta and tells whose account it is", async () => {
  const deps = {
    whoAmI: async () => ({ id: "555", username: "acme" }),
    subscribeApp: async () => true,
    fetch: (async () => ({ ok: true, status: 200, json: async () => ({ verified_name: "Acme", display_phone_number: "+1 555" }) })) as unknown as typeof fetch,
  };
  const entry = { brandId: "acme", platform: "instagram", key: "access_token", value: "t".repeat(20), row: 2 };
  const ok = await checkSecret(entry, "", deps);
  assert.equal(ok.ok, true);
  assert.equal(ok.patch?.igUserId, "555");
  const wrong = await checkSecret(entry, "999", deps);
  assert.equal(wrong.ok, false);
  assert.match(wrong.message, /belongs to account 555/);
  const wa = await checkSecret({ ...entry, platform: "whatsapp" }, "", deps);
  assert.equal(wa.ok, false);
  assert.equal((await checkSecret({ ...entry, platform: "whatsapp" }, "777", deps)).ok, true);
  assert.equal((await checkSecret({ ...entry, platform: "ai", key: "gemini_key" }, "", deps)).message, "stored");
});
