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
export { requestRoleUpgrade } from "./roles/requestRoleUpgrade";
export { grantAdminRole } from "./roles/grantAdminRole";
export { bootstrapSuperAdmin } from "./roles/bootstrapSuperAdmin";
