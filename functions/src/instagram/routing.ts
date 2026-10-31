import { IgConfig } from "./config";
import { verifySignature } from "./signature";

/** Meta's handshake carries no account id: the verify token tells which brand is being connected. */
export function brandForVerifyToken(configs: IgConfig[], token: string, platform: "instagram" | "whatsapp" = "instagram"): IgConfig | undefined {
  if (!token) return undefined;
  return configs.find((c) => (platform === "instagram" ? c.verifyToken : c.whatsapp.verifyToken) === token);
}

export interface Routed<E> { config: IgConfig; entry: E }

/**
 * One webhook, many accounts. Each entry belongs to the brand that owns the account id it carries (Instagram user id, or
 * WhatsApp phone number id); the body must have been signed with THAT brand's app secret. Entries nobody owns, or whose
 * signature does not fit their brand, are dropped.
 */
export function routeEntries<E extends { id?: unknown }>(
  configs: IgConfig[], entries: E[], rawBody: Buffer | string | undefined, signature: string | undefined,
  platform: "instagram" | "whatsapp" = "instagram", accountOf: (e: E) => string = (e) => String(e?.id ?? ""),
): { work: Array<Routed<E>>; unowned: string[]; badSignature: string[] } {
  const verified = new Map<string, boolean>();
  const work: Array<Routed<E>> = [];
  const unowned: string[] = [];
  const badSignature: string[] = [];
  for (const entry of entries) {
    const account = accountOf(entry);
    const config = configs.find((c) => !!account && (platform === "instagram" ? c.igUserId : c.whatsapp.phoneNumberId) === account);
    if (!config) { unowned.push(account); continue; }
    if (!verified.has(config.brandId)) verified.set(config.brandId, verifySignature(rawBody, signature, platform === "instagram" ? config.appSecret : config.whatsapp.appSecret));
    if (verified.get(config.brandId)) work.push({ config, entry }); else badSignature.push(config.brandId);
  }
  return { work, unowned, badSignature };
}
