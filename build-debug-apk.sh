#!/usr/bin/env sh
set -eu

ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
SDK_DIR="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
JAVA_DIR="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
BUILD_TOOLS="$SDK_DIR/build-tools/36.1.0"
ANDROID_JAR="$SDK_DIR/platforms/android-36/android.jar"
OUT_DIR="$ROOT_DIR/build/manual"

if [ ! -f "$OUT_DIR/debug.keystore" ]; then
  mkdir -p "$OUT_DIR"
  "$JAVA_DIR/bin/keytool" \
    -genkeypair \
    -keystore "$OUT_DIR/debug.keystore" \
    -storepass android \
    -keypass android \
    -alias androiddebugkey \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname CN=AndroidDebug,O=MyLifePal,C=US
fi

build_apk() {
  MODULE="$1"
  PACKAGE="$2"
  MIN_SDK="$3"
  APK_DIR="$ROOT_DIR/$MODULE/build/outputs/apk/debug"
  MODULE_OUT="$OUT_DIR/$MODULE"

  mkdir -p "$MODULE_OUT/compiled" "$MODULE_OUT/generated" "$MODULE_OUT/classes" "$MODULE_OUT/dex" "$APK_DIR"

  "$BUILD_TOOLS/aapt2" compile --dir "$ROOT_DIR/$MODULE/src/main/res" -o "$MODULE_OUT/compiled"
  sed "s/<manifest /<manifest package=\"$PACKAGE\" /" "$ROOT_DIR/$MODULE/src/main/AndroidManifest.xml" > "$MODULE_OUT/AndroidManifest.xml"
  "$BUILD_TOOLS/aapt2" link \
    --manifest "$MODULE_OUT/AndroidManifest.xml" \
    -I "$ANDROID_JAR" \
    --java "$MODULE_OUT/generated" \
    --custom-package "$PACKAGE" \
    --min-sdk-version "$MIN_SDK" \
    --target-sdk-version 36 \
    --version-code 1 \
    --version-name 0.1.0 \
    -o "$MODULE_OUT/resources.apk" \
    "$MODULE_OUT"/compiled/*.flat

  "$JAVA_DIR/bin/javac" \
    -source 8 \
    -target 8 \
    -classpath "$ANDROID_JAR" \
    -d "$MODULE_OUT/classes" \
    $(find "$ROOT_DIR/$MODULE/src/main/java" -name '*.java') \
    "$MODULE_OUT/generated/$(echo "$PACKAGE" | tr . /)/R.java"

  JAVA_HOME="$JAVA_DIR" "$BUILD_TOOLS/d8" \
    --min-api "$MIN_SDK" \
    --lib "$ANDROID_JAR" \
    --output "$MODULE_OUT/dex" \
    $(find "$MODULE_OUT/classes" -name '*.class')

  cp "$MODULE_OUT/resources.apk" "$MODULE_OUT/unsigned.apk"
  zip -q -j "$MODULE_OUT/unsigned.apk" "$MODULE_OUT/dex/classes.dex"
  "$BUILD_TOOLS/zipalign" -f 4 "$MODULE_OUT/unsigned.apk" "$MODULE_OUT/aligned.apk"

  JAVA_HOME="$JAVA_DIR" "$BUILD_TOOLS/apksigner" sign \
    --ks "$OUT_DIR/debug.keystore" \
    --ks-pass pass:android \
    --key-pass pass:android \
    --out "$APK_DIR/$MODULE-debug.apk" \
    "$MODULE_OUT/aligned.apk"

  JAVA_HOME="$JAVA_DIR" "$BUILD_TOOLS/apksigner" verify --verbose "$APK_DIR/$MODULE-debug.apk"
}

build_apk app com.mylifepal.app 23
build_apk wear com.mylifepal.watch 26
