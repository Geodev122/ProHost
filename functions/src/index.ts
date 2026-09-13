import { onCall } from "firebase-functions/v2/https";
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

export { initiateWhishPayment } from "./payments/initiateWhishPayment";
export { whishWebhook } from "./payments/whishWebhook";
export { checkWhishStatus } from "./payments/checkWhishStatus";

export { updatePricing } from "./admin/pricing";
export { setListingVerification, setListingSubscriptionActive, requestListingVerification } from "./admin/listings";

export { recordClientAuditLog } from "./audit/recordClientAuditLog";

export { onBookingRequestCreated, onBookingRequestStatusChanged } from "./notifications/bookingNotifications";
export { sendPaymentReminder } from "./notifications/sendPaymentReminder";

export { onWorkspaceListingCreated, onWorkspaceListingDeleted, onWorkspaceListingStatusChanged } from "./listings/listingCountTracker";
export { onWorkspaceListingPublishValidation } from "./listings/publishValidation";
export { onWorkspaceListingDeletedCleanup } from "./listings/listingDeleteCleanup";
export { onBookingAcceptConflictGuard } from "./bookings/bookingConflictGuard";
export { onUserFavoritesChanged } from "./users/favoritesSync";

export { expirePackages } from "./packages/expirePackages";
export { migratePaygUsers } from "./packages/migratePaygUsers";
export { seedLegacyPackagePlans } from "./packages/seedLegacyPackagePlans";
