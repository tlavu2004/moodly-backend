#!/usr/bin/env bash
set -euo pipefail

mode="${1:-}"
case "$mode" in
  local|test) ;;
  *) echo "Usage: $0 local|test" >&2; exit 2 ;;
esac

cd "$(dirname "$0")/../.."
# Local ports must come from .env.local, even after exporting test settings.
if [ "$mode" = local ]; then
  for key in ${!MONGODB_@} ${!ELASTICSEARCH_@}; do
    unset "$key"
  done
fi

compose=(docker compose -p "moodly-$mode" --env-file ".env.$mode" -f "docker-compose.$mode.yml")

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
