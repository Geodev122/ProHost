import { test } from "node:test";
import assert from "node:assert/strict";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";

/**
 * Agent boundary (migration plan P9, docs/migration/AGENT_ADAPTER.md). The AI agent (functions/src/instagram) is being decoupled from the ProHost
 * backend so it can move to its own project and serve many apps. Two directions are pinned here:
 *  1. ProHost code must not import the agent, except the function exports in index.ts, adapter/prohostAdapter.ts (the AppAdapter implementation) and the one composition root
 *     that wires the agent's owner-DM channel into the lead sink (website/officeLeadSync.ts).
 *  2. The agent may import only this list of ProHost modules. Each entry is a seam still to be replaced by the AppAdapter interface
 *     (searchListings, accountInsight, offers, leadSink, adminNotify, knowledge, health). The list may shrink, never grow.
 */
const SRC = join(__dirname, "..");
const AGENT = join(SRC, "instagram");

function walk(dir: string): string[] {
  return readdirSync(dir).flatMap((f) => {
    const p = join(dir, f);
    return statSync(p).isDirectory() ? walk(p) : p.endsWith(".ts") && !p.endsWith(".test.ts") ? [p] : [];
  });
}

const IMPORT = /from\s+"([^"]+)"/g;
const importsOf = (file: string) => [...readFileSync(file, "utf8").matchAll(IMPORT)].map((m) => m[1]).filter((s) => s.startsWith("."));

const ALLOWED_PROHOST_IMPORTERS = new Set(["index.ts", join("website", "officeLeadSync.ts"), join("adapter", "prohostAdapter.ts")]);

test("ProHost modules do not import the agent (except the composition root)", () => {
  const offenders: string[] = [];
  for (const file of walk(SRC)) {
    if (file.startsWith(AGENT)) continue;
    const rel = relative(SRC, file);
    if (ALLOWED_PROHOST_IMPORTERS.has(rel)) continue;
    for (const spec of importsOf(file)) {
      if (/(^|\/)instagram(\/|$)/.test(spec)) offenders.push(`${rel} imports ${spec}`);
    }
  }
  assert.deepEqual(offenders, []);
});

// Seams the agent still uses today (target modules relative to src/). Replace them with AppAdapter calls, then delete the entry.
const AGENT_SEAMS = new Set([
  "lib/officeLeadFields", // aiAgent.ts / aiRuntime.ts -> adapter.leadSink
  "lib/whatsappAlerts", // sheetsBridge.ts -> adapter.health
  "lib/runtimeIdentity", // runAs(): deployment identity, stays as a deployment concern
]);

test("the agent imports only the listed ProHost seams", () => {
  const unexpected: string[] = [];
  for (const file of walk(AGENT)) {
    for (const spec of importsOf(file)) {
      const target = relative(SRC, join(file, "..", spec)).replace(/\\/g, "/");
      if (target.startsWith("instagram")) continue; // inside the agent
      if (!AGENT_SEAMS.has(target)) unexpected.push(`${relative(SRC, file)} imports ${target}`);
    }
  }
  assert.deepEqual(unexpected, []);
});
