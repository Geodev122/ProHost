import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import * as path from "node:path";
import { DELETE_ACCOUNT_BODY, renderDeleteAccountPage } from "./deleteAccountPage";

// Compiled tests run from functions/lib/legal; the sources run from functions/src/legal.
const root = path.resolve(__dirname, "../../..");

test("the function's delete-account copy matches public/delete-account.html", () => {
  const html = readFileSync(path.join(root, "public/delete-account.html"), "utf8");
  const a = html.indexOf('<div class="wrap">') + '<div class="wrap">'.length;
  let body = html.slice(a, html.indexOf('<footer class="site">'));
  body = body.slice(0, body.lastIndexOf("</div>")).replace(/^\n+|\n+$/g, "");
  assert.equal(DELETE_ACCOUNT_BODY, body);
});

test("the page names the in-app path, the email route and what is deleted, and needs no Hosting assets", () => {
  const page = renderDeleteAccountPage();
  for (const must of ["Delete Your Account", "Delete Account", "admin@pro-host.tech", "What gets deleted", "app.geonajjar.prohost"]) {
    assert.ok(page.includes(must), `missing: ${must}`);
  }
  assert.ok(!/href="\/site\.css"|src="\/logo/.test(page), "page must not depend on Hosting assets");
});
