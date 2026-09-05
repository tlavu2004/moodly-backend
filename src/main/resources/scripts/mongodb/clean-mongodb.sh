#!/bin/bash

set -euo pipefail

# Define color code
if [[ -t 1 ]]; then
  RED='\033[0;31m'
  YELLOW='\033[1;33m'
  GREEN='\033[0;32m'
  NC='\033[0m'
else
  # shellcheck disable=SC2034
  RED=''
  # shellcheck disable=SC2034
  YELLOW=''
  # shellcheck disable=SC2034
  GREEN=''
  # shellcheck disable=SC2034
  NC=''
fi

# Get the directory where script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Go up 5 levels from src/main/resources/scripts/mongodb to reach project root
SERVICE_DIR="$(cd "$SCRIPT_DIR/../../../../.." && pwd)"

ENV_FILENAME=${1:-".env.local"}
ENV_FILE="$SERVICE_DIR/$ENV_FILENAME"

# Check for the selected environment file in service directory
if [[ ! -f "$ENV_FILE" ]]; then
  echo -e "${RED}ERROR: Environment file not found at $ENV_FILE${NC}" >&2
  exit 1
fi

# Load variables from the selected environment file (robustly without requiring quotes)
echo "Loading environment from $ENV_FILE..."

while IFS='=' read -r key value || [[ -n "${key-}" ]]; do
  # Skip comments and empty lines
  if [[ $key =~ ^#.* ]] || [[ -z $key ]]; then
    continue
  fi

  # Clean potential carriage returns
  key=${key//$'\r'/}
  value=${value//$'\r'/}

  if [[ -n "$key" ]]; then
    export "$key"="$value"
  fi
done < "$ENV_FILE"

# Validate required variables for the selected Moodly environment
required_vars=(
  MONGODB_HOST
  MONGODB_PORT
  MONGODB_DATABASE
  MONGODB_REPLICA_SET
)

for var in "${required_vars[@]}"; do
  if [[ -z "${!var:-}" ]]; then
    echo -e "${RED}ERROR: Required variable '$var' is missing in $ENV_FILENAME${NC}" >&2
    exit 1
  fi
done

MONGODB_URI="mongodb://$MONGODB_HOST:$MONGODB_PORT/$MONGODB_DATABASE?replicaSet=$MONGODB_REPLICA_SET"

# Prefer a locally installed shell, but use the MongoDB container when the host
# does not have mongosh (the normal setup for this Docker-based project).
MONGOSH_COMMAND=()
if command -v mongosh >/dev/null 2>&1; then
  MONGOSH_COMMAND=(mongosh "$MONGODB_URI")
elif command -v docker >/dev/null 2>&1; then
  case "$ENV_FILENAME" in
    .env.local)
      COMPOSE_FILE="docker-compose.local.yml"
      COMPOSE_PROJECT="moodly-local"
      ;;
    .env.test)
      COMPOSE_FILE="docker-compose.test.yml"
      COMPOSE_PROJECT="moodly-test"
      ;;
    *)
      echo -e "${RED}ERROR: mongosh is not installed, and no Docker Compose configuration is known for $ENV_FILENAME. Install mongosh or use .env.local/.env.test.${NC}" >&2
      exit 1
      ;;
  esac

  MONGODB_CONTAINER_PORT="${MONGODB_CONTAINER_PORT:-$MONGODB_PORT}"
  CONTAINER_MONGODB_URI="mongodb://localhost:$MONGODB_CONTAINER_PORT/$MONGODB_DATABASE?directConnection=true"
  MONGOSH_COMMAND=(docker compose -p "$COMPOSE_PROJECT" --env-file "$ENV_FILE" -f "$SERVICE_DIR/$COMPOSE_FILE" exec -T mongodb mongosh "$CONTAINER_MONGODB_URI")
else
  echo -e "${RED}ERROR: mongosh is not installed or not in PATH, and Docker is unavailable.${NC}" >&2
  exit 1
fi

# Confirm destructive action
echo -e "${YELLOW}--------------------------------------------------------"
echo "WARNING: This will DROP the MongoDB database and all data!"
echo "Target URI: $MONGODB_URI"
echo "Env File:   $ENV_FILENAME"
echo -e "--------------------------------------------------------${NC}"

# Reminder: Press Enter to cancel
read -r -p "Are you sure? Type 'yes' to continue (or press Enter to cancel): " confirm

# Force user type correctly the word 'yes', any other values of empty value (Enter) will cancel
if [[ "$confirm" != "yes" ]]; then
  echo -e "${YELLOW}Aborted. No changes were made.${NC}"
  exit 0
fi

# Change to service directory
cd "$SERVICE_DIR" || {
  echo -e "${RED}ERROR: Cannot cd to $SERVICE_DIR${NC}" >&2
  exit 1
}

# Drop the selected MongoDB database explicitly
"${MONGOSH_COMMAND[@]}" --quiet --eval '
  const result = db.dropDatabase();
  if (!result.ok) {
    throw new Error(`MongoDB cleanup failed: ${JSON.stringify(result)}`);
  }
  print(JSON.stringify(result));
'

echo -e "${GREEN}MongoDB database cleaned successfully!${NC}"
