#!/bin/sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
classes_dir=$(mktemp -d "${TMPDIR:-/tmp}/slideassist-bubble.XXXXXX")
trap 'rm -rf "$classes_dir"' EXIT HUP INT TERM
javac -d "$classes_dir" \
  "$project_dir/app/src/main/java/com/example/slideassist/BubblePlacement.java" \
  "$project_dir/app/src/main/java/com/example/slideassist/BubbleSettings.java" \
  "$project_dir/app/src/main/java/com/example/slideassist/BubbleTimer.java" \
  "$project_dir/tests/BubblePlacementTest.java" "$project_dir/tests/BubbleTimerTest.java"
java -cp "$classes_dir" com.example.slideassist.BubblePlacementTest
java -cp "$classes_dir" com.example.slideassist.BubbleTimerTest
