#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."

compose=(docker compose -p moodly-test --env-file .env.test -f compose.test.yaml)
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

# Reuse development Auth0/Cloudinary credentials, then override infrastructure
# settings with the isolated manual-test environment. Never load test fixtures.
# Let Compose parse dotenv syntax rather than executing values as shell code
# (for example, Auth0 subjects can contain a literal pipe).
environment=$(docker compose -p moodly-test --env-file .env.local --env-file .env.test \
  -f compose.test.yaml config --environment)
while IFS= read -r assignment; do
  if [[ "$assignment" =~ ^(APP_|AUTH0_|CLOUDINARY_|MONGODB_|ELASTICSEARCH_|MOODLY_)[a-zA-Z0-9_]*= ]]; then
    export "$assignment"
  fi
done <<< "$environment"
unset environment assignment
export SPRING_PROFILES_ACTIVE=compose-test
export SPRING_MONGODB_URI="mongodb://${MONGODB_HOST}:${MONGODB_PORT}/${MONGODB_DATABASE}?replicaSet=${MONGODB_REPLICA_SET}"
export SPRING_ELASTICSEARCH_URIS="http://${ELASTICSEARCH_HOST}:${ELASTICSEARCH_PORT}"

bash scripts/docker/start-infrastructure.sh test
if [ "${1:-}" = build ]; then
  ./mvnw package -DskipTests
fi
# Devtools restart can report success even when application startup fails.
status=0
./mvnw -Dspring-boot.run.jvmArguments=-Dspring.devtools.restart.enabled=false spring-boot:run || status=$?
if [ "$status" -eq 130 ] || [ "$status" -eq 143 ]; then status=0; fi
exit "$status"
