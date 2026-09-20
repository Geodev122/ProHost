import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { getFirestore } from "firebase-admin/firestore";
import "../lib/admin";

/**
 * KYC levels:
 *  0 — Registered (phone OTP verified, name, email collected)
 *  1 — Basic      (+ profile picture)
 *  2 — Verified   (+ email address verified via link)
 *  3 — ID Verified(+ ID document APPROVED by admin)
 *
 * Trigger: any write to user_profiles/{userId}. Recomputes and writes
 * kycLevel only when the derived value differs — avoids an infinite loop
 * while still converging correctly even if the document arrives with a
 * stale / missing kycLevel value.
 */
function computeKycLevel(data: Record<string, unknown>): number {
  if (data.idDocumentVerificationStatus === "APPROVED") return 3;
  if (data.emailVerified === true) return 2;
  if (data.profilePictureUrl) return 1;
  return 0;
}

export const recomputeKycLevel = onDocumentWritten(
  "user_profiles/{userId}",
  async (event) => {
    const after = event.data?.after;
    if (!after?.exists) return;

    const data = after.data() as Record<string, unknown>;
    const newLevel = computeKycLevel(data);

    if (data.kycLevel === newLevel) return; // already correct — don't re-trigger

    await getFirestore()
      .collection("user_profiles")
      .doc(event.params.userId)
      .update({ kycLevel: newLevel });
  }
);
