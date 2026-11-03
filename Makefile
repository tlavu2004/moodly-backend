.PHONY: local-up local-start local-stop local-down local-status local-replica-status local-elasticsearch-status local-await local-run local-build-run local-logs local-clean test verify cdc-test test-up test-start test-stop test-down test-status test-replica-status test-elasticsearch-status test-await test-run test-cdc-run test-build-run test-logs test-clean config-local config-test openapi

LOCAL_COMPOSE = docker compose -p moodly-local --env-file .env.local -f docker-compose.local.yml
TEST_COMPOSE = docker compose -p moodly-test --env-file .env.test -f docker-compose.test.yml

local-up:
	bash scripts/docker/start-infrastructure.sh local

local-start:
	bash scripts/docker/start-infrastructure.sh local

local-stop:
	$(LOCAL_COMPOSE) stop

local-down:
	$(LOCAL_COMPOSE) down

local-status:
	$(LOCAL_COMPOSE) ps

local-replica-status:
	$(LOCAL_COMPOSE) exec mongodb mongosh --quiet --eval "rs.status().members[0].stateStr"

local-elasticsearch-status:
	set -a; . ./.env.local; set +a; curl -fsS "http://$${ELASTICSEARCH_HOST}:$${ELASTICSEARCH_PORT}/_cluster/health?pretty"

local-await: local-up

local-run:
	bash scripts/docker/run-local-backend.sh

local-build-run:
	bash scripts/docker/run-local-backend.sh build

local-logs:
	$(LOCAL_COMPOSE) logs -f mongodb elasticsearch

local-clean:
	$(LOCAL_COMPOSE) down -v --remove-orphans

test:
	mvn test

verify:
	mvn verify

openapi:
	bash src/main/resources/scripts/openapi/generate-openapi.sh

cdc-test:
	mvn test -Dtest=CdcSearchInfrastructureIntegrationTest

test-up:
	bash scripts/docker/start-infrastructure.sh test

test-start:
	bash scripts/docker/start-infrastructure.sh test

test-stop:
	$(TEST_COMPOSE) stop

test-down:
	$(TEST_COMPOSE) down

test-status:
	$(TEST_COMPOSE) ps

test-replica-status:
	$(TEST_COMPOSE) exec mongodb mongosh --quiet --port $${MONGODB_CONTAINER_PORT} --eval "rs.status().members[0].stateStr"

test-elasticsearch-status:
	set -a; . ./.env.test; set +a; curl -fsS "http://$${ELASTICSEARCH_HOST}:$${ELASTICSEARCH_PORT}/_cluster/health?pretty"

test-await: test-up

test-run:
	bash scripts/docker/run-test-backend.sh

test-cdc-run:
	bash scripts/docker/run-test-backend.sh

test-build-run:
	bash scripts/docker/run-test-backend.sh build

test-logs:
	$(TEST_COMPOSE) logs -f mongodb elasticsearch

test-clean:
	$(TEST_COMPOSE) down -v --remove-orphans

config-local:
	$(LOCAL_COMPOSE) config

config-test:
	$(TEST_COMPOSE) config
