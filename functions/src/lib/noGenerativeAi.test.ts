import { test } from "node:test";
import assert from "node:assert/strict";
import * as fs from "node:fs";
import * as path from "node:path";

/**
 * Guard against Google generative-AI use creeping back (Google Cloud Trust & Safety "Generative AI Prohibited Use
 * Policy" notice of 29 Oct 2026, caused by Vertex GPU jobs on 25 Oct; see docs/GCP_AUDIT_2026-11-04.md, docs/GEMINI_USE.md).
 * Fails when a workflow, script or function source names a Vertex AI / Gemini / Imagen / Lyria / Veo endpoint or model.
 * Allowed: docs, the remediation scripts that disable these APIs, and the Maintenance tasks that audit/disable them.
 * To use a generative model deliberately, discuss with the owner, update docs/GEMINI_USE.md and add the file to ALLOWED.
 */
const ROOT = path.resolve(__dirname, "../../..");
const SCAN_DIRS = [".github/workflows", "scripts", "functions/src", "tools", "marketing/source"];
const ALLOWED = [
  "scripts/gcp-remediation/",
  "scripts/maintenance/run.js",
  "functions/src/lib/noGenerativeAi.test.ts",
  "functions/src/lib/noGenerativeAi.test.js"
];
const EXT = new Set([".ts", ".js", ".py", ".sh", ".yml", ".yaml", ".json", ".mjs"]);
const FORBIDDEN = [
  /aiplatform\.googleapis\.com/i,
  /generativelanguage\.googleapis\.com/i,
  /publishers\/google\/models/i,
  /\b(gemini|imagen|lyria|veo)-[0-9a-z.\-]+/i,
  /roles\/aiplatform\./i,
  /@google\/generative-ai|@google-cloud\/vertexai|google-genai|google\.generativeai|vertexai/i
];

function walk(dir: string, out: string[]): void {
  if (!fs.existsSync(dir)) return;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === "node_modules" || entry.name === "lib" || entry.name.startsWith(".") && dir !== path.join(ROOT, ".github")) continue;
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full, out);
    else if (EXT.has(path.extname(entry.name))) out.push(full);
  }
}

test("no workflow, script or function source calls Vertex AI / Gemini / Imagen / Lyria / Veo", () => {
  const files: string[] = [];
  for (const d of SCAN_DIRS) walk(path.join(ROOT, d), files);
  const hits: string[] = [];
  for (const f of files) {
    const rel = path.relative(ROOT, f).split(path.sep).join("/");
    if (ALLOWED.some((a) => rel === a || rel.startsWith(a))) continue;
    const text = fs.readFileSync(f, "utf8");
    for (const re of FORBIDDEN) {
      const m = re.exec(text);
      if (m) hits.push(`${rel}: ${m[0]}`);
    }
  }
  assert.deepEqual(hits, [], `generative-AI references found (see docs/GEMINI_USE.md):\n${hits.join("\n")}`);
});
