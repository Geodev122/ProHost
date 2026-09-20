import { onCall, HttpsError } from "firebase-functions/v2/https";
import { getFirestore } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { sendEmail, hostingerSmtpSecret } from "../lib/email";
import { idDocumentApprovedTemplate, idDocumentRejectedTemplate, UserContext } from "../lib/emailTemplates";
import "../lib/admin";

/**
 * Admin-only callable: approve or reject a pending ID document submission.
 *
 * On APPROVED: sets idDocumentVerificationStatus to APPROVED (kycLevel trigger
 *   will then recompute kycLevel to 3 automatically).
 * On REJECTED: clears idDocumentUrl + sets status REJECTED, so the user is
 *   prompted to re-upload a clearer photo.
 *
 * Both paths update the id_review_queue document and send an email to the user.
 */
export const reviewIdDocument = onCall(
  { secrets: [hostingerSmtpSecret] },
  async (request) => {
    if (request.auth?.token.role !== "ADMIN") {
      throw new HttpsError("permission-denied", "Admin access required.");
    }

    const { userId, decision, reason } = request.data as {
      userId?: string;
      decision?: "APPROVED" | "REJECTED";
      reason?: string;
    };

    if (!userId) throw new HttpsError("invalid-argument", "userId is required.");
    if (decision !== "APPROVED" && decision !== "REJECTED") {
      throw new HttpsError("invalid-argument", "decision must be APPROVED or REJECTED.");
    }

    const db = getFirestore();
    const now = Date.now();

    const profileSnap = await db.collection("user_profiles").doc(userId).get();
    const profileData = profileSnap.data();
    if (!profileData) throw new HttpsError("not-found", "User not found.");

    const profileUpdate: Record<string, unknown> = {
      idDocumentVerificationStatus: decision,
      idDocumentReviewedAt: now,
      updatedAt: now,
    };
    if (decision === "REJECTED") {
      // Clear the URL so the user can re-upload a new document
      profileUpdate.idDocumentUrl = null;
      profileUpdate.idDocumentSubmittedAt = null;
    }

    await db.collection("user_profiles").doc(userId).update(profileUpdate);

    await db.collection("id_review_queue").doc(userId).update({
      status: decision,
      reviewedAt: now,
      reviewerUid: request.auth!.uid,
      ...(reason ? { rejectionReason: reason } : {}),
    });

    logger.info("id_document_reviewed", { userId, decision, reviewer: request.auth!.uid });

    const userCtx: UserContext = {
      fullName: profileData.fullName ?? "Member",
      email: profileData.email ?? "",
      role: (profileData.role ?? "SPECIALIST") as UserContext["role"],
    };
    const tpl = decision === "APPROVED"
      ? idDocumentApprovedTemplate(userCtx)
      : idDocumentRejectedTemplate(userCtx, reason);
    if (userCtx.email) {
      await sendEmail({ to: userCtx.email, ...tpl });
    }

    return { ok: true, decision };
  }
);
