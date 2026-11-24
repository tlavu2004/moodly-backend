#!/usr/bin/env bash
set -euo pipefail

output_path="${1:-docs/api/json/moodly-openapi.json}"
response_path="$(mktemp)"

cleanup() {
  if [[ -n "${application_pid:-}" ]]; then
    kill "$application_pid" 2>/dev/null || true
    wait "$application_pid" 2>/dev/null || true
  fi
  rm -f "$response_path"
}
trap cleanup EXIT

# Java tooling reads the same non-secret port properties as the docs profile.
SERVER_PORT=$(bash scripts/docker/run-tooling.sh docs-port)
export SERVER_PORT

echo "Starting the documentation profile..."
./mvnw --batch-mode -Dspring-boot.run.profiles=docs spring-boot:run &
application_pid=$!

readonly max_openapi_attempts=45
for ((attempt=1; attempt<=max_openapi_attempts; attempt++)); do
  if curl --fail --silent --show-error "http://127.0.0.1:${SERVER_PORT}/v3/api-docs" >"$response_path" 2>/dev/null; then
    mkdir -p "$(dirname "$output_path")"
    bash scripts/docker/run-tooling.sh automation normalize-openapi "$response_path" "$output_path"
    exit 0
  fi
  if ! kill -0 "$application_pid" 2>/dev/null; then
    echo "The documentation profile stopped before the OpenAPI endpoint was ready." >&2
    exit 1
  fi
  sleep 1
done

echo "Timed out waiting for the documentation profile to start." >&2
exit 1
