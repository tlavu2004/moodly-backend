.DEFAULT_GOAL := help

infra_env = $(if $(MOODLY_INFRA_ENV_FILE),$(MOODLY_INFRA_ENV_FILE),$(if $(wildcard .env.local),.env.local,.env.local.example))
compose = docker compose -p moodly-local --env-file "$(infra_env)" -f compose.local.yaml

.PHONY: help
define help_text
Backend
  make local-run / test-run               Run the local or isolated test backend on shared infrastructure
  make local-build-run / test-build-run   Build before running the backend
  make test-cdc-run                       Alias for test-run
  make test-search-run                    Manual test backend with CDC and search enabled
Tests and OpenAPI
  make test                               Run Maven tests
  make unit-test                          Run tests without Compose integration groups
  make integration-test                   Run Compose integration groups (disposable data)
  make verify                             Run tests, build, and check coverage
  make cdc-test                           Run CDC integration tests
  make openapi                            Generate the OpenAPI contract
Shared Docker Infrastructure (used by local and test)
  make local-up                           Start the stack and wait for readiness
  make local-start / local-await          Aliases for local-up
  make local-stop                         Stop containers
  make local-down                         Remove containers and the stack network
  make local-clean                        Delete stack data volumes (requires CONFIRM=yes)
  make local-status / local-logs          List containers or follow their logs
  make local-replica-status               Check MongoDB replica state
  make local-elasticsearch-status         Check Elasticsearch cluster health
  make validate-local                     Validate Compose without printing values
  make config-local                       Show resolved Compose configuration (may expose secrets)
  make                                    Show this help without starting containers
endef

help:
	$(info $(help_text))


# Backend and Maven entry points
.PHONY: local-run local-build-run test-run test-cdc-run test-search-run test-build-run test unit-test integration-test verify cdc-test openapi

local-run test-run: %-run:
	bash scripts/docker/run-$*-backend.sh

local-build-run test-build-run: %-build-run:
	bash scripts/docker/run-$*-backend.sh build

test-cdc-run: test-run

test-search-run:
	MOODLY_CDC_ENABLED=true MOODLY_SEARCH_INDEX_INITIALIZATION_ENABLED=true bash scripts/docker/run-test-backend.sh

test:
	./mvnw test

# Infrastructure groups follow *IntegrationTest; the application context smoke
# class also imports MongoTestConfiguration and therefore needs the test stack.
unit-test:
	./mvnw test '-Dtest=*Test,*Tests,!*IntegrationTest,!MoodlyApplicationTests'

integration-test:
	./mvnw test '-Dtest=*IntegrationTest,MoodlyApplicationTests'

verify:
	./mvnw verify

cdc-test:
	./mvnw test -Dtest=CdcSearchInfrastructureIntegrationTest

openapi:
	bash src/main/resources/scripts/openapi/generate-openapi.sh

# The operator owns the one shared infrastructure stack.
.PHONY: local-up local-start local-await local-stop local-down local-status local-replica-status local-elasticsearch-status local-logs local-clean config-local validate-local

local-start local-await: local-up

local-up:
	bash scripts/docker/start-infrastructure.sh local

local-stop:
	$(compose) stop

local-down:
	$(compose) down

local-status:
	$(compose) ps

local-replica-status:
	$(compose) exec mongodb sh -c 'exec mongosh --quiet --port "$$MONGODB_CONTAINER_PORT" --eval "rs.status().members[0].stateStr"'

local-elasticsearch-status:
	$(compose) exec elasticsearch curl -fsS 'http://localhost:9200/_cluster/health?pretty'

local-logs:
	$(compose) logs -f mongodb elasticsearch

local-clean:
	@if [ "$(CONFIRM)" != "yes" ]; then echo "Refusing to delete shared local/test volumes. Re-run with CONFIRM=yes." >&2; exit 2; fi
	$(compose) down -v --remove-orphans

config-local:
	$(compose) config

validate-local:
	$(compose) config --quiet
