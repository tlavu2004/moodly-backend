#!/usr/bin/env bash
set -euo pipefail

output_path="${1:-docs/api/moodly-openapi.json}"
response_path="$(mktemp)"

cleanup() {
  if [[ -n "${application_pid:-}" ]]; then
    kill "$application_pid" 2>/dev/null || true
    wait "$application_pid" 2>/dev/null || true
  fi
  rm -f "$response_path"
}
trap cleanup EXIT

echo "Starting the documentation profile..."
./mvnw --batch-mode -Dspring-boot.run.profiles=docs spring-boot:run &
application_pid=$!

for _ in {1..45}; do
  if curl --fail --silent --show-error http://127.0.0.1:8081/v3/api-docs >"$response_path" 2>/dev/null; then
    mkdir -p "$(dirname "$output_path")"
    python3 - "$response_path" "$output_path" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    document = json.load(source)

# SpringDoc can emit response codes in a different order between runs.
for path in document.get("paths", {}).values():
    for operation in path.values():
        if isinstance(operation, dict) and isinstance(operation.get("responses"), dict):
            operation["responses"] = dict(sorted(operation["responses"].items()))

with open(sys.argv[2], "w", encoding="utf-8") as output:
    json.dump(document, output, indent=2, ensure_ascii=False)
    output.write("\n")
PY
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
