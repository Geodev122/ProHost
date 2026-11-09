import { setAppAdapter } from "../instagram/appAdapter";
import { readPlayCatalog } from "../billing/playCatalogLive";
import { sendEmail } from "../lib/email";
import { esc } from "../lib/emailTemplates";

/**
 * ProHost's implementation of the AI agent's AppAdapter (migration plan P9). The only place that connects the agent to ProHost's
 * billing and mail modules; index.ts imports it for its side effect (registration).
 */
setAppAdapter({
  readOffers: async () => ({ basePlans: (await readPlayCatalog()).basePlans }),
  sendAdminMail: async (mail) => { await sendEmail(mail); },
  escapeHtml: esc,
});
