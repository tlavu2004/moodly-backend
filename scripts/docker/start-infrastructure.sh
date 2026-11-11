#!/usr/bin/env bash
set -euo pipefail

mode="${1:-}"
case "$mode" in
  local) ;;
  *) echo "Usage: $0 local" >&2; exit 2 ;;
esac

cd "$(dirname "$0")/../.."
# Infrastructure coordinates are independent of caller test namespace settings.
for key in ${!MONGODB_@} ${!ELASTICSEARCH_@}; do
  unset "$key"
done
unset MOODLY_SEARCH_INDEX_NAME
infra_env="${MOODLY_INFRA_ENV_FILE:-.env.local}"
if [ ! -f "$infra_env" ] && [ -z "${MOODLY_INFRA_ENV_FILE:-}" ]; then
  infra_env=.env.local.example
fi
compose=(docker compose -p moodly-local --env-file "$infra_env" -f compose.local.yaml)

# PRIMARY health requires initialization first, so do not use --wait here.
"${compose[@]}" up -d mongodb elasticsearch
ready=false
readonly max_mongo_ping_attempts=60
for ((attempt=1; attempt<=max_mongo_ping_attempts; attempt++)); do
  if "${compose[@]}" exec -T mongodb sh -c \
    'mongosh --quiet --host 127.0.0.1 --port "$MONGODB_CONTAINER_PORT" --eval "quit(db.adminCommand({ping: 1}).ok ? 0 : 1)"' >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 2
done
if [ "$ready" != true ]; then
  echo "Timed out waiting for MongoDB to accept connections ($mode)." >&2
  exit 1
fi

"${compose[@]}" exec -T mongodb sh -c \
  'exec mongosh --quiet --host 127.0.0.1 --port "$MONGODB_CONTAINER_PORT" --file /dev/stdin' \
  < scripts/docker/init-replica-set.js

"${compose[@]}" up -d --wait --wait-timeout 120 mongodb elasticsearch
echo "MongoDB replica set and Elasticsearch are ready ($mode)."
