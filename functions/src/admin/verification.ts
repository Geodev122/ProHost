import { onCall, HttpsError } from "firebase-functions/v2/https";
import { getFirestore } from "firebase-admin/firestore";
import { recordAuditLog } from "../lib/auditLog";
import "../lib/admin";

type DocumentType =
  | "SYNDICATE_CARD"
  | "NATIONAL_ID"
  | "PRACTICE_LICENSE"
  | "COMMERCIAL_REGISTER"
  | "TITLE_DEED_OR_LEASE"
  | "TAX_REGISTRATION";

type VerificationStatus = "UNVERIFIED" | "PENDING_REVIEW" | "VERIFIED" | "ACTION_REQUIRED";

/**
 * Mirrors DocumentType.requiredFor in DataModels.kt.
 */
const REQUIRED_TYPES: Record<"PROFESSIONAL" | "SPACE_OWNER", DocumentType[]> = {
  PROFESSIONAL: ["SYNDICATE_CARD", "NATIONAL_ID", "PRACTICE_LICENSE", "TAX_REGISTRATION"],
  SPACE_OWNER: ["NATIONAL_ID", "COMMERCIAL_REGISTER", "TITLE_DEED_OR_LEASE", "TAX_REGISTRATION"],
};

/**
 * Recomputes a user's verificationStatus/verificationTier/trustScore from their
 * actual credential document states — the single source of truth used by both
 * a user's own submission and an admin's approve/reject decision, mirroring
 * ProSpaceRepository.recalculateUserVerification (Kotlin) so both sides of the
 * (now server-authoritative) flow agree.
 */
async function recalculateUserVerification(userId: string): Promise<void> {
  const db = getFirestore();
  const userSnap = await db.collection("user_profiles").doc(userId).get();
  if (!userSnap.exists) return;
  const user = userSnap.data()!;
  const role = user.role as "PROFESSIONAL" | "SPACE_OWNER" | "ADMIN";

  if (role === "ADMIN") {
    await userSnap.ref.set(
      { isVerified: true, verificationStatus: "VERIFIED", verificationTier: "TIER_3_COMMERCIAL_HOST", trustScore: 98 },
      { merge: true }
    );
    return;
  }

  const requiredTypes = REQUIRED_TYPES[role] ?? [];
  const docsSnap = await db.collection("user_credentials").where("userId", "==", userId).get();
  const docs = docsSnap.docs.map((d) => d.data());

  const verifiedCount = requiredTypes.filter((req) =>
    docs.some((d) => d.type === req && d.status === "VERIFIED")
  ).length;
  const hasRejected = docs.some((d) => d.status === "REJECTED");
  const hasPending = docs.some((d) => d.status === "PENDING_REVIEW");

  let newStatus: VerificationStatus;
  if (verifiedCount === requiredTypes.length && requiredTypes.length > 0) {
    newStatus = "VERIFIED";
  } else if (hasRejected) {
    newStatus = "ACTION_REQUIRED";
  } else if (hasPending || verifiedCount > 0) {
    newStatus = "PENDING_REVIEW";
  } else {
    newStatus = "UNVERIFIED";
  }

  const isFullyVerified = newStatus === "VERIFIED";
  const tier =
    role === "SPACE_OWNER"
      ? isFullyVerified
        ? "TIER_3_COMMERCIAL_HOST"
        : "TIER_1_BASIC"
      : isFullyVerified
        ? "TIER_2_PROFESSIONAL"
        : "TIER_1_BASIC";

  const trustScore =
    newStatus === "VERIFIED" ? 98 : newStatus === "PENDING_REVIEW" ? 75 : newStatus === "ACTION_REQUIRED" ? 45 : 30;

  await userSnap.ref.set(
    {
      isVerified: isFullyVerified,
      verificationStatus: newStatus,
      verificationTier: tier,
      trustScore,
      updatedAt: Date.now(),
    },
    { merge: true }
  );
}

interface SubmitVerificationData {
  // No fields needed — a user can only ever submit their own verification.
}

/**
 * Self-service: a user asks for their already-uploaded credential documents to
 * be reviewed. Firestore rules deny every client write to verificationStatus/
 * verificationTier/trustScore on user_profiles, so this replaces
 * ProSpaceRepository.submitUserVerification's direct write.
 */
export const submitVerificationForReview = onCall<SubmitVerificationData>(async (request) => {
  const auth = request.auth;
  if (!auth) {
    throw new HttpsError("unauthenticated", "Sign in required.");
  }
  await recalculateUserVerification(auth.uid);
  await recordAuditLog({
    actionType: "VERIFICATION_SUBMITTED",
    details: `User ${auth.token.email ?? auth.uid} submitted credential documents for compliance review`,
    actorEmail: auth.token.email ?? "system@prohost.app",
    severity: "INFO",
  });
  return { ok: true };
});

interface ReviewCredentialDocumentData {
  documentId?: string;
  decision?: "APPROVE" | "REJECT";
  reviewerNotes?: string;
  rejectionReason?: string;
}

/**
 * The only path that can move a credential document to VERIFIED/REJECTED, or
 * change a user's verification status/tier/trust score as a result. Replaces
 * ProSpaceRepository.adminApproveDocument/adminRejectDocument's direct writes.
 */
export const reviewCredentialDocument = onCall<ReviewCredentialDocumentData>(async (request) => {
  const auth = request.auth;
  if (!auth) {
    throw new HttpsError("unauthenticated", "Sign in required.");
  }
  if (auth.token.role !== "ADMIN") {
    throw new HttpsError("permission-denied", "Only an Admin can review credential documents.");
  }

  const { documentId, decision } = request.data ?? {};
  if (!documentId || (decision !== "APPROVE" && decision !== "REJECT")) {
    throw new HttpsError("invalid-argument", "documentId and decision (APPROVE|REJECT) are required.");
  }

  const db = getFirestore();
  const docRef = db.collection("user_credentials").doc(documentId);
  const docSnap = await docRef.get();
  if (!docSnap.exists) {
    throw new HttpsError("not-found", "Credential document not found.");
  }
  const doc = docSnap.data()!;

  if (decision === "APPROVE") {
    await docRef.set(
      {
        status: "VERIFIED",
        reviewerNotes: request.data.reviewerNotes ?? "Validated against Lebanese Syndicate Registry",
        rejectionReason: null,
        updatedAt: Date.now(),
      },
      { merge: true }
    );
    await recordAuditLog({
      actionType: "DOCUMENT_ACCREDITED",
      details: `Admin ${auth.token.email ?? auth.uid} approved ${doc.type} (#${doc.documentNumber}) for member ${doc.userId}`,
      actorEmail: auth.token.email ?? "system@prohost.app",
      severity: "SECURE",
    });
  } else {
    const reason = request.data.rejectionReason ?? "Document requires revision.";
    await docRef.set(
      {
        status: "REJECTED",
        rejectionReason: reason,
        reviewerNotes: `Revision requested: ${reason}`,
        updatedAt: Date.now(),
      },
      { merge: true }
    );
    await recordAuditLog({
      actionType: "DOCUMENT_REVISION_REQUESTED",
      details: `Admin ${auth.token.email ?? auth.uid} requested revision on ${doc.type} (#${doc.documentNumber}): ${reason}`,
      actorEmail: auth.token.email ?? "system@prohost.app",
      severity: "WARN",
    });
  }

  await recalculateUserVerification(doc.userId);
  return { ok: true };
});
