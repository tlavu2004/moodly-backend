#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."

if [ "${1:-}" != --environment-loaded ]; then
  python_bin=python3
  if ! command -v "$python_bin" >/dev/null 2>&1; then python_bin=python; fi
  exec "$python_bin" scripts/docker/shared-infrastructure.py local "$BASH" "$PWD/scripts/docker/run-local-backend.sh" --environment-loaded "$@"
fi
shift
# The launcher validates namespaces and passes every dotenv key without shell eval.
bash scripts/docker/start-infrastructure.sh local
if [ "${1:-}" = build ]; then
  ./mvnw package -DskipTests
fi
status=0
./mvnw -Dspring-boot.run.jvmArguments=-Dspring.devtools.restart.enabled=false spring-boot:run || status=$?
if [ "$status" -eq 130 ] || [ "$status" -eq 143 ]; then status=0; fi
exit "$status"
