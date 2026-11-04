#!/usr/bin/env bash
# Additive, non-breaking: PITR, delete protection, daily backup, bucket hardening.
set -uo pipefail; . "$(dirname "$0")/lib.sh"
B=gs://prohost-f766f.firebasestorage.app
run firestore databases update --database='(default)' --enable-pitr --delete-protection
run firestore backups schedules create --database='(default)' --recurrence=daily --retention=7d
run firestore backups schedules create --database='(default)' --recurrence=weekly --day-of-week=SUN --retention=8w
run storage buckets update $B --public-access-prevention --versioning --soft-delete-duration=7d
