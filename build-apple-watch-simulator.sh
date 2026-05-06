#!/usr/bin/env sh
set -eu

ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
export DEVELOPER_DIR="${DEVELOPER_DIR:-/Applications/Xcode.app/Contents/Developer}"

xcodebuild \
  -project "$ROOT_DIR/apple-watch/MyLifePalWatch.xcodeproj" \
  -scheme MyLifePalWatch \
  -configuration Debug \
  -sdk watchsimulator26.1 \
  -derivedDataPath "$ROOT_DIR/build/xcode" \
  CODE_SIGNING_ALLOWED=NO \
  build
