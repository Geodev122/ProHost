#!/usr/bin/env bash
# usage: 99-rollback.sh <service...|all>  -> restore previous runtime SA recorded in migrated-from.txt
set -uo pipefail; . "$(dirname "$0")/lib.sh"
F=$(ls -d "$(dirname "$0")"/backup-*/migrated-from.txt | tail -1)
while read -r s old; do
  [ "$1" = all ] || [[ " $* " == *" $s "* ]] || continue
  run run services update "$s" --region=$REGION --service-account="$old" --quiet --format='value(status.url)'
done < "$F"
