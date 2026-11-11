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

# Read only the non-secret port; Spring imports the remaining fixture properties.
# .env.docs files intentionally use unquoted Java-properties-compatible values.
SERVER_PORT=$(python3 - <<'PYDOCS'
from pathlib import Path
port = None
for path in (Path(".env.docs.example"), Path(".env.docs")):
    if not path.exists():
        continue
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        key, separator, value = line.partition("=")
        if separator and key.strip() == "SERVER_PORT":
            port = value.strip()
if port is None or not port.isdigit() or not 1 <= int(port) <= 65535:
    raise SystemExit("Set a valid SERVER_PORT in .env.docs.example or .env.docs")
print(port)
PYDOCS
)
export SERVER_PORT

echo "Starting the documentation profile..."
./mvnw --batch-mode -Dspring-boot.run.profiles=docs spring-boot:run &
application_pid=$!

readonly max_openapi_attempts=45
for ((attempt=1; attempt<=max_openapi_attempts; attempt++)); do
  if curl --fail --silent --show-error "http://127.0.0.1:${SERVER_PORT}/v3/api-docs" >"$response_path" 2>/dev/null; then
    mkdir -p "$(dirname "$output_path")"
    python3 - "$response_path" "$output_path" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    document = json.load(source)

# SpringDoc can emit response codes in a different order between runs.
document["tags"] = sorted(document.get("tags", []), key=lambda tag: tag["name"])
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
