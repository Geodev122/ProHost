import { onCall, HttpsError } from "firebase-functions/v2/https";
import { getFirestore } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { sendEmail, hostingerSmtpSecret } from "../lib/email";
import { inAppInquiryTemplate, UserContext } from "../lib/emailTemplates";
import "../lib/admin";

const MAX_INQUIRIES_PER_DAY = 3;
const WINDOW_MS = 24 * 60 * 60 * 1000;

export const sendInquiryEmail = onCall(
  { secrets: [hostingerSmtpSecret] },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in to send inquiries.");
    }

    const uid = request.auth.uid;
    const { spaceId, message } = request.data as { spaceId?: string; message?: string };

    if (!spaceId || typeof spaceId !== "string") {
      throw new HttpsError("invalid-argument", "spaceId is required.");
    }
    if (!message || typeof message !== "string" || message.trim().length < 5) {
      throw new HttpsError("invalid-argument", "Message must be at least 5 characters.");
    }
    if (message.length > 2000) {
      throw new HttpsError("invalid-argument", "Message must be under 2000 characters.");
    }

    const db = getFirestore();
    const now = Date.now();

    // Rate limit: max 3 inquiries per 24h
    const rateLimitRef = db.collection("inquiry_rate_limits").doc(uid);
    const rateLimitSnap = await rateLimitRef.get();
    const rateData = rateLimitSnap.data();
    if (rateData && rateData.windowStart && now - (rateData.windowStart as number) < WINDOW_MS) {
      if ((rateData.count as number) >= MAX_INQUIRIES_PER_DAY) {
        throw new HttpsError(
          "resource-exhausted",
          `You can send up to ${MAX_INQUIRIES_PER_DAY} inquiries per day. Try again tomorrow.`
        );
      }
      await rateLimitRef.set({ count: (rateData.count as number) + 1 }, { merge: true });
    } else {
      await rateLimitRef.set({ count: 1, windowStart: now });
    }

    // Look up the listing
    const spaceSnap = await db.collection("workspace_listings").doc(spaceId).get();
    if (!spaceSnap.exists) {
      throw new HttpsError("not-found", "Listing not found.");
    }
    const spaceData = spaceSnap.data()!;
    const ownerId = spaceData.ownerId as string;
    if (!ownerId) {
      throw new HttpsError("internal", "Listing has no owner.");
    }

    // Prevent owners from inquiring about their own listing
    if (ownerId === uid) {
      throw new HttpsError("failed-precondition", "You cannot send an inquiry about your own listing.");
    }

    // Look up owner and sender in parallel
    const [ownerSnap, senderSnap] = await Promise.all([
      db.collection("user_profiles").doc(ownerId).get(),
      db.collection("user_profiles").doc(uid).get(),
    ]);

    const ownerData = ownerSnap.data();
    const senderData = senderSnap.data();

    if (!ownerData?.email) {
      throw new HttpsError("failed-precondition", "Owner does not have a contact email.");
    }

    const senderCtx: UserContext = {
      fullName: senderData?.fullName ?? "A ProHost member",
      email: senderData?.email ?? "",
      role: (senderData?.role ?? "SPECIALIST") as UserContext["role"],
    };

    const subject = `Inquiry about ${spaceData.title ?? "your listing"}`;
    const tpl = inAppInquiryTemplate(senderCtx, ownerData.fullName ?? "Host", subject, message.trim());
    await sendEmail({
      to: ownerData.email as string,
      replyTo: senderData?.email ?? undefined,
      ...tpl,
    });

    logger.info("inquiry_email_sent", { from: uid, to: ownerId, spaceId });
    return { ok: true };
  }
);
