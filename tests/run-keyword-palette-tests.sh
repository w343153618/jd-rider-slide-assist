#!/bin/sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
classes_dir=$(mktemp -d "${TMPDIR:-/tmp}/slideassist-keyword-palette.XXXXXX")
trap 'rm -rf "$classes_dir"' EXIT HUP INT TERM
javac -d "$classes_dir" \
  "$project_dir/app/src/main/java/com/example/slideassist/KeywordMatcher.java" \
  "$project_dir/app/src/main/java/com/example/slideassist/KeywordAlertPalette.java" \
  "$project_dir/tests/KeywordAlertPaletteTest.java"
java -cp "$classes_dir" com.example.slideassist.KeywordAlertPaletteTest
