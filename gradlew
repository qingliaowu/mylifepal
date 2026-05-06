#!/usr/bin/env sh
set -eu

PROJECT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
GRADLE_BIN="${GRADLE_BIN:-$HOME/.gradle/wrapper/dists/gradle-9.0.0-bin/d6wjpkvcgsg3oed0qlfss3wgl/gradle-9.0.0/bin/gradle}"
ANDROID_STUDIO_JAVA="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

if [ -d "$ANDROID_STUDIO_JAVA" ]; then
  export JAVA_HOME="${JAVA_HOME:-$ANDROID_STUDIO_JAVA}"
fi

exec "$GRADLE_BIN" --project-dir "$PROJECT_DIR" "$@"
