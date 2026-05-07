#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
EXTENSION_DIR="$ROOT_DIR/chrome-extension"
OUTPUT_DIR="$ROOT_DIR/build/chrome"
OUTPUT_FILE="$OUTPUT_DIR/mylifepal-chrome-extension.zip"

mkdir -p "$OUTPUT_DIR"
rm -f "$OUTPUT_FILE"

cd "$EXTENSION_DIR"
zip -qr "$OUTPUT_FILE" . -x "*.DS_Store"

echo "Chrome extension package: $OUTPUT_FILE"
