#!/usr/bin/env sh
set -eu

ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
export DEVELOPER_DIR="${DEVELOPER_DIR:-/Applications/Xcode.app/Contents/Developer}"

xcodebuild \
  -project "$ROOT_DIR/macos/MyLifePalMac.xcodeproj" \
  -scheme MyLifePalMac \
  -configuration Debug \
  -sdk macosx26.1 \
  -derivedDataPath "$ROOT_DIR/build/macos" \
  CODE_SIGNING_ALLOWED=NO \
  build
