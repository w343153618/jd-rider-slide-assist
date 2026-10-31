#!/bin/sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
sdk_dir=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-"$HOME/Library/Android/sdk"}}
android_jar="$sdk_dir/platforms/android-35/android.jar"
if [ ! -f "$android_jar" ]; then
  printf '%s\n' 'Android SDK Platform 35 is required (set ANDROID_HOME).' >&2
  exit 1
fi
classes_dir=$(mktemp -d "${TMPDIR:-/tmp}/slideassist-update-tests.XXXXXX")
trap 'rm -rf "$classes_dir"' EXIT HUP INT TERM
javac -cp "$android_jar" -d "$classes_dir" \
  "$project_dir/app/src/main/java/com/example/slideassist/UpdateManager.java" \
  "$project_dir/app/src/main/java/com/example/slideassist/UpdateApkProvider.java" \
  "$project_dir/tests/UpdatePolicyTest.java"
java -cp "$classes_dir:$android_jar" com.example.slideassist.UpdatePolicyTest
