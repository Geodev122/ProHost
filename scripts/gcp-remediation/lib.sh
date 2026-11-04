#!/usr/bin/env bash
# Shared helpers. DRY_RUN=1 prints mutating commands instead of running them.
export PROJECT=prohost-f766f REGION=europe-west1
export BACKUP_DIR=${BACKUP_DIR:-/home/user/remediation/backup-$(date +%F)}
g() { env -u CLOUDSDK_AUTH_ACCESS_TOKEN gcloud --project="$PROJECT" "$@"; }
run() { # mutating command wrapper
  if [ "${DRY_RUN:-0}" = 1 ]; then echo "[dry-run] gcloud $*"; else echo "[run] gcloud $*"; g "$@"; fi
}
