#!/usr/bin/env bash
# usage: 41-migrate.sh <service...>   (DRY_RUN=1 supported). Switch services to their group SA via a new revision.
set -uo pipefail; . "$(dirname "$0")/lib.sh"
mkdir -p "$BACKUP_DIR"; for s in "$@"; do
  grp=$(awk -v s="$s" '$1==s{print $2}' "$(dirname "$0")/services.map"); [ -z "$grp" ] && { echo "skip $s (not mapped)"; continue; }
  old=$(g run services describe "$s" --region=$REGION --format='value(spec.template.spec.serviceAccountName)')
  echo "$s $old" >> "$BACKUP_DIR/migrated-from.txt"
  run run services update "$s" --region=$REGION --service-account="run-$grp@$PROJECT.iam.gserviceaccount.com" --quiet --format='value(status.url)'
done
