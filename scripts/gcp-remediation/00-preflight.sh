#!/usr/bin/env bash
# Read-only backup of state needed for rollback. No secret values are read.
set -uo pipefail; . "$(dirname "$0")/lib.sh"
umask 077; mkdir -p "$BACKUP_DIR"; cd "$BACKUP_DIR"
g projects get-iam-policy $PROJECT --format=json > project-iam-policy.json
g services list --enabled --format=json > enabled-services.json
g iam service-accounts list --format=json > service-accounts.json
for sa in $(g iam service-accounts list --format='value(email)'); do
  g iam service-accounts keys list --iam-account="$sa" --format=json > "sa-keys-$sa.json" 2>/dev/null
done
g run services list --platform=managed --region=$REGION --format=json > run-services.json 2>/dev/null
for s in $(g run services list --region=$REGION --format='value(metadata.name)' 2>/dev/null); do
  g run services get-iam-policy "$s" --region=$REGION --format=json > "run-iam-$s.json" 2>/dev/null
done
g services api-keys list --format=json > api-keys.json 2>/dev/null   # key metadata/restrictions only, no keyString
g secrets list --format=json > secrets-metadata.json
g logging sinks list --format=json > log-sinks.json
g logging buckets list --format=json > log-buckets.json
g alpha monitoring policies list --format=json > alert-policies.json 2>/dev/null
g firestore databases list --format=json > firestore-databases.json 2>/dev/null
echo "backup written to $BACKUP_DIR"; ls "$BACKUP_DIR" | wc -l
