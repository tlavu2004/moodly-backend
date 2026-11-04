.DEFAULT_GOAL := local-up

compose = docker compose -p moodly-$(1) --env-file .env.$(1) -f docker-compose.$(1).yml

.PHONY: help
help:
	@echo Backend
	@echo   make local-run / test-run               Run the backend against the selected stack
	@echo   make local-build-run / test-build-run   Build before running the backend
	@echo   make test-cdc-run                       Alias for test-run
	@echo Tests and OpenAPI
	@echo   make test                               Run Maven tests
	@echo   make verify                             Run tests, build, and check coverage
	@echo   make cdc-test                           Run CDC integration tests
	@echo   make openapi                            Generate the OpenAPI contract
	@echo Docker - replace STACK with local or test
	@echo   make STACK-up                           Start the stack and wait for readiness
	@echo   make STACK-start / STACK-await          Aliases for STACK-up
	@echo   make STACK-stop                         Stop containers
	@echo   make STACK-down                         Remove containers and the stack network
	@echo   make STACK-clean                        Remove the stack and delete its data volumes
	@echo   make STACK-status / STACK-logs          List containers or follow their logs
	@echo   make STACK-replica-status               Check MongoDB replica state
	@echo   make STACK-elasticsearch-status         Check Elasticsearch cluster health
	@echo   make config-STACK                       Show resolved Compose configuration
	@echo   make                                    Defaults to local-up


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
