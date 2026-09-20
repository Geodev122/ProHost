import { onCall, HttpsError } from "firebase-functions/v2/https";
import { getFirestore } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { sendEmail, hostingerSmtpSecret } from "../lib/email";
import { idDocumentSubmittedTemplate, UserContext } from "../lib/emailTemplates";
import "../lib/admin";

/**
 * Callable: authenticated user submits their ID document for admin review.
 *
 * Security:
 *  - URL must be a genuine firebasestorage.googleapis.com download URL.
 *  - URL must point to the caller's own storage path (uid-scoped).
 *  - idDocumentUrl is a PROTECTED field in firestore.rules — clients can't
 *    write it directly; this function uses the Admin SDK to bypass that rule.
 *  - Blocks re-submission while a review is already PENDING_REVIEW.
 *
 * Side-effects:
 *  - Writes idDocumentUrl + idDocumentVerificationStatus: PENDING_REVIEW to
 *    user_profiles/{uid} (Admin SDK).
 *  - Creates / overwrites id_review_queue/{uid} for the admin review UI.
 *  - Sends a confirmation email to the user.
 */
export const submitIdDocument = onCall(
  { secrets: [hostingerSmtpSecret] },
  async (request) => {
    if (!request.auth?.uid) {
      throw new HttpsError("unauthenticated", "You must be signed in.");
    }

    const { storageUrl } = request.data as { storageUrl?: string };

    if (!storageUrl || !/^https:\/\/firebasestorage\.googleapis\.com\//.test(storageUrl)) {
      throw new HttpsError("invalid-argument", "A valid Firebase Storage URL is required.");
    }

    const uid = request.auth.uid;
    const encodedUid = encodeURIComponent(uid);

    // The Storage path must belong to this user: users/{uid}/...
    // Firebase Storage encodes / as %2F in download URLs.
    if (
      !storageUrl.includes(`/users%2F${encodedUid}%2F`) &&
      !storageUrl.includes(`/users/${uid}/`)
    ) {
      throw new HttpsError("permission-denied", "You may only submit your own documents.");
    }

    const db = getFirestore();
    const profileSnap = await db.collection("user_profiles").doc(uid).get();
    const profileData = profileSnap.data();

    if (!profileData) {
      throw new HttpsError("not-found", "User profile not found.");
    }

    if (profileData.idDocumentVerificationStatus === "PENDING_REVIEW") {
      throw new HttpsError(
        "already-exists",
        "Your ID document is already under review. You'll receive an email when it's been processed."
      );
    }

    const now = Date.now();

    await db.collection("user_profiles").doc(uid).set(
      {
        idDocumentUrl: storageUrl,
        idDocumentVerificationStatus: "PENDING_REVIEW",
        idDocumentSubmittedAt: now,
        updatedAt: now,
      },
      { merge: true }
    );

    await db.collection("id_review_queue").doc(uid).set({
      userId: uid,
      fullName: profileData.fullName ?? "Unknown",
      email: profileData.email ?? "",
      phone: profileData.phone ?? "",
      role: profileData.role ?? "SPECIALIST",
      storageUrl,
      submittedAt: now,
      status: "PENDING_REVIEW",
    });

    // If this is a re-submission after a rejection, restore listings that were
    // hidden via isOwnerIdRejected. Only PRO_HOST users have listings.
    if (profileData.role === "PRO_HOST") {
      const rejectedListings = await db
        .collection("workspace_listings")
        .where("ownerId", "==", uid)
        .where("isOwnerIdRejected", "==", true)
        .get();
      if (!rejectedListings.empty) {
        const bw = db.bulkWriter();
        rejectedListings.docs.forEach((doc) =>
          bw.set(doc.ref, { isOwnerIdRejected: false, ownerIsIdVerified: true }, { merge: true })
        );
        await bw.close();
        logger.info("id_document_resubmit_listings_restored", { uid, count: rejectedListings.size });
      }
    }

    logger.info("id_document_submitted", { uid });

    const userCtx: UserContext = {
      fullName: profileData.fullName ?? "Member",
      email: profileData.email ?? "",
      role: (profileData.role ?? "SPECIALIST") as UserContext["role"],
    };
    const tpl = idDocumentSubmittedTemplate(userCtx);
    if (userCtx.email) {
      await sendEmail({ to: userCtx.email, ...tpl });
    }

    return { ok: true };
  }
);
