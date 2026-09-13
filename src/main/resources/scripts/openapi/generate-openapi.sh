#!/usr/bin/env bash
set -euo pipefail

output_path="${1:-docs/api/moodly-openapi.json}"
log_path="$(mktemp)"

cleanup() {
  if [[ -n "${application_pid:-}" ]]; then
    kill "$application_pid" 2>/dev/null || true
    wait "$application_pid" 2>/dev/null || true
  fi
  rm -f "$log_path"
}
trap cleanup EXIT

./mvnw --batch-mode -Dspring-boot.run.profiles=docs spring-boot:run >"$log_path" 2>&1 &
application_pid=$!

for _ in {1..60}; do
  if curl --fail --silent --show-error http://127.0.0.1:8081/actuator/health >/dev/null 2>&1; then
    mkdir -p "$(dirname "$output_path")"
    curl --fail --silent --show-error http://127.0.0.1:8081/v3/api-docs \
      | python3 -m json.tool >"$output_path"
    exit 0
  fi
  if ! kill -0 "$application_pid" 2>/dev/null; then
    cat "$log_path" >&2
    exit 1
  fi
  sleep 1
done

cat "$log_path" >&2
echo "Timed out waiting for the documentation profile to start." >&2
exit 1
