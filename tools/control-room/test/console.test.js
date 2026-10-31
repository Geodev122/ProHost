/* Drives the built console in Chromium against an in-memory Google Sheet (a mock of the Sheets connector).
 * Needs functions/lib (cd functions && npm run build) for the ProHost starting rows. Run: node tools/control-room/test/console.test.js */
const assert = require("node:assert/strict");
const path = require("node:path");
const fs = require("node:fs");
const { chromium } = require("playwright");
const LIB = path.join(__dirname, "../../../functions/lib/instagram");
const { PROHOST_PROFILE, PROHOST_SOURCES } = require(`${LIB}/brands`);
const T = require(`${LIB}/registryTabs`);
const R = require(`${LIB}/rulesSheet`);
require("../build.js");
const html = fs.readFileSync(path.join(__dirname, "../dist/control-room.html"), "utf8");

const sheet = {};
const put = (tab, rows) => { sheet[tab] = rows.map((r) => r.slice()); };
function seedBrand(prefix, profile, extra = {}) {
  put(prefix + "Rules", [[...R.RULE_COLUMNS]]);
  put(prefix + "Log", [[...R.LOG_COLUMNS]]);
  put(prefix + "Contacts", [[...R.CONTACT_COLUMNS]]);
  put(prefix + "Status", [["key", "value"], ["mode", "dry-run"], ...(extra.status || [])]);
  put(prefix + "Knowledge", [["question", "answer"]]);
  put(prefix + "Learn", [["time", "lang", "question", "times", "status", "id"]]);
  put(prefix + "Leads", [[...R.LEAD_COLUMNS]]);
  put(prefix + "Profile", [["key", "value"], ...T.profileRows(profile)]);
  put(prefix + "Buttons", [[...T.BUTTON_COLUMNS], ...T.buttonRows(profile.buttons)]);
  put(prefix + "Playbooks", [[...T.PLAYBOOK_COLUMNS], ...T.playbookRows(profile.playbooks)]);
  put(prefix + "Sources", [[...T.SOURCE_COLUMNS], ...(extra.sources || [])]);
  put(prefix + "KnowledgeDraft", [["section", "text", "sources", "status"], ...(extra.draft || [])]);
}
put("Brands", [[...T.BRAND_COLUMNS], ["prohost", "ProHost", "TRUE", "", "the original account"]]);
put("Platforms", [[...T.PLATFORM_COLUMNS], ["prohost", "instagram", "17841400", "", "", "", "", "", "TRUE", "connected @prohost.app"], ["prohost", "whatsapp", "", "", "", "", "", "en", "FALSE", "missing: phone_number_id"]]);
put("Secrets", [[...T.SECRET_COLUMNS], ["prohost", "instagram", "access_token", "", "set …9f2a · valid, @prohost.app · 2026-10-30"]]);
seedBrand("", PROHOST_PROFILE, { sources: PROHOST_SOURCES, status: [["health_account", "@prohost.app"], ["health_token_days_left", "41"], ["health_platforms", "instagram: connected"]] });
sheet.Rules.push(["wel", "TRUE", "Welcome", "dm_welcome", "", "word", "", "", "", "Hi {first_name}!", "", "", "", "24", "", "0", ""]);

const colNum = (s) => s.split("").reduce((n, c) => n * 26 + c.charCodeAt(0) - 64, 0);
function parseRange(r) {
  const m = /^'((?:[^']|'')+)'!([A-Z]+)(\d+)(?::([A-Z]+)(\d+))?$/.exec(r);
  if (!m) throw new Error("Unable to parse range: " + r);
  return { tab: m[1].replace(/''/g, "'"), c1: colNum(m[2]), r1: +m[3], c2: m[4] ? colNum(m[4]) : colNum(m[2]), r2: m[5] ? +m[5] : +m[3] };
}
const calls = [];
async function callTool(page, tool, input) {
  calls.push({ tool, input });
  const { tab, c1, r1, c2, r2 } = parseRange(input.range);
  if (!sheet[tab]) throw new Error("Unable to parse range: " + input.range + " (no tab " + tab + ")");
  if (tool === "get_values") {
    const rows = sheet[tab].slice(r1 - 1, r2).map((r) => { const x = r.slice(c1 - 1, c2); while (x.length && (x[x.length - 1] === "" || x[x.length - 1] == null)) x.pop(); return x; });
    while (rows.length && !rows[rows.length - 1].length) rows.pop();
    return { payload: { values: rows } };
  }
  if (tool === "update_values") {
    input.values.forEach((row, i) => { const t = sheet[tab]; while (t.length < r1 + i) t.push([]); row.forEach((v, j) => { const line = t[r1 - 1 + i]; while (line.length < c1 + j) line.push(""); line[c1 - 1 + j] = v; }); });
    return { payload: { updatedRange: input.range } };
  }
  throw new Error("unexpected tool " + tool);
}

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1100, height: 900 } });
  const errors = [];
  page.on("pageerror", (e) => errors.push(String(e)));
  page.on("console", (m) => { if (m.type() === "error") errors.push(m.text()); });
  await page.exposeFunction("__sheet", (tool, input) => callTool(page, tool, input));
  await page.addInitScript(() => {
    window.claude = { use: async (name) => name === "mcp" ? { callTool: async (_s, tool, input) => window.__sheet(tool, input) } : name === "downloads" ? { save: async () => undefined } : null };
  });
  await page.route("https://fonts.googleapis.com/**", (r) => r.fulfill({ status: 200, contentType: "text/css", body: "" }));
  await page.route("https://console.test/", (r) => r.fulfill({ status: 200, contentType: "text/html; charset=utf-8", body: html }));
  await page.goto("https://console.test/");
  const text = async () => page.locator("main").innerText();
  const shot = async (n) => { if (process.env.SHOTS) await page.screenshot({ path: path.join(process.env.SHOTS, n + ".png"), fullPage: true }); };
  const until = async (fn, ms = 4000) => { const t = Date.now(); while (Date.now() - t < ms) { try { if (fn()) return; } catch (e) { /* not yet */ } await page.waitForTimeout(40); } };
  const nav = async (v) => { await page.click(`[data-view="${v}"]`); await page.waitForTimeout(60); };
  await page.waitForSelector(".stat", { timeout: 8000 }).catch(async (e) => { console.log("PAGE TEXT:", (await page.locator("body").innerText()).slice(0, 600), "\nERRORS:", errors); throw e; });

  /* ProHost opens with its real profile */
  assert.match(await page.locator("header").innerText(), /ProHost/);
  assert.match(await text(), /Go-live checklist/);
  assert.match(await text(), /Instagram: connected @prohost\.app/);
  await shot("1-overview-prohost");
  await nav("brand");
  await shot("2-brand-prohost");
  assert.equal(await page.inputValue("#b_name"), "ProHost");
  assert.match(await page.inputValue("#b_persona"), /SELLING, HONESTLY/);
  assert.ok((await page.inputValue("#b_kb")).length > 3000, "ProHost knowledge is shown");
  assert.equal(await page.locator("[data-btn]").count(), 6);
  assert.equal(await page.locator("[data-src]").count(), PROHOST_SOURCES.length);
  await nav("agent");
  await shot("3-agent-prohost");
  assert.match(await text(), /Locked core/);
  assert.equal(await page.locator("button[data-act=editplaybook]").count(), PROHOST_PROFILE.playbooks.length);
  assert.match(await text(), /Says it is an AI/);

  /* creating a brand adds a Brands row (the server creates its tabs) */
  await page.click("[data-act=newbrand]");
  await page.fill("#nb_name", "Acme Dental");
  await page.click("[data-act=nbcreate]");
  await until(() => sheet.Brands[2]);
  assert.deepEqual(sheet.Brands[2].slice(0, 3), ["acmedental", "Acme Dental", "TRUE"]);
  assert.match(await text(), /Setting up Acme Dental/);
  // the server (simulated) creates the tabs and the Platforms/Secrets rows
  const { blankProfile } = require(`${LIB}/brands`);
  seedBrand("acmedental_", blankProfile("acmedental", "Acme Dental"));
  for (const p of ["instagram", "whatsapp"]) for (const k of T.SECRET_KEYS[p]) sheet.Secrets.push(["acmedental", p, k, "", ""]);
  await page.click("[data-act=refresh]");
  await page.waitForSelector("#b_name");
  assert.equal(await page.inputValue("#b_name"), "Acme Dental");
  assert.equal(await page.locator("[data-btn]").count(), 0);
  assert.match(await text(), /off/, "ProHost data providers are off");

  /* identity + knowledge + button are written to the brand's prefixed tabs, never ProHost's */
  await page.fill("#b_aname", "Acme's automated assistant");
  await page.fill("#b_purpose", "You help patients book dental visits.");
  await page.fill("#b_persona", "Friendly and brief.");
  await page.click("[data-act=saveidentity]");
  await until(() => Object.fromEntries(sheet.acmedental_Profile.slice(1)).persona === "Friendly and brief.");
  const prof = Object.fromEntries(sheet.acmedental_Profile.slice(1));
  assert.equal(prof.assistant_name, "Acme's automated assistant");
  assert.equal(prof.persona, "Friendly and brief.");
  assert.notEqual(Object.fromEntries(sheet.Profile.slice(1)).assistant_name, "Acme's automated assistant", "ProHost's profile untouched");
  await page.fill("#b_kb", "Acme Dental is open Mon-Fri 9-5. Call 555 0100.");
  await page.click("[data-act=saveknowledgebase]");
  await page.click("[data-act=addbutton]");
  await page.fill("[data-btn='0'] [data-f=id]", "book");
  await page.fill("[data-btn='0'] [data-f=url]", "https://acme.test/book");
  await page.fill("[data-btn='0'] [data-f=en]", "Book now");
  await page.click("[data-act=savebuttons]");
  await until(() => sheet.acmedental_Buttons[1] && sheet.acmedental_Buttons[1][0] === "book" && Object.fromEntries(sheet.acmedental_Profile.slice(1)).knowledge_base.startsWith("Acme Dental is open"));
  assert.equal(Object.fromEntries(sheet.acmedental_Profile.slice(1)).knowledge_base, "Acme Dental is open Mon-Fri 9-5. Call 555 0100.");
  assert.deepEqual(sheet.acmedental_Buttons[1].slice(0, 3), ["book", "https://acme.test/book", "Book now"]);
  assert.equal(sheet.Buttons.length, 7, "ProHost buttons untouched");

  /* sources → build knowledge → review → approve */
  await page.click("[data-act=addsource]");
  await page.fill("[data-src='0'] [data-f=ref]", "https://acme.test/about");
  await page.fill("[data-src='0'] [data-f=note]", "About");
  await page.click("[data-act=buildknowledge]");
  await until(() => Object.fromEntries(sheet.acmedental_Status.slice(1)).build_knowledge === "request");
  assert.deepEqual(sheet.acmedental_Sources[1].slice(0, 3), ["url", "https://acme.test/about", "About"]);
  assert.equal(Object.fromEntries(sheet.acmedental_Status.slice(1)).build_knowledge, "request");
  sheet.acmedental_KnowledgeDraft.push(["ABOUT", "Open Mon-Fri 9-5.", "About", "draft"]);
  await page.click("[data-act=refresh]");
  await page.waitForSelector("[data-draft]");
  await page.click("[data-act=approve]");
  await until(() => sheet.acmedental_KnowledgeDraft[1][3] === "approved");
  assert.equal(sheet.acmedental_KnowledgeDraft[1][3], "approved");

  /* platforms: credentials go to the Secrets tab as pending values and the inputs are cleared */
  await nav("platforms");
  await page.fill("#p_instagram_acct", "178414000000001");
  await page.click("[data-act=saveplatform][data-p=instagram]");
  await until(() => (sheet.Platforms.find((r) => r[0] === "acmedental" && r[1] === "instagram") || [])[2] === "178414000000001");
  await page.waitForTimeout(300);
  await page.fill("#sec_instagram_access_token", "IGAAsecretTOKEN1234567890");
  await page.click("[data-act=gentoken]");
  const verify = await page.inputValue("#sec_instagram_verify_token");
  assert.match(verify, /^[0-9a-f]{32}$/);
  const rowOf = (k) => sheet.Secrets.find((r) => r[0] === "acmedental" && r[1] === "instagram" && r[2] === k);
  await page.click("[data-act=savesecrets][data-p=instagram]");
  await until(() => rowOf("verify_token") && rowOf("verify_token")[3] === verify && rowOf("access_token") && rowOf("access_token")[3]);
  assert.equal(rowOf("access_token")[3], "IGAAsecretTOKEN1234567890");
  assert.match(rowOf("access_token")[4], /^pending/);
  assert.equal(rowOf("verify_token")[3], verify);
  assert.equal(await page.inputValue("#sec_instagram_access_token"), "", "the typed token is cleared from the page");
  assert.ok(!(await page.content()).includes("IGAAsecretTOKEN1234567890"), "the token is not in the page");
  const plat = sheet.Platforms.find((r) => r[0] === "acmedental" && r[1] === "instagram");
  assert.equal(plat[2], "178414000000001");
  // the server (simulated) takes the secret in and blanks the cell
  rowOf("access_token")[3] = ""; rowOf("access_token")[4] = "set …7890 · valid, @acmedental · 2026-10-31";
  await page.click("[data-act=refresh]");
  await page.waitForTimeout(150);
  assert.match(await text(), /set …7890/);
  assert.match(await text(), /https:\/\/pro-host\.tech\/wa-webhook/);

  await shot("4-platforms-acme");
  /* rules: a WhatsApp-only rule lands in the brand's Rules tab with its platform, and the simulator respects it */
  await nav("rules");
  await page.click("[data-act=newrule]");
  await page.fill("#e_name", "Opening hours");
  await page.selectOption("#e_trigger", "dm_keyword");
  await page.selectOption("#e_platform", "whatsapp");
  await page.fill("#e_keywords", "hours, open");
  await page.fill("#e_dm", "We are open Mon-Fri 9-5.");
  await page.check("#e_on");
  await page.click("[data-act=saverule]");
  await until(() => sheet.acmedental_Rules[1] && sheet.acmedental_Rules[1][0] === "opening-hours");
  const rule = sheet.acmedental_Rules[1];
  assert.equal(rule[0], "opening-hours");
  assert.equal(rule[16], "whatsapp");
  assert.equal(sheet.Rules.length, 2, "ProHost rules untouched");
  await shot("5-rules-acme");
  await nav("sim");
  await page.selectOption("#s_kind", "dm");
  await page.fill("#s_text", "what are your hours?");
  await page.selectOption("#s_channel", "whatsapp");
  assert.match(await page.locator("#simout").innerText(), /We are open Mon-Fri 9-5\./);
  await page.selectOption("#s_channel", "instagram");
  assert.match(await page.locator("#simout").innerText(), /Nothing would be sent/);

  /* agent: going live needs a confirmation; settings land in the brand's Status tab */
  await nav("agent");
  await page.click("[data-act=mode][data-m=live]");
  assert.match(await text(), /Go LIVE for Acme Dental/);
  await page.click("[data-act=golive]");
  await until(() => Object.fromEntries(sheet.acmedental_Status.slice(1)).mode === "live");
  assert.equal(Object.fromEntries(sheet.acmedental_Status.slice(1)).mode, "live");
  assert.equal(Object.fromEntries(sheet.Status.slice(1)).mode, "dry-run", "ProHost stays in dry-run");
  await page.fill("#k_vars", "book = https://acme.test/book");
  await page.fill("#k_cap", "30");
  await page.click("[data-act=savesettings]");
  await until(() => Object.fromEntries(sheet.acmedental_Status.slice(1)).hourly_cap === "30" && Object.fromEntries(sheet.acmedental_Status.slice(1)).var_book);
  const st = Object.fromEntries(sheet.acmedental_Status.slice(1));
  assert.equal(st.var_book, "https://acme.test/book");
  assert.equal(st.hourly_cap, "30");

  /* switching back to ProHost shows ProHost's own data again */
  await page.selectOption("#brandsel", "prohost");
  await page.waitForTimeout(250);
  await nav("brand");
  assert.equal(await page.inputValue("#b_name"), "ProHost");

  assert.deepEqual(errors.filter((e) => !/fonts|ERR_|favicon/i.test(e)), []);
  await page.setViewportSize({ width: 390, height: 800 });
  await nav("platforms"); await shot("6-platforms-phone");
  await nav("dash");
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  assert.ok(overflow <= 1, "no horizontal scroll at phone width (" + overflow + ")");
  await browser.close();
  console.log("console tests passed (" + calls.length + " sheet calls)");
})().catch((e) => { console.error(e); process.exit(1); });
