#!/usr/bin/env node
/**
 * Browser smoke test for a built Compose-for-Web app (spike now, adminWeb later).
 *
 *   node scripts/migration/web_smoke.js <dist-dir> [--expect-title "firebase:ok"] [--shot out.png] [--timeout 60000]
 *
 * Serves <dist-dir> over HTTP, opens it in Chromium (Playwright), waits for document.title to contain the expected text, fails on any
 * uncaught page error or console error (favicon 404 ignored), checks that a canvas was drawn, and saves a screenshot.
 * Needs `playwright` resolvable (CI: `npm i playwright && npx playwright install chromium --with-deps` in a temp folder, NODE_PATH set).
 */
const http = require("http");
const fs = require("fs");
const path = require("path");
const { chromium } = require("playwright");

const args = process.argv.slice(2);
const dist = path.resolve(args[0] || ".");
const opt = (name, dflt) => { const i = args.indexOf(name); return i >= 0 ? args[i + 1] : dflt; };
const expectTitle = opt("--expect-title", "");
const shot = opt("--shot", "");
const timeout = Number(opt("--timeout", "60000"));
const MIME = { ".html": "text/html", ".js": "text/javascript", ".mjs": "text/javascript", ".wasm": "application/wasm", ".css": "text/css", ".json": "application/json", ".png": "image/png", ".svg": "image/svg+xml", ".ttf": "font/ttf" };

const server = http.createServer((req, res) => {
  const rel = decodeURIComponent(req.url.split("?")[0]);
  const file = path.join(dist, rel === "/" ? "index.html" : rel);
  if (!file.startsWith(dist) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) { res.writeHead(404); res.end(); return; }
  res.writeHead(200, { "Content-Type": MIME[path.extname(file)] || "application/octet-stream", "Cache-Control": "no-store" });
  fs.createReadStream(file).pipe(res);
});

(async () => {
  await new Promise((r) => server.listen(0, "127.0.0.1", r));
  const url = `http://127.0.0.1:${server.address().port}/`;
  const browser = await chromium.launch({ args: ["--no-sandbox"] });
  const page = await browser.newPage({ viewport: { width: 1280, height: 800 } });
  const problems = [];
  page.on("pageerror", (e) => problems.push(`pageerror: ${e.message}`));
  page.on("console", (m) => { if (m.type() === "error" && !/favicon/.test(m.text())) problems.push(`console.error: ${m.text()}`); });
  await page.goto(url);
  let title = "";
  try {
    await page.waitForFunction((t) => document.title && (!t || document.title.includes(t)), expectTitle, { timeout });
    title = await page.title();
  } catch (e) { title = await page.title(); problems.push(`title never matched "${expectTitle}", was "${title}"`); }
  await page.waitForTimeout(2000); // let the first frames draw
  const drawn = await page.evaluate(() => { const c = document.querySelector("canvas"); return !!c && c.width > 0 && c.height > 0; });
  if (!drawn) problems.push("no canvas was drawn");
  if (shot) await page.screenshot({ path: shot });
  await browser.close();
  server.close();
  console.log(`title: ${title}`);
  console.log(problems.length ? problems.map((p) => `PROBLEM ${p}`).join("\n") : "web smoke OK");
  process.exit(problems.length ? 1 : 0);
})().catch((e) => { console.error(e); process.exit(2); });
