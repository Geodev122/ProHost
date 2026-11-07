#!/usr/bin/env bash
# Prints the id of the first available iPhone simulator (newest runtime first), creating nothing.
set -euo pipefail
xcrun simctl list devices available -j | python3 -c '
import json,sys
d=json.load(sys.stdin)["devices"]
best=None
for rt,devs in sorted(d.items(), reverse=True):
    if "iOS" not in rt: continue
    for x in devs:
        if x["name"].startswith("iPhone") and x.get("isAvailable",True):
            print(x["udid"]); sys.exit(0)
sys.exit("no iPhone simulator available")
'
