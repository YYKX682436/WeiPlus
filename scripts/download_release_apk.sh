#!/bin/sh
set -e

OUT_DIR="dist"
TAG="${1:-}"
REPO="${HCAT_RELEASE_REPO:-}"
APK_NAME="${HCAT_APK_NAME:-Hchat-release-signed.apk}"

cd "$(dirname "$0")/.."
if [ -z "$REPO" ]; then
  REPO="$(gh repo view --json nameWithOwner --jq .nameWithOwner)"
fi
mkdir -p "$OUT_DIR"

if [ -z "$TAG" ]; then
  for candidate in $(gh release list --repo "$REPO" --limit 100 --json tagName --jq '.[].tagName'); do
    case "$candidate" in
      v*) ;;
      *) continue ;;
    esac
    assets="$(gh release view "$candidate" --repo "$REPO" --json assets --jq '.assets[].name')"
    if printf '%s\n' "$assets" | grep -Fxq "$APK_NAME"; then
      TAG="$candidate"
      break
    fi
  done
fi

if [ -z "$TAG" ]; then
  printf '未找到包含 %s 的发行版\n' "$APK_NAME" >&2
  exit 1
fi

gh release download "$TAG" \
  --repo "$REPO" \
  --pattern "$APK_NAME" \
  --dir "$OUT_DIR" \
  --clobber
printf '已下载: %s/%s\n来源: %s/%s\n' "$OUT_DIR" "$APK_NAME" "$REPO" "$TAG"
