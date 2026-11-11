#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
if [ "${1:-}" != local ]; then echo "Usage: $0 local" >&2; exit 2; fi
exec bash scripts/docker/run-tooling.sh up
