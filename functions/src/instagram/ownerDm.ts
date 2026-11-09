import { getFirestore } from "firebase-admin/firestore";
import { canSend, loadConfig } from "./config";
import { sendStep } from "./graph";

/** The owner's personal Instagram account; DMs reach it only inside Meta's 24 h window. */
export const OWNER_INSTAGRAM = "geo_elnajjat";
const DAY_MS = 24 * 60 * 60 * 1000;

/** Best effort: DM the owner's Instagram account, only inside the 24 h window Meta allows. */
export async function dmOwner(text: string, now: number): Promise<string> {
  const config = await loadConfig();
  if (!canSend(config)) return "instagram not live";
  const q = await getFirestore().collection("ig_contacts").where("username", "==", OWNER_INSTAGRAM).limit(1).get();
  const owner = q.docs[0];
  if (!owner) return "owner never messaged the account";
  if (now - (Number(owner.get("lastSeenAt")) || 0) > DAY_MS) return "outside the 24 h window";
  await sendStep({ igUserId: config.igUserId, token: config.accessToken }, { type: "dm", recipientId: owner.id, text });
  return "sent";
}
