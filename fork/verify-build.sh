#!/bin/sh
# Builds a fork APK and checks what matters for users upgrading: package id, versionCode,
# and that the LFS-backed swipe model is real content.
#
#   fork/verify-build.sh [<gradle variant>]     (default: RemotewhisperStableRelease)
set -eu
. "$(dirname "$0")/lib.sh"
cd "$FORK_ROOT"

variant=${1:-RemotewhisperStableRelease}
expected_package=io.github.v3djg6gl.futo.keyboard.remotewhisper

# Without git-lfs the huggingface submodule checks out pointer files; the build succeeds
# but swipe typing is broken at runtime.
if grep -rlI 'version https://git-lfs.github.com/spec' java/assets/futo-swipe 2>/dev/null | grep -q .; then
    die "java/assets/futo-swipe contains git-lfs pointer files; install git-lfs and run 'git submodule update --init --force java/assets/futo-swipe'"
fi

# CI passes the version in the environment; locally derive it the same way Gradle does.
if [ -z "${VERSION_CODE:-}" ]; then
    eval "$(sh fork/version.sh)"
fi

./gradlew --console=plain "assemble$variant"

# RemotewhisperStableRelease -> build/outputs/apk/remotewhisperStable/release/
build_type=$(echo "$variant" | sed -E 's/.*(Release|Debug)$/\1/' | tr 'A-Z' 'a-z')
flavors=$(echo "$variant" | sed -E 's/(Release|Debug)$//')
flavors="$(echo "$flavors" | cut -c1 | tr 'A-Z' 'a-z')$(echo "$flavors" | cut -c2-)"
apk=$(ls -t build/outputs/apk/"$flavors"/"$build_type"/*.apk 2>/dev/null | head -n 1)
[ -n "$apk" ] || die "no APK in build/outputs/apk/$flavors/$build_type"

build_tools=$(ls -d "${ANDROID_HOME:-$HOME/Android/Sdk}"/build-tools/* | sort -V | tail -n 1)
badging=$("$build_tools/aapt2" dump badging "$apk")
package=$(echo "$badging" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")
code=$(echo "$badging" | sed -n "s/^package:.* versionCode='\([0-9]*\)'.*/\1/p")
name=$(echo "$badging" | sed -n "s/^package:.* versionName='\([^']*\)'.*/\1/p")

echo "APK:         $apk"
echo "package:     $package"
echo "versionName: $name"
echo "versionCode: $code (expected $VERSION_CODE)"
echo "permissions:"
echo "$badging" | sed -n "s/^uses-permission: name='\([^']*\)'.*/  \1/p"

case "$package" in
    "$expected_package"|"$expected_package".*) ;;
    *) die "unexpected package id $package" ;;
esac
[ "$code" = "$VERSION_CODE" ] || die "versionCode $code does not match $VERSION_CODE"
echo "OK"
