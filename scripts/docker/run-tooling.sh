#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."

backend_mode=false
case "${1:-}" in local|manual|container|container-test) backend_mode=true ;; esac
interrupted=false
if [ "$backend_mode" = true ]; then
  mkdir -p target
  interrupt_result=$(mktemp target/moodly-interrupt.XXXXXX)
  printf '%s' not-started > "$interrupt_result"
  export MOODLY_TOOLING_INTERRUPT_RESULT="$interrupt_result"
  trap 'interrupted=true' INT
  trap 'rm -f "$interrupt_result"' EXIT
fi

backend_status() {
  local status=$1
  if [ "$backend_mode" = true ] && [ "$interrupted" = true ] && [ "$status" -eq 130 ]; then
    case "$(cat "$interrupt_result")" in clean|not-started) return 0 ;; esac
  fi
  return "$status"
}

# Reuse the project's Java/Jackson dependencies; no extra interpreter is needed.
# Send Maven diagnostics to stderr so modes such as docs-port have clean stdout.
bootstrap_status=0
./mvnw --batch-mode --quiet -DskipTests test-compile dependency:build-classpath -Dmdep.outputFile=target/development-classpath.txt >&2 || bootstrap_status=$?
if [ "$bootstrap_status" -ne 0 ]; then
  backend_status "$bootstrap_status"
  exit $?
fi
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
if [ "$backend_mode" = true ]; then
  status=0
  "$java_command" -cp "$classpath" com.tlavu.moodly.support.DevelopmentLauncher "$@" || status=$?
  backend_status "$status"
  exit $?
fi
exec "$java_command" -cp "$classpath" com.tlavu.moodly.support.DevelopmentLauncher "$@"
