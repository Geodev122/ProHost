import { sendEmail } from "../lib/email";
import { esc } from "../lib/emailTemplates";
import { IgConfig } from "./config";

export const IG_SUBJECT_TAG = "[ProHost Instagram]";

/** Admin alert through the mail queue; same tag/header convention as the website forms (docs/EMAIL.md). */
export async function alertAdmin(config: Pick<IgConfig, "adminEmail"> & { brand?: Pick<IgConfig["brand"], "id" | "name"> }, title: string, detail: string): Promise<void> {
  await sendEmail({
    to: config.adminEmail,
    subject: `${config.brand && config.brand.id !== "prohost" ? `[${config.brand.name} Instagram]` : IG_SUBJECT_TAG} ${title}`,
    html: `<p><strong>${esc(title)}</strong></p><p>${esc(detail)}</p><p>Open the IG Control Room artifact for the live log.</p>`,
    headers: { "X-ProHost-Form": "instagram", "X-ProHost-Tags": "instagram,automation" },
  });
}
