import { createHmac, timingSafeEqual } from "node:crypto";

/** Meta signs the raw POST body with the app secret: header `X-Hub-Signature-256: sha256=<hex>`. */
export function sign(rawBody: Buffer | string, appSecret: string): string {
  return "sha256=" + createHmac("sha256", appSecret).update(rawBody).digest("hex");
}

export function verifySignature(rawBody: Buffer | string | undefined, header: string | undefined, appSecret: string | undefined): boolean {
  if (!rawBody || !header || !appSecret) return false;
  const expected = Buffer.from(sign(rawBody, appSecret));
  const got = Buffer.from(header.trim());
  return expected.length === got.length && timingSafeEqual(expected, got);
}
