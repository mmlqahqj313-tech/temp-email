#!/bin/sh
set -eu
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
echo "Gradle is not installed in this environment." >&2
echo "For this project, use the GitHub Actions workflow to build the APK, or install Gradle 9.6.0 locally." >&2
exit 127
