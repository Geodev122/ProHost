#!/usr/bin/env bash
# Instant rollback of stage 50b: restore Editor on the default compute SA.
. "$(dirname "$0")/lib.sh"; run projects add-iam-policy-binding $PROJECT --member="serviceAccount:646730915838-compute@developer.gserviceaccount.com" --role=roles/editor --condition=None --quiet --format='value(etag)'
