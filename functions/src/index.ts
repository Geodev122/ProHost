import { setGlobalOptions } from "firebase-functions/v2";
import "./lib/admin";

setGlobalOptions({ region: "europe-west1", maxInstances: 10 });

export { assignInitialRole } from "./roles/assignInitialRole";
export { grantAdminRole } from "./roles/grantAdminRole";
export { setAccountSuspended } from "./roles/setAccountSuspended";
export { revokeProHostRole } from "./roles/revokeProHostRole";
export { bootstrapSuperAdmin } from "./roles/bootstrapSuperAdmin";
export { deleteOwnAccount } from "./roles/deleteOwnAccount";

export { updatePricing } from "./admin/pricing";
export { forceProHostUpgrade } from "./admin/forceProHostUpgrade";
export { lookupUserForGrant } from "./admin/lookupUserForGrant";
export { getAdminAnalytics, backfillProHostUpgradeDates } from "./admin/adminAnalytics";
export { setListingVerification, setListingSubscriptionActive, requestListingVerification } from "./admin/listings";
export { configureGa4ApiSecret } from "./admin/ga4Config";
export { backfillEmailVerified } from "./admin/backfillEmailVerified";
export { migrateLegacyListingFields } from "./admin/migrateLegacyFields";
export {
  adminCounts,
  adminSearch,
  adminUserDossier,
  adminExportUsers,
  backfillSearchNames,
  onUserProfileWrittenSearchName,
  onListingWrittenSearchName,
} from "./admin/adminDirectory";

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

export { verifyEmailLink } from "./auth/emailVerification";
export { sendEmailOtp, verifyEmailOtp, clickEmailOtpLink } from "./auth/emailOtp";
export { sendSignInEmailLink, sendVerificationEmailLink } from "./auth/emailLinkAuth";
export { playBillingRtdn } from "./billing/playBillingRtdn";
export { verifyAndRestorePurchase } from "./billing/verifyAndRestorePurchase";
export { retryPendingPlayActivations } from "./billing/retryPendingPlayActivations";
export { billingSyncJob, runBillingSync } from "./billing/billingSyncJob";
export {
  onUserProfileCreatedAssignCode,
  onBookingCreatedAssignCode,
  onListingWrittenAssignCodes,
  backfillDisplayCodes,
} from "./ids/displayCodes";
