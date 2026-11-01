#!/bin/sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
classes_dir=$(mktemp -d "${TMPDIR:-/tmp}/slideassist-tests.XXXXXX")
trap 'rm -rf "$classes_dir"' EXIT HUP INT TERM
javac -d "$classes_dir" \
  "$project_dir/app/src/main/java/com/example/slideassist/VolumeKeyChord.java" \
  "$project_dir/app/src/main/java/com/example/slideassist/SwipeTiming.java" \
  "$project_dir/tests/VolumeKeyChordTest.java" "$project_dir/tests/SwipeTimingTest.java"
java -cp "$classes_dir" com.example.slideassist.VolumeKeyChordTest
java -cp "$classes_dir" com.example.slideassist.SwipeTimingTest
sh "$project_dir/tests/run-slider-geometry-tests.sh"
sh "$project_dir/tests/run-interaction-tests.sh"
sh "$project_dir/tests/run-triple-tap-tests.sh"
sh "$project_dir/tests/run-reaction-tests.sh"
sh "$project_dir/tests/run-bubble-placement-tests.sh"
sh "$project_dir/tests/run-keyword-tests.sh"
sh "$project_dir/tests/run-keyword-palette-tests.sh"
