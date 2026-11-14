// Render the App Store screenshots (iPhone 6.9", 1320x2868) from the store-screenshot source.
// usage: node scripts/ios/render_store_screenshots.js   → ios/store/screenshots/<locale>/NN-name.png (en-US, ar-SA)
// They are captioned mock-ups of the app's screens; replace them with captures of the iOS build before submission.
const path = require("path"), fs = require("fs"), { pathToFileURL } = require("url");
const { chromium } = require("playwright");
const ROOT = path.resolve(__dirname, "../..");
const SRC = pathToFileURL(path.join(ROOT, "play/screenshots/source/shots.html")).href;
const NAMES = ["01-explore-map", "02-search-and-filter", "03-room-details", "04-book-slots",
  "05-my-rentals", "06-list-your-space", "07-accept-requests", "08-occupancy-and-revenue"];
(async () => {
  const b = await chromium.launch({ args: ["--force-color-profile=srgb", "--allow-file-access-from-files"] });
  for (const [locale, lang] of [["en-US", "en"], ["ar-SA", "ar"]]) {
    const out = path.join(ROOT, "ios/store/screenshots", locale);
    fs.mkdirSync(out, { recursive: true });
    const pg = await b.newPage({ viewport: { width: 1320, height: 2868 }, deviceScaleFactor: 1 });
    for (let i = 1; i <= NAMES.length; i++) {
      await pg.goto(`${SRC}?f=ios&lang=${lang}#${i}`);
      await pg.reload();
      await pg.waitForFunction("document.fonts.ready.then(() => true)");
      await pg.waitForFunction("Array.from(document.images).every(i => i.complete)");
      await pg.waitForTimeout(500);
      // JPEG: Apple accepts it, it has no alpha channel, and eight 1320x2868 images stay small.
      await pg.screenshot({ path: path.join(out, `${NAMES[i - 1]}.jpg`), type: "jpeg", quality: 92 });
    }
    await pg.close();
    console.log(locale, "done");
  }
  await b.close();
})();
