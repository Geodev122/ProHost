import { test } from "node:test";
import * as assert from "node:assert/strict";
import { enforcePlayIntegrity, DecodedIntegrityToken } from "./playIntegrity";

const UID = "user-1";
const nonce = (uid: string) => Buffer.from(`${uid}:abc`).toString("base64url");

function token(over: {
  pkg?: string; uid?: string; ts?: number; app?: string; licence?: string; device?: string[];
} = {}): DecodedIntegrityToken {
  return {
    tokenPayloadExternal: {
      requestDetails: {
        requestPackageName: over.pkg ?? "app.geonajjar.prohost",
        nonce: nonce(over.uid ?? UID),
        timestampMillis: String(over.ts ?? Date.now()),
      },
      appIntegrity: { appRecognitionVerdict: over.app ?? "PLAY_RECOGNIZED" },
      accountDetails: { appLicensingVerdict: over.licence ?? "LICENSED" },
      deviceIntegrity: { deviceRecognitionVerdict: over.device ?? ["MEETS_DEVICE_INTEGRITY"] },
    },
  };
}
const decoder = (t: DecodedIntegrityToken | null) => async () => t;
const blocked = (p: Promise<void>) => assert.rejects(p, (e: { code?: string }) => e.code === "failed-precondition");

test("a Play-recognised, licensed install passes", async () => {
  await enforcePlayIntegrity("t", UID, decoder(token()));
  await enforcePlayIntegrity("t", UID, decoder(token({ app: "UNEVALUATED", licence: "UNEVALUATED" })));
});

test("a decode failure (API / link problem) never locks people out", async () => {
  await enforcePlayIntegrity("t", UID, decoder(null));
});

test("modified or sideloaded builds are blocked", async () => {
  await blocked(enforcePlayIntegrity("t", UID, decoder(token({ app: "UNRECOGNIZED_VERSION" }))));
  await blocked(enforcePlayIntegrity("t", UID, decoder(token({ app: "SOMETHING_NEW" }))));
  await blocked(enforcePlayIntegrity("t", UID, decoder(token({ licence: "UNLICENSED" }))));
});

test("tokens for another app, another user or too old are blocked", async () => {
  await blocked(enforcePlayIntegrity("t", UID, decoder(token({ pkg: "com.evil" }))));
  await blocked(enforcePlayIntegrity("t", UID, decoder(token({ uid: "someone-else" }))));
  await blocked(enforcePlayIntegrity("t", UID, decoder(token({ ts: Date.now() - 11 * 60 * 1000 }))));
});

test("device integrity is logged, not enforced", async () => {
  await enforcePlayIntegrity("t", UID, decoder(token({ device: [] })));
});
