#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."

if [ "${1:-}" != --lease-held ]; then
  python_bin=python3
  if ! command -v "$python_bin" >/dev/null 2>&1; then python_bin=python; fi
  exec "$python_bin" scripts/docker/shared-infrastructure.py manual "$BASH" "$PWD/scripts/docker/run-test-backend.sh" --lease-held "$@"
fi
shift
# The launcher validates namespace ownership and holds the test lease until exit.
# Shared infrastructure belongs to the operator, not to this backend process.
bash scripts/docker/start-infrastructure.sh local
if [ "${1:-}" = build ]; then
  ./mvnw package -DskipTests
fi
status=0
./mvnw -Dspring-boot.run.jvmArguments=-Dspring.devtools.restart.enabled=false spring-boot:run || status=$?
if [ "$status" -eq 130 ] || [ "$status" -eq 143 ]; then status=0; fi
exit "$status"
