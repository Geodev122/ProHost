import { test } from "node:test";
import assert from "node:assert/strict";
import { blankProfile } from "./brands";
import { parseConfig } from "./config";
import { buildKnowledgeDraft, composeKnowledge, driveFileId, gather, htmlToText, parseDraft, parseRepoRef, parseSources, safePublicUrl } from "./knowledgeBuilder";

test("Sources tab: types and https links are validated", () => {
  const { sources, errors } = parseSources([
    ["type", "url_or_text", "note", "status"],
    ["url", "https://acme.test/about", "About page", ""], ["text", "We open 9-5.", "hours", ""], ["repo", "http://github.com/a/b", "", ""], ["ftp", "x", "", ""], ["drive", "", "", ""], ["", "", "", ""],
  ]);
  assert.deepEqual(sources.map((s) => s.type), ["url", "text"]);
  assert.equal(errors.length, 3);
});

test("the server only fetches public https pages", () => {
  assert.ok(safePublicUrl("https://acme.test/about"));
  for (const bad of ["http://acme.test", "https://localhost/x", "https://127.0.0.1/x", "https://10.0.0.5/", "https://metadata.google.internal/x", "https://intranet/x", "https://user:pw@acme.test/", "https://acme.test:8443/", "file:///etc/passwd", "not a url"]) {
    assert.equal(safePublicUrl(bad), null, bad);
  }
});

test("html becomes readable text without scripts, styles and navigation", () => {
  const t = htmlToText("<html><head><style>a{}</style><script>alert(1)</script></head><body><nav>Menu</nav><h1>Acme &amp; Co</h1><p>We fix teeth.</p><ul><li>Cleaning</li><li>Whitening</li></ul><footer>© x</footer></body></html>");
  assert.equal(t, "Acme & Co\nWe fix teeth.\nCleaning\nWhitening");
});

test("repo and Drive references", () => {
  assert.deepEqual(parseRepoRef("https://github.com/Geodev122/ProHost"), { owner: "Geodev122", repo: "ProHost", path: "" });
  assert.deepEqual(parseRepoRef("https://github.com/o/r#docs/FAQ.md"), { owner: "o", repo: "r", path: "docs/FAQ.md" });
  assert.equal(parseRepoRef("https://gitlab.com/o/r"), null);
  assert.equal(driveFileId("https://docs.google.com/document/d/1AbCdEfGhIjKlMnOpQrStUvWxYz/edit"), "1AbCdEfGhIjKlMnOpQrStUvWxYz");
  assert.equal(driveFileId("https://drive.google.com/open?id=1AbCdEfGhIjKlMnOpQrStUvWxYz"), "1AbCdEfGhIjKlMnOpQrStUvWxYz");
  assert.equal(driveFileId("https://drive.google.com/"), null);
});

const page = (body: string, type = "text/html") => ({ ok: true, status: 200, headers: { get: () => type }, text: async () => body });
const fakeFetch = (async (url: string) => {
  if (String(url).includes("acme.test/about")) return page("<h1>About Acme</h1><p>Open 9 to 5, Mon-Fri. Call +1 555 0100.</p>");
  if (String(url).includes("acme.test/missing")) return { ok: false, status: 404, headers: { get: () => "" }, text: async () => "" };
  if (String(url).includes("readme")) return page("# Acme app\nBook online.", "text/plain");
  throw new Error("unexpected " + url);
}) as unknown as typeof fetch;

test("gathering reads each source and reports failures per source", async () => {
  const g = await gather([
    { type: "url", ref: "https://acme.test/about", note: "About", row: 2 }, { type: "url", ref: "https://acme.test/missing", note: "Gone", row: 3 },
    { type: "text", ref: "Closed on public holidays.", note: "", row: 4 }, { type: "url", ref: "https://127.0.0.1/", note: "", row: 5 }, { type: "repo", ref: "https://github.com/acme/app", note: "", row: 6 },
  ], { fetch: fakeFetch, drive: () => { throw new Error("no drive"); } });
  assert.equal(g[0].status, "read 50 characters");
  assert.match(g[0].text, /Open 9 to 5/);
  assert.match(g[1].status, /FAILED: HTTP 404/);
  assert.equal(g[2].text, "Closed on public holidays.");
  assert.match(g[3].status, /not a public https page/);
  assert.match(g[4].text, /Book online/);
});

test("a draft is built from the sources and parsed into reviewable sections; nothing is applied until approved", async () => {
  const config = parseConfig({ ai: { provider: "vertex" } }, blankProfile("acme", "Acme"));
  let prompt = "";
  const complete = (async (_c: unknown, system: string, _k: string, msgs: Array<{ content: string }>) => {
    prompt = system + msgs[0].content;
    return { text: '```json\n{"sections":[{"section":"ABOUT","text":"Acme is open 9 to 5.","sources":"About"},{"section":"","text":"dropped"}]}\n```', inputTokens: 1, outputTokens: 1, cacheReadTokens: 0, model: "m" };
  }) as never;
  const r = await buildKnowledgeDraft(config, [{ type: "url", ref: "https://acme.test/about", note: "About", row: 2 }], { fetch: fakeFetch, drive: () => { throw new Error("x"); }, complete });
  assert.equal(r.error, "");
  assert.deepEqual(r.sections.map((s) => s.section), ["ABOUT"]);
  assert.match(prompt, /ONLY facts stated in the SOURCES/);
  assert.match(prompt, /Call \+1 555 0100/);
  const none = await buildKnowledgeDraft(config, [{ type: "url", ref: "https://acme.test/missing", note: "", row: 2 }], { fetch: fakeFetch, drive: () => { throw new Error("x"); }, complete });
  assert.equal(none.error, "none of the sources could be read");
  const noKey = await buildKnowledgeDraft({ ...config, ai: { ...config.ai, provider: "gemini" } }, [{ type: "text", ref: "x", note: "", row: 2 }], { fetch: fakeFetch, drive: () => { throw new Error("x"); }, complete });
  assert.match(noKey.error, /no gemini key/);
  assert.deepEqual(parseDraft("nonsense"), []);
});

test("only approved draft rows become knowledge", () => {
  const { text, applied } = composeKnowledge([["About", "Open 9-5.", "src", "approved"], ["Pricing", "Guess", "src", "draft"], ["Contact", "admin@acme.test", "", "APPROVED"], ["", "", "", "approved"]]);
  assert.equal(text, "ABOUT\nOpen 9-5.\n\nCONTACT\nadmin@acme.test");
  assert.deepEqual(applied, [2, 4]);
});
