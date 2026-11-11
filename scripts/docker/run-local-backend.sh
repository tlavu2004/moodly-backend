#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."

for key in ${!MONGODB_@} ${!ELASTICSEARCH_@}; do
  unset "$key"
done
compose=(docker compose -p moodly-local --env-file .env.local -f compose.local.yaml)
environment=$("${compose[@]}" config --environment)
while IFS= read -r assignment; do
  if [[ "$assignment" =~ ^(APP_|AUTH0_|CLOUDINARY_|MONGODB_|ELASTICSEARCH_|MOODLY_)[a-zA-Z0-9_]*= ]]; then
    export "$assignment"
  fi
done <<< "$environment"
unset environment assignment
export SPRING_PROFILES_ACTIVE=local
export SPRING_MONGODB_URI="mongodb://${MONGODB_HOST}:${MONGODB_PORT}/${MONGODB_DATABASE}?replicaSet=${MONGODB_REPLICA_SET}"
export SPRING_ELASTICSEARCH_URIS="http://${ELASTICSEARCH_HOST}:${ELASTICSEARCH_PORT}"

cleanup() {
  local status=$?
  trap - EXIT
  trap '' INT TERM
  if ! "${compose[@]}" stop; then
    echo "Failed to stop moodly-local." >&2
    if [ "$status" -eq 0 ]; then status=1; fi
  fi
  exit "$status"
}
trap cleanup EXIT
trap 'exit 0' INT TERM

bash scripts/docker/start-infrastructure.sh local
if [ "${1:-}" = build ]; then
  ./mvnw package -DskipTests
fi
status=0
./mvnw -Dspring-boot.run.jvmArguments=-Dspring.devtools.restart.enabled=false spring-boot:run || status=$?
if [ "$status" -eq 130 ] || [ "$status" -eq 143 ]; then status=0; fi
exit "$status"
