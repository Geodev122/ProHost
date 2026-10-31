import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import path from "node:path";
import { CLAIM_PHRASES, RETIRED_CLAIMS } from "./offerClaims";

// Compiled tests run from functions/lib/billing; the sources run from functions/src/billing.
const root = path.resolve(__dirname, "../../..");
const read = (f: string) => readFileSync(path.join(root, f), "utf8");

const PAGES: Array<[string, "en" | "ar"]> = [
  ["public/index.html", "en"],
  ["public/list-your-space.html", "en"],
  ["public/ar.html", "ar"],
  ["public/admin.html", "en"], // the campaign email template
];

for (const [file, lang] of PAGES) {
  test(`${file} states the launch offer the Play catalog sells`, () => {
    const text = read(file);
    for (const phrase of CLAIM_PHRASES[lang]) assert.match(text, phrase, `${file} should match ${phrase}`);
  });
}

test("no page, template, marketing script or voiceover still states a retired offer", () => {
  const files = [
    ...PAGES.map(([f]) => f),
    "marketing/README.md",
    "scripts/marketing/voiceover_en.json",
    "scripts/website/seo-pages.js",
    "scripts/website/build-seo-pages.js",
  ];
  for (const f of files) {
    const text = read(f);
    for (const retired of RETIRED_CLAIMS) assert.doesNotMatch(text, retired, `${f} still matches retired claim ${retired}`);
  }
});
