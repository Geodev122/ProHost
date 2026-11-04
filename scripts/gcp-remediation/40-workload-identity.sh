#!/usr/bin/env bash
# 40a: create per-group runtime SAs with narrow roles. Does NOT touch services.
set -uo pipefail; . "$(dirname "$0")/lib.sh"
BASE="roles/logging.logWriter roles/monitoring.metricWriter roles/cloudtrace.agent roles/datastore.user roles/storage.objectUser roles/serviceusage.serviceUsageConsumer"
declare -A EXTRA=(
 [admin]="roles/firebaseauth.admin roles/secretmanager.secretAccessor roles/firebasecloudmessaging.admin"
 [auth]="roles/firebaseauth.admin roles/secretmanager.secretAccessor"
 [billing]="roles/firebaseauth.admin roles/secretmanager.secretAccessor roles/firebasecloudmessaging.admin"
 [ig]="roles/secretmanager.secretAccessor roles/firebasecloudmessaging.admin"
 [public]=""
 [triggers]="roles/firebaseauth.admin roles/firebasecloudmessaging.admin roles/cloudtasks.enqueuer roles/pubsub.publisher roles/secretmanager.secretAccessor")
for grp in admin auth billing ig public triggers; do
  sa="run-$grp@$PROJECT.iam.gserviceaccount.com"
  g iam service-accounts describe "$sa" >/dev/null 2>&1 || run iam service-accounts create "run-$grp" --display-name="Cloud Run runtime: $grp"
  for r in $BASE ${EXTRA[$grp]}; do
    run projects add-iam-policy-binding $PROJECT --member="serviceAccount:$sa" --role="$r" --condition=None --quiet --format='value(etag)'
  done
  # allow signing URLs / custom tokens as itself
  run iam service-accounts add-iam-policy-binding "$sa" --member="serviceAccount:$sa" --role=roles/iam.serviceAccountTokenCreator --quiet --format='value(etag)'
done
