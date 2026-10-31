#!/bin/sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
classes_dir=$(mktemp -d "${TMPDIR:-/tmp}/slideassist-interaction.XXXXXX")
trap 'rm -rf "$classes_dir"' EXIT HUP INT TERM
javac -d "$classes_dir" \
  "$project_dir/app/src/main/java/com/example/slideassist/InteractionRecorder.java" \
  "$project_dir/app/src/main/java/com/example/slideassist/ReportStats.java" \
  "$project_dir/tests/InteractionRecorderTest.java"
java -cp "$classes_dir" com.example.slideassist.InteractionRecorderTest
