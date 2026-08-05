.PHONY: local-up local-start local-stop local-down local-status local-replica-status local-elasticsearch-status local-run local-build-run local-logs local-clean test test-up test-start test-stop test-down test-status test-replica-status test-run test-build-run test-logs test-clean config-local config-test

LOCAL_COMPOSE = docker compose -p moodly-local --env-file .env.local -f docker-compose.local.yml
TEST_COMPOSE = docker compose -p moodly-test --env-file .env.test -f docker-compose.test.yml

local-up:
	$(LOCAL_COMPOSE) up -d

local-start:
	$(LOCAL_COMPOSE) start

local-stop:
	$(LOCAL_COMPOSE) stop

local-down:
	$(LOCAL_COMPOSE) down

local-status:
	$(LOCAL_COMPOSE) ps

local-replica-status:
	$(LOCAL_COMPOSE) exec mongodb mongosh --quiet --eval "rs.status().members[0].stateStr"

local-elasticsearch-status:
	$(LOCAL_COMPOSE) exec elasticsearch curl -fsS "http://localhost:9200/_cluster/health?pretty"

local-run: local-up
	set -a; . ./.env.local; set +a; SPRING_PROFILES_ACTIVE=local mvn spring-boot:run; status=$$?; if [ $$status -eq 130 ] || [ $$status -eq 143 ]; then exit 0; fi; exit $$status

local-build-run: local-up
	set -a; . ./.env.local; set +a; mvn clean install -DskipTests && (SPRING_PROFILES_ACTIVE=local mvn spring-boot:run; status=$$?; if [ $$status -eq 130 ] || [ $$status -eq 143 ]; then exit 0; fi; exit $$status)

local-logs:
	$(LOCAL_COMPOSE) logs -f mongodb elasticsearch

local-clean:
	$(LOCAL_COMPOSE) down -v --remove-orphans

test:
	mvn test

test-up:
	$(TEST_COMPOSE) up -d

test-start:
	$(TEST_COMPOSE) start

test-stop:
	$(TEST_COMPOSE) stop

test-down:
	$(TEST_COMPOSE) down

test-status:
	$(TEST_COMPOSE) ps

test-replica-status:
	$(TEST_COMPOSE) exec mongodb mongosh --quiet --eval "rs.status().members[0].stateStr"

test-run: test-up
	SPRING_PROFILES_ACTIVE=test mvn spring-boot:run; status=$$?; if [ $$status -eq 130 ] || [ $$status -eq 143 ]; then exit 0; fi; exit $$status

test-build-run: test-up
	mvn clean install -DskipTests && (SPRING_PROFILES_ACTIVE=test mvn spring-boot:run; status=$$?; if [ $$status -eq 130 ] || [ $$status -eq 143 ]; then exit 0; fi; exit $$status)

test-logs:
	$(TEST_COMPOSE) logs -f mongodb

test-clean:
	$(TEST_COMPOSE) down -v --remove-orphans

config-local:
	$(LOCAL_COMPOSE) config

config-test:
	$(TEST_COMPOSE) config
