import { test } from "node:test";
import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

/**
 * The Instagram agent may READ the app's data (listings, occupancy, the profile a person asked about) but never write it.
 * It writes only its own collections (ig_*) and its own config documents. This scans the agent's sources so a write to app
 * data can't slip in: every app-collection reference must end in .get() in the same expression.
 */
const SRC = join(__dirname, "..", "..", "src", "instagram");
const OWN_CONFIG_DOCS = ["instagram", "instagram_knowledge", "instagram_offers"];
const WRITES = /\.(set|update|add|delete|create)\(/;

const sources = readdirSync(SRC)
  .filter((f) => f.endsWith(".ts") && !f.endsWith(".test.ts"))
  .map((f) => ({ file: f, text: readFileSync(join(SRC, f), "utf8") }));

test("the agent never writes app data", () => {
  const offenders: string[] = [];
  for (const { file, text } of sources) {
    for (const m of text.matchAll(/\.collection\(([^)]*)\)/g)) {
      const arg = m[1].trim();
      if (/^"ig_[a-z_]+"$/.test(arg)) continue; // its own collections
      // the statement this reference belongs to (up to the end of the line that closes it)
      const rest = text.slice(m.index!, m.index! + 400);
      const statement = rest.split(/;\n|\n\s*\n/)[0];
      if (arg === '"app_config"') {
        const doc = /\.doc\("([^"]+)"\)/.exec(statement)?.[1] ?? "";
        if (!OWN_CONFIG_DOCS.includes(doc) && WRITES.test(statement)) offenders.push(`${file}: app_config/${doc}`);
        continue;
      }
      const getAt = statement.indexOf(".get()");
      const write = WRITES.exec(statement);
      if (getAt < 0 || (write && write.index < getAt)) offenders.push(`${file}: ${arg} → ${statement.split("\n")[0].trim()}`);
    }
    // a batch or transaction would hide writes behind a reference
    if (/\.(batch|runTransaction)\(/.test(text)) {
      for (const m of text.matchAll(/\.collection\(("(?!ig_)[^"]+"|[A-Z_]+)\)/g)) {
        if (m[1] !== '"app_config"') offenders.push(`${file}: batch/transaction next to ${m[1]}`);
      }
    }
  }
  assert.deepEqual(offenders, []);
});

test("the scan sees the agent's reads of app data", () => {
  const all = sources.map((s) => s.text).join("\n");
  for (const name of ['"workspace_listings"', '"user_profiles"', '"booking_requests"']) {
    assert.ok(all.includes(`.collection(${name})`), `${name} is read somewhere`);
  }
});
