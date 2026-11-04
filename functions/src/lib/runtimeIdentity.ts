/**
 * Runtime identity per function group (docs/GCP_IAM.md, scripts/gcp-remediation/services.map).
 *
 * Every function used to run as the project's default compute service account. Each group below has its own
 * least-privilege account (`run-<group>@…`, created by scripts/gcp-remediation/40-workload-identity.sh).
 * In firebase-functions v2 the `serviceAccount` option is also the identity Eventarc and Cloud Scheduler use to
 * call the function, so every group account holds run.invoker + eventarc.eventReceiver.
 *
 * Usage: `onRequest({ ...runAs("public"), memory: "256MiB" }, handler)`.
 * A new function needs a group on purpose: give it the narrowest one that has the roles it needs, and add a
 * role to that group's account (never re-grant Editor to the default account).
 */
export type RuntimeGroup = "admin" | "auth" | "billing" | "ig" | "public" | "triggers";

const PROJECT_ID = "prohost-f766f";

export function runtimeServiceAccount(group: RuntimeGroup): string {
  return `run-${group}@${PROJECT_ID}.iam.gserviceaccount.com`;
}

/** Spread into a v2 function's options. */
export function runAs(group: RuntimeGroup): { serviceAccount: string } {
  return { serviceAccount: runtimeServiceAccount(group) };
}
