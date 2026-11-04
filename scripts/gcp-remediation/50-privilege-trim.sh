#!/usr/bin/env bash
# 50a (default): grant narrow predefined roles to default compute SA (additive).
# 50b (STEP=b): then remove roles/editor. Evidence: Recommender insights, 23 exercised permissions / 46d.
set -uo pipefail; . "$(dirname "$0")/lib.sh"
SA=646730915838-compute@developer.gserviceaccount.com; M="serviceAccount:$SA"
ROLES="roles/datastore.user roles/firebaseauth.admin roles/firebasecloudmessaging.admin roles/pubsub.publisher \
 roles/storage.objectUser roles/logging.logWriter roles/eventarc.eventReceiver roles/run.invoker roles/artifactregistry.writer \
 roles/aiplatform.user roles/cloudquotas.admin roles/secretmanager.secretAccessor roles/serviceusage.serviceUsageConsumer \
 roles/monitoring.metricWriter roles/cloudtrace.agent"
for r in $ROLES; do run projects add-iam-policy-binding $PROJECT --member="$M" --role="$r" --condition=None --quiet --format='value(etag)'; done
# delete-artifacts on the functions image repo only
run artifacts repositories add-iam-policy-binding gcf-artifacts --location=$REGION --member="$M" --role=roles/artifactregistry.repoAdmin --quiet --format='value(etag)'
run iam service-accounts add-iam-policy-binding $SA --member="$M" --role=roles/iam.serviceAccountTokenCreator --quiet --format='value(etag)'
[ "${STEP:-a}" = b ] && run projects remove-iam-policy-binding $PROJECT --member="$M" --role=roles/editor --condition=None --quiet --format='value(etag)'
