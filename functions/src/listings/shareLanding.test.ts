import { test } from "node:test";
import assert from "node:assert/strict";
import { runInNewContext } from "node:vm";
import { appHandoffScript } from "./shareLanding";

const PLAY = "https://play.google.com/store/apps/details?id=app.geonajjar.prohost";

function run(ua: string, href: string) {
  const u = new URL(href);
  const button = { href: "https://pro-host.tech/listing/SPC-1" };
  const location = { href, host: u.host, origin: u.origin, pathname: u.pathname, search: u.search };
  const timers: Array<() => void> = [];
  const js = appHandoffScript(PLAY).replace(/^<script>|<\/script>$/g, "");
  runInNewContext(js, { navigator: { userAgent: ua }, location, URLSearchParams, encodeURIComponent,
    document: { getElementById: (id: string) => (id === "open-app" ? button : null) }, setTimeout: (f: () => void) => timers.push(f) });
  timers.forEach((f) => f());
  return { button: button.href, nav: location.href };
}

test("Android: the button and an automatic attempt open the app on the listing and room (Instagram's browser ignores App Links)", () => {
  const r = run("Mozilla/5.0 (Linux; Android 14) Instagram 300", "https://pro-host.tech/listing/SPC-1?room=SUB-a&utm_source=instagram");
  assert.ok(r.button.startsWith("intent://pro-host.tech/listing/SPC-1?room=SUB-a#Intent;scheme=https;package=app.geonajjar.prohost;"));
  assert.ok(r.button.includes(`S.browser_fallback_url=${encodeURIComponent(PLAY)}`));
  assert.ok(r.nav.startsWith("intent://pro-host.tech/listing/SPC-1?room=SUB-a#Intent;"));
  assert.ok(decodeURIComponent(r.nav).includes("noapp=1"), "falls back to this page, marked so it never loops");
});

test("no app installed (noapp=1) or not Android: no automatic attempt", () => {
  const back = run("Mozilla/5.0 (Linux; Android 14)", "https://pro-host.tech/listing/SPC-1?noapp=1");
  assert.equal(back.nav, "https://pro-host.tech/listing/SPC-1?noapp=1");
  assert.ok(back.button.startsWith("intent://"), "the button still offers the app (or Play)");
  const ios = run("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0)", "https://pro-host.tech/listing/SPC-1");
  assert.equal(ios.button, "https://pro-host.tech/listing/SPC-1"); assert.equal(ios.nav, "https://pro-host.tech/listing/SPC-1");
});
