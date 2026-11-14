#!/usr/bin/env bash
# Downloads the photos the design-screenshot mock listings use (ui/preview/AppScreensPreview.kt) into app/build/design-photos,
# so the render JVM (which runs at a frozen date, where fresh TLS certificates are not yet valid) reads them from disk.
set -euo pipefail
cd "$(dirname "$0")/../.."
out=app/build/design-photos
mkdir -p "$out"
grep -o 'https://images\.unsplash\.com/[A-Za-z0-9_-]*' app/src/main/java/com/example/ui/preview/AppScreensPreview.kt | sort -u | while read -r url; do
  file="$out/${url##*/}.jpg"
  [ -s "$file" ] || curl -fsSL --retry 4 --retry-delay 2 -A "okhttp/4.12.0" -o "$file" "$url"
  echo "photo ${url##*/}: $(stat -c %s "$file") bytes"
done
