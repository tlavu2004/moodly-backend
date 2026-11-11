.DEFAULT_GOAL := help

compose = docker compose -p moodly-$(1) --env-file .env.$(1) -f docker-compose.$(1).yml

.PHONY: help
define help_text
Backend
  make local-run / test-run               Run the backend against the selected stack
  make local-build-run / test-build-run   Build before running the backend
  make test-cdc-run                       Alias for test-run
Tests and OpenAPI
  make test                               Run Maven tests
  make verify                             Run tests, build, and check coverage
  make cdc-test                           Run CDC integration tests
  make openapi                            Generate the OpenAPI contract
Docker - replace STACK with local or test
  make STACK-up                           Start the stack and wait for readiness
  make STACK-start / STACK-await          Aliases for STACK-up
  make STACK-stop                         Stop containers
  make STACK-down                         Remove containers and the stack network
  make STACK-clean                        Delete stack data volumes (requires CONFIRM=yes)
  make STACK-status / STACK-logs          List containers or follow their logs
  make STACK-replica-status               Check MongoDB replica state
  make STACK-elasticsearch-status         Check Elasticsearch cluster health
  make validate-STACK                     Validate Compose without printing values
  make config-STACK                       Show resolved Compose configuration (may expose secrets)
  make                                    Show this help without starting containers
endef

help:
	$(info $(help_text))


# Backend and Maven entry points
.PHONY: local-run local-build-run test-run test-cdc-run test-build-run test verify cdc-test openapi

local-run test-run: %-run:
	bash scripts/docker/run-$*-backend.sh

local-build-run test-build-run: %-build-run:
	bash scripts/docker/run-$*-backend.sh build

test-cdc-run: test-run

test:
	./mvnw test

verify:
	./mvnw verify

cdc-test:
	./mvnw test -Dtest=CdcSearchInfrastructureIntegrationTest

openapi:
	bash src/main/resources/scripts/openapi/generate-openapi.sh

# Shared Compose commands for local and test
.PHONY: local-up local-stop local-down local-status local-replica-status local-elasticsearch-status local-logs local-clean
.PHONY: test-up test-stop test-down test-status test-replica-status test-elasticsearch-status test-logs test-clean
.PHONY: local-start local-await test-start test-await config-local config-test validate-local validate-test

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
	@if [ "$(CONFIRM)" != "yes" ]; then echo "Refusing to delete $* data volumes. Re-run with CONFIRM=yes." >&2; exit 2; fi
	$(call compose,$*) down -v --remove-orphans

config-local config-test: config-%:
	$(call compose,$*) config

validate-local validate-test: validate-%:
	$(call compose,$*) config --quiet
