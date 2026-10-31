import { IgConfig } from "./config";
import { sendStep } from "./graph";
import { Channel, Step } from "./types";
import { sendWhatsApp } from "./whatsapp";

/** The function that delivers a step on the given channel with this brand's credentials. */
export function senderFor(config: IgConfig, channel: Channel): (step: Step) => Promise<string> {
  if (channel === "whatsapp") return (step) => sendWhatsApp({ phoneNumberId: config.whatsapp.phoneNumberId, token: config.whatsapp.accessToken }, step);
  return (step) => sendStep({ igUserId: config.igUserId, token: config.accessToken }, step);
}
