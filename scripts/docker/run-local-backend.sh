#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."

for key in ${!MONGODB_@} ${!ELASTICSEARCH_@}; do
  unset "$key"
done
unset MOODLY_SEARCH_INDEX_NAME
infra_env="${MOODLY_INFRA_ENV_FILE:-.env.local}"
compose=(docker compose -p moodly-local --env-file "$infra_env" -f compose.local.yaml)
environment=$("${compose[@]}" config --environment)
while IFS= read -r assignment; do
  if [[ "$assignment" =~ ^(APP_|AUTH0_|CLOUDINARY_|MONGODB_|ELASTICSEARCH_|MOODLY_)[a-zA-Z0-9_]*= ]]; then
    export "$assignment"
  fi
done <<< "$environment"
unset environment assignment
export MOODLY_INFRA_ENV_FILE="$infra_env"
export SPRING_PROFILES_ACTIVE=local
export SPRING_MONGODB_URI="mongodb://${MONGODB_HOST}:${MONGODB_PORT}/${MONGODB_DATABASE}?replicaSet=${MONGODB_REPLICA_SET}"
export SPRING_ELASTICSEARCH_URIS="http://${ELASTICSEARCH_HOST}:${ELASTICSEARCH_PORT}"

# Refuse local/test namespace collisions before starting application writers.
python_bin=python3
if ! command -v "$python_bin" >/dev/null 2>&1; then python_bin=python; fi
"$python_bin" scripts/docker/shared-infrastructure.py config >/dev/null

bash scripts/docker/start-infrastructure.sh local
if [ "${1:-}" = build ]; then
  ./mvnw package -DskipTests
fi
status=0
./mvnw -Dspring-boot.run.jvmArguments=-Dspring.devtools.restart.enabled=false spring-boot:run || status=$?
if [ "$status" -eq 130 ] || [ "$status" -eq 143 ]; then status=0; fi
exit "$status"
