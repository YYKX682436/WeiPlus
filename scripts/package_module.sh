#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
variant="${1:-}"
case "$variant" in
  normal) mykey=false ;;
  sales) mykey=true ;;
  *) echo "用法: $0 normal|sales" >&2; exit 2 ;;
esac

cd "$root"
./gradlew :app:assembleRelease -PwithMyKey="$mykey" --no-watch-fs -x lintVitalAnalyzeRelease
version="$(sed -n 's/^HCAT_VERSION_NAME=//p' gradle.properties | tail -n 1)"
if [[ "$mykey" == true ]]; then
  apk="dist/Hchat-${version}-sales-release-signed.apk"
else
  apk="dist/Hchat-${version}-release-signed.apk"
fi
test -s "$apk"
echo "已打包: $root/$apk"
