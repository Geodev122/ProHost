import { onCall } from "./lib/callable";
import { setGlobalOptions } from "firebase-functions/v2";
import "./lib/admin";

setGlobalOptions({ region: "europe-west1", maxInstances: 10 });

/**
 * Trivial pipeline sanity check — kept around cheaply; useful for confirming
 * deploy/auth still works without touching anything stateful.
 */
export const ping = onCall(() => {
  return { ok: true, timestamp: Date.now() };
});

export { assignInitialRole } from "./roles/assignInitialRole";
export { grantAdminRole } from "./roles/grantAdminRole";
export { setAccountSuspended } from "./roles/setAccountSuspended";
export { revokeProHostRole } from "./roles/revokeProHostRole";
export { bootstrapSuperAdmin } from "./roles/bootstrapSuperAdmin";
export { deleteOwnAccount } from "./roles/deleteOwnAccount";

export { updatePricing } from "./admin/pricing";
export { grantPackageToUser } from "./admin/grantPackage";
export { lookupUserForGrant } from "./admin/lookupUserForGrant";
export { setListingVerification, setListingSubscriptionActive, requestListingVerification } from "./admin/listings";

export { recordClientAuditLog } from "./audit/recordClientAuditLog";

export { onBookingRequestCreated, onBookingRequestStatusChanged, onBookingPaymentAcknowledged } from "./notifications/bookingNotifications";
export { sendPaymentReminder } from "./notifications/sendPaymentReminder";

export { onWorkspaceListingCreated, onWorkspaceListingDeleted, onWorkspaceListingStatusChanged } from "./listings/listingCountTracker";
export { onWorkspaceListingPublishValidation } from "./listings/publishValidation";
export { onWorkspaceListingDeletedCleanup } from "./listings/listingDeleteCleanup";
export { listingShareLanding } from "./listings/shareLanding";
export { legalDocumentPage } from "./legal/legalDocumentPage";
export { onBookingAcceptConflictGuard } from "./bookings/bookingConflictGuard";
export { onUserFavoritesChanged } from "./users/favoritesSync";

export { expirePackages } from "./packages/expirePackages";

export { resendEmailVerification, verifyEmailLink } from "./auth/emailVerification";
export { sendEmailOtp, verifyEmailOtp, clickEmailOtpLink } from "./auth/emailOtp";
export { sendSignInEmailLink, sendVerificationEmailLink } from "./auth/emailLinkAuth";
export { playBillingRtdn } from "./billing/playBillingRtdn";
export { recomputeKycLevel } from "./users/kycLevel";
export { submitIdDocument } from "./users/submitIdDocument";
export { reviewIdDocument } from "./users/reviewIdDocument";
export { sendInquiryEmail } from "./users/sendInquiryEmail";
