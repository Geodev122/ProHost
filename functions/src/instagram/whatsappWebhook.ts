import * as logger from "firebase-functions/logger";
import { onRequest } from "firebase-functions/v2/https";
import "../lib/admin";
import { loadAllConfigs } from "./config";
import { brandForVerifyToken, routeEntries } from "./routing";
import { entryPhoneNumberId, parseWhatsAppEntry } from "./whatsapp";
import { handleEvent } from "./webhook";

/** pro-host.tech/wa-webhook (hosting rewrite). GET = Meta's handshake, POST = WhatsApp Business events for every brand. */
export const waWebhook = onRequest({ cors: false, memory: "256MiB", timeoutSeconds: 60 }, async (req, res) => {
  const configs = await loadAllConfigs();
  if (req.method === "GET") {
    const token = String(req.query["hub.verify_token"] ?? "");
    if (req.query["hub.mode"] === "subscribe" && brandForVerifyToken(configs, token, "whatsapp")) res.status(200).type("text/plain").send(String(req.query["hub.challenge"] ?? ""));
    else res.status(403).send("Forbidden");
    return;
  }
  if (req.method !== "POST") { res.status(405).send("Method not allowed"); return; }
  const body = req.body as { entry?: unknown[] };
  const entries = (Array.isArray(body?.entry) ? body.entry : []) as Array<{ id?: unknown }>;
  const { work, unowned, badSignature } = routeEntries(configs, entries, req.rawBody, req.get("x-hub-signature-256"), "whatsapp", entryPhoneNumberId);
  for (const account of unowned) logger.warn("wa webhook: no brand owns this number", { account });
  if (!work.length && entries.length && badSignature.length) { res.status(403).send("Bad signature"); return; }
  try {
    for (const { config, entry } of work) {
      if (!config.whatsapp.enabled) continue;
      for (const event of parseWhatsAppEntry(entry, config.whatsapp.phoneNumberId)) await handleEvent(config, event);
    }
  } catch (e) {
    logger.error("wa webhook: processing failed", { message: (e as Error).message });
  }
  res.status(200).send("EVENT_RECEIVED");
});
