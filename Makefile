.DEFAULT_GOAL := local-up

compose = docker compose -p moodly-$(1) --env-file .env.$(1) -f docker-compose.$(1).yml

# Backend and Maven entry points
.PHONY: local-run local-build-run test-run test-cdc-run test-build-run test verify cdc-test openapi

local-run test-run: %-run:
	bash scripts/docker/run-$*-backend.sh

local-build-run test-build-run: %-build-run:
	bash scripts/docker/run-$*-backend.sh build

test-cdc-run: test-run

test:
	mvn test

verify:
	mvn verify

cdc-test:
	mvn test -Dtest=CdcSearchInfrastructureIntegrationTest

openapi:
	bash src/main/resources/scripts/openapi/generate-openapi.sh

# Shared Compose commands for local and test
.PHONY: local-up local-stop local-down local-status local-replica-status local-elasticsearch-status local-logs local-clean
.PHONY: test-up test-stop test-down test-status test-replica-status test-elasticsearch-status test-logs test-clean
.PHONY: local-start local-await test-start test-await config-local config-test

local-start local-await: local-up
test-start test-await: test-up

local-up test-up: %-up:
	bash scripts/docker/start-infrastructure.sh $*

local-stop test-stop: %-stop:
	$(call compose,$*) stop

local-down test-down: %-down:
	$(call compose,$*) down

local-status test-status: %-status:
	$(call compose,$*) ps

local-replica-status test-replica-status: %-replica-status:
	$(call compose,$*) exec mongodb sh -c 'exec mongosh --quiet --port "$$MONGODB_CONTAINER_PORT" --eval "rs.status().members[0].stateStr"'

local-elasticsearch-status test-elasticsearch-status: %-elasticsearch-status:
	set -a; . ./.env.$*; set +a; curl -fsS "http://$${ELASTICSEARCH_HOST}:$${ELASTICSEARCH_PORT}/_cluster/health?pretty"

local-logs test-logs: %-logs:
	$(call compose,$*) logs -f mongodb elasticsearch

local-clean test-clean: %-clean:
	$(call compose,$*) down -v --remove-orphans

config-local config-test: config-%:
	$(call compose,$*) config
