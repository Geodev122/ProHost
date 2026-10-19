#!/usr/bin/env node
/**
 * Renders the 1200x630 social preview images (public/img/og/<slug>.jpg and public/img/og-hero.jpg) in the look of the
 * home-page hero: navy gradient over a room photo, headline, Google Play badge, app phone and floating cards.
 *
 *   OG_FONT_DIR=<dir with plus-jakarta-sans-latin-700/800-normal.woff2> node scripts/website/build-og-images.js
 *
 * Needs Playwright (global or local) and Chromium. The font files come from the @fontsource/plus-jakarta-sans npm
 * package (package/files/). Without OG_FONT_DIR the system sans-serif is used. Re-run after changing a page headline in
 * scripts/website/seo-pages.js, then commit the images. The Arabic page reuses og-hero.jpg (the Latin font has no Arabic).
 */
const fs = require("fs");
const path = require("path");
const ROOT = path.join(__dirname, "..", "..");
const PUB = path.join(ROOT, "public");
const FONT = process.env.OG_FONT_DIR || "";
const { PAGES } = require("./seo-pages.js");

let chromium;
try { ({ chromium } = require("playwright")); } catch (e) { ({ chromium } = require(process.env.PLAYWRIGHT_PATH || "/opt/node22/lib/node_modules/playwright")); }

const strip = (h) => String(h).replace(/<[^>]+>/g, "");
const esc = (s) => String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
const file = (p) => "file://" + path.join(PUB, p);
const fontFace = (w) => FONT && fs.existsSync(path.join(FONT, `plus-jakarta-sans-latin-${w}-normal.woff2`))
  ? `@font-face{font-family:PJS;font-weight:${w};src:url("file://${path.join(FONT, `plus-jakarta-sans-latin-${w}-normal.woff2`)}") format("woff2")}` : "";

/** Splits a headline into two lines at the word boundary closest to the middle. */
function twoLines(html) {
  const words = html.split(/\s+/); let best = 1, bd = 1e9;
  for (let i = 1; i < words.length; i++) { const a = strip(words.slice(0, i).join(" ")).length, b = strip(words.slice(i).join(" ")).length; if (Math.abs(a - b) < bd) { bd = Math.abs(a - b); best = i; } }
  return words.slice(0, best).join(" ") + "<br>" + words.slice(best).join(" ");
}

function card({ pill, h1, lead, photo }) {
  const longest = Math.max(...h1.split("<br>").map((l) => strip(l).length)), fs = longest <= 20 ? 68 : longest <= 25 ? 58 : 50;
  return `<!doctype html><html><head><meta charset="utf-8"><style>
${fontFace(700)}${fontFace(800)}
*{box-sizing:border-box;margin:0;padding:0}
body{width:1200px;height:630px;position:relative;overflow:hidden;background:#1C2A40;font-family:PJS,Inter,system-ui,sans-serif;color:#fff}
.bg{position:absolute;inset:0;background:linear-gradient(105deg,rgba(20,31,49,.96) 0%,rgba(28,42,64,.88) 45%,rgba(28,42,64,.55) 100%),url("${file("/img/" + photo + ".jpg")}") center/cover}
.brand{position:absolute;left:64px;top:44px;display:flex;align-items:center;gap:12px}
.tile{width:46px;height:46px;border-radius:12px;background:#fff;display:flex;align-items:center;justify-content:center}.tile img{width:36px;height:auto}
.word{font-weight:800;font-size:34px;letter-spacing:-.02em}.word i{font-style:normal;color:#F25F4C}
.pill{position:absolute;left:64px;top:128px;display:inline-flex;align-items:center;gap:12px;background:rgba(242,95,76,.16);border:1.5px solid rgba(242,95,76,.55);color:#FFD6CF;border-radius:99px;padding:8px 22px 8px 8px;font:600 20px Inter,sans-serif}
.pill b{background:#F25F4C;color:#fff;border-radius:99px;padding:5px 14px;font:800 18px PJS,Inter,sans-serif}
h1{position:absolute;left:64px;top:190px;width:700px;font-weight:800;font-size:${fs}px;line-height:1.08;letter-spacing:-.035em}h1 em{font-style:normal;color:#F25F4C}
.lead{position:absolute;left:64px;top:356px;width:540px;font:500 24px/1.45 Inter,sans-serif;color:rgba(255,255,255,.86)}
.play{position:absolute;left:64px;top:508px;display:flex;align-items:center;gap:14px;background:#0D1117;border:1px solid rgba(255,255,255,.2);border-radius:14px;padding:11px 24px 11px 18px}
.play small{display:block;font:600 11px Inter;letter-spacing:.08em;opacity:.8}.play b{display:block;font:700 24px/1.1 PJS,Inter,sans-serif}
.phone{position:absolute;left:800px;top:52px;width:262px;border-radius:38px;background:#0F1520;padding:9px;box-shadow:0 40px 80px -20px rgba(0,0,0,.75),inset 0 0 0 2px #2A3446}
.phone div{border-radius:30px;height:640px;background:url("${file("/img/app-explore.jpg")}") top/cover}
.f{position:absolute;background:#fff;color:#1F2A37;border-radius:16px;box-shadow:0 18px 40px -12px rgba(0,0,0,.5);padding:14px 18px;display:flex;gap:12px;align-items:center;font:500 16px Inter,sans-serif}
.f b{display:block;font:800 19px/1.2 PJS,Inter,sans-serif}.f small{color:#5D6B7E;font-size:15px}.ic{width:44px;height:44px;border-radius:12px;display:grid;place-items:center;font:800 22px PJS,Inter,sans-serif}
</style></head><body><div class="bg"></div>
<div class="brand"><div class="tile"><img src="${file("/logo.png")}" alt=""></div><div class="word">Pro<i>Host</i></div></div>
<div class="pill">${pill}</div>
<h1>${h1}</h1>
${lead ? `<p class="lead">${esc(lead)}</p>` : ""}
<div class="play"><svg width="30" height="32" viewBox="0 0 26 28"><path d="M1.2.5 14.6 14 1.2 27.5c-.5-.3-.8-.9-.8-1.6V2.1c0-.7.3-1.3.8-1.6z" fill="#00D7FE"/><path d="M19.1 9.5 14.6 14 1.2.5c.2-.1.4-.2.7-.2.3 0 .6.1.9.2l16.3 9z" fill="#00F076"/><path d="M19.1 18.5 2.8 27.7c-.3.2-.6.2-.9.2-.3 0-.5-.1-.7-.2L14.6 14l4.5 4.5z" fill="#FF3A44"/><path d="M24.6 14c0 .6-.3 1.2-.9 1.5l-4.6 3L14.6 14l4.5-4.5 4.6 3c.6.3.9.9.9 1.5z" fill="#FFD500"/></svg><span><small>GET IT ON</small><b>Google Play</b></span></div>
<div class="phone"><div></div></div>
<div class="f" style="left:762px;top:206px"><span class="ic" style="background:#FFECE9;color:#F25F4C">!</span><span><b>New request · $625</b><small>Workshop · 25 attendees</small></span></div>
<div class="f" style="left:1010px;top:300px"><span class="ic" style="background:#E5EDF6;color:#2B5A8C">✓</span><span><b>Accepted</b><small>Calendar updated</small></span></div>
<div class="f" style="left:880px;top:496px"><span class="ic" style="background:#E3F4EA;color:#2F8F57">↗</span><span><b>+$3,750 this month</b><small>Example earnings</small></span></div>
</body></html>`;
}

const jobs = [{ out: "img/og-hero.jpg", pill: "<b>4 MONTHS FREE</b> or 50% off your first year", h1: "Turn empty rooms<br>into <em>steady income.</em>",
  lead: "List your clinic, therapy room, office, studio or meeting room and rent it to verified professionals.", photo: "hotel" }];
jobs.push({ out: "img/og/partner.jpg", pill: "Strategic partnership", h1: "Become ProHost's<br><em>strategic partner</em>", lead: "One partner. A share of app revenue and your logo in the app.", photo: "meeting" });
for (const p of PAGES) {
  const first = String(p.lead).split(/(?<=[.!?])\s/)[0];
  jobs.push({ out: `img/og/${p.slug}.jpg`, pill: esc(p.eyebrow), h1: twoLines(p.h1), lead: first.length > 90 ? "" : first, photo: p.img });
}

(async () => {
  const browser = await chromium.launch({ args: ["--allow-file-access-from-files"] });
  const page = await browser.newPage({ viewport: { width: 1200, height: 630 }, deviceScaleFactor: 1 });
  const tmp = path.join(require("os").tmpdir(), "prohost-og.html");
  for (const j of jobs) {
    fs.writeFileSync(tmp, card(j));
    await page.goto("file://" + tmp);
    await page.evaluate(() => document.fonts.ready);
    await page.waitForTimeout(250);
    fs.mkdirSync(path.dirname(path.join(PUB, j.out)), { recursive: true });
    await page.screenshot({ path: path.join(PUB, j.out), type: "jpeg", quality: 88 });
    console.log("wrote", j.out);
  }
  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
