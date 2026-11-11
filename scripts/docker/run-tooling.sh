#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."

# Reuse the project's Java/Jackson dependencies; no extra interpreter is needed.
# Send Maven diagnostics to stderr so modes such as docs-port have clean stdout.
./mvnw --batch-mode --quiet -DskipTests test-compile dependency:build-classpath -Dmdep.outputFile=target/development-classpath.txt >&2
dependencies=$(tr -d '\r\n' < target/development-classpath.txt)
case "${OSTYPE:-}" in
  msys*|cygwin*) classpath="target/test-classes;target/classes;$dependencies" ;;
  *) classpath="target/test-classes:target/classes:$dependencies" ;;
esac
java_command=java
if [ -n "${JAVA_HOME:-}" ]; then
  tooling_java_home="$JAVA_HOME"
  if command -v cygpath >/dev/null 2>&1; then tooling_java_home=$(cygpath -u "$tooling_java_home"); fi
  java_command="$tooling_java_home/bin/java"
fi
exec "$java_command" -cp "$classpath" com.tlavu.moodly.support.DevelopmentLauncher "$@"
