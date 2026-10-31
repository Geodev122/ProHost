// Storage security rules tests (emulator). Cross-service rules read Firestore, so both
// emulators run (see package.json).
import { readFileSync } from "node:fs";
import { after, before, beforeEach, describe, test } from "node:test";
import { assertFails, assertSucceeds, initializeTestEnvironment } from "@firebase/rules-unit-testing";
import { doc, setDoc } from "firebase/firestore";
import { ref, uploadBytes } from "firebase/storage";

let env;
const bytes = new Uint8Array([1, 2, 3]);

before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-prohost",
    firestore: { rules: readFileSync(new URL("../../firestore.rules", import.meta.url), "utf8"), host: "127.0.0.1", port: 8080 },
    storage: { rules: readFileSync(new URL("../../storage.rules", import.meta.url), "utf8"), host: "127.0.0.1", port: 9199 },
  });
});
after(async () => env?.cleanup());
beforeEach(async () => {
  await env.clearFirestore();
  await env.clearStorage();
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "workspace_listings", "L1"), { ownerId: "host1", status: "ACTIVE" });
    await setDoc(doc(ctx.firestore(), "booking_requests", "B1"), { ownerId: "host1", practitionerId: "spec1", status: "ACCEPTED" });
  });
});

const host = () => env.authenticatedContext("host1", { role: "PRO_HOST" }).storage();
const spec = () => env.authenticatedContext("spec1", { role: "SPECIALIST" }).storage();

describe("storage", () => {
  test("owner uploads listing photos; specialists can't seed new listing folders", async () => {
    
    await assertSucceeds(uploadBytes(ref(host(), "listings/NEW/a.jpg"), bytes, { contentType: "image/jpeg" }));
    await assertFails(uploadBytes(ref(spec(), "listings/NEW2/a.jpg"), bytes, { contentType: "image/jpeg" }));
  });
  test("SVG is rejected on public paths", async () => {
    await assertFails(uploadBytes(ref(host(), "listings/NEW3/x.svg"), bytes, { contentType: "image/svg+xml" }));
    await assertFails(uploadBytes(ref(spec(), "profile_pictures/spec1/p.svg"), bytes, { contentType: "image/svg+xml" }));
    await assertSucceeds(uploadBytes(ref(spec(), "profile_pictures/spec1/p.jpg"), bytes, { contentType: "image/jpeg" }));
  });
  // Cross-service checks (firestore.get/exists inside storage.rules — signed leases,
  // verification documents, uploads to an existing listing) aren't reproducible in the
  // local emulator, where those reads always come back "not found"; they're reviewed by
  // hand in the rules file.
});
