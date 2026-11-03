#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."

compose=(docker compose -p moodly-test --env-file .env.test -f docker-compose.test.yml)
cleanup() {
  status=$?
  trap - EXIT
  if ! "${compose[@]}" stop; then
    echo "Failed to stop moodly-test." >&2
    if [ "$status" -eq 0 ]; then status=1; fi
  fi
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

# Automated tests own isolated Testcontainers, not the manual Compose stack.
"${compose[@]}" stop
mvn "$@"
