#!/bin/sh

set -eu

cd "$SRCROOT/../../.."

if [ "$PLATFORM_NAME" = "iphonesimulator" ]; then
    engine_target="iosSimulatorArm64"
else
    engine_target="iosArm64"
fi

if [ "$CONFIGURATION" = "Release" ]; then
    engine_build_type="Release"
    engine_directory="releaseFramework"
else
    engine_build_type="Debug"
    engine_directory="debugFramework"
fi

if [ "${OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED:-NO}" = "YES" ]; then
    echo "Using existing Kotlin frameworks because OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED is YES"
else
    ./gradlew \
        ":graphite-engine:link${engine_build_type}Framework${engine_target}" \
        :sample:sharedUI:embedAndSignAppleFrameworkForXcode
fi

engine_framework="$SRCROOT/../../graphite-engine/build/bin/$engine_target/$engine_directory/GraphiteEngine.framework"
app_frameworks="$TARGET_BUILD_DIR/$WRAPPER_NAME/Frameworks"
embedded_framework="$app_frameworks/GraphiteEngine.framework"

if [ ! -d "$engine_framework" ]; then
    echo "Missing GraphiteEngine framework: $engine_framework" >&2
    exit 1
fi

mkdir -p "$app_frameworks"
rm -rf "$embedded_framework"
/usr/bin/ditto "$engine_framework" "$embedded_framework"

if [ "${CODE_SIGNING_ALLOWED:-NO}" = "YES" ]; then
    signing_identity="${EXPANDED_CODE_SIGN_IDENTITY:--}"
    /usr/bin/codesign --force --sign "$signing_identity" --timestamp=none "$embedded_framework"
fi
