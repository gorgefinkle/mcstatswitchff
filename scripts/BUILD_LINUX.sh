#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
: "${JAVA_HOME:?Set JAVA_HOME to a Java 25 JDK directory}"
"$JAVA_HOME/bin/java" -version
if [[ -n "${GRADLE_HOME:-}" && -x "$GRADLE_HOME/bin/gradle" ]]; then
  exec "$GRADLE_HOME/bin/gradle" --no-daemon clean build -PtargetMinecraft=26.2
fi
exec ./gradlew --no-daemon clean build -PtargetMinecraft=26.2
