.PHONY: local-up local-start local-stop local-down local-status local-replica-status local-elasticsearch-status local-await local-run local-build-run local-logs local-clean test cdc-test test-up test-start test-stop test-down test-status test-replica-status test-elasticsearch-status test-await test-run test-cdc-run test-build-run test-logs test-clean config-local config-test

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
	set -a; . ./.env.local; set +a; curl -fsS "http://$${ELASTICSEARCH_HOST}:$${ELASTICSEARCH_PORT}/_cluster/health?pretty"

local-await: local-up
	@set -a; . ./.env.local; set +a; \
	for attempt in $$(seq 1 30); do \
		if curl -fsS "http://$${ELASTICSEARCH_HOST}:$${ELASTICSEARCH_PORT}/_cluster/health?wait_for_status=yellow&timeout=1s" >/dev/null 2>&1 \
			&& $(LOCAL_COMPOSE) exec -T mongodb mongosh --quiet --eval "rs.status().myState" | grep -qx 1; then \
			echo "MongoDB replica set and Elasticsearch are ready."; exit 0; \
		fi; \
		sleep 2; \
	done; \
	echo "Timed out waiting for MongoDB replica set or Elasticsearch." >&2; exit 1

local-run: local-await
	set -a; . ./.env.local; set +a; SPRING_PROFILES_ACTIVE=local mvn spring-boot:run; status=$$?; if [ $$status -eq 130 ] || [ $$status -eq 143 ]; then exit 0; fi; exit $$status

local-build-run: local-await
	set -a; . ./.env.local; set +a; mvn clean install -DskipTests && (SPRING_PROFILES_ACTIVE=local mvn spring-boot:run; status=$$?; if [ $$status -eq 130 ] || [ $$status -eq 143 ]; then exit 0; fi; exit $$status)

local-logs:
	$(LOCAL_COMPOSE) logs -f mongodb elasticsearch

local-clean:
	$(LOCAL_COMPOSE) down -v --remove-orphans

test:
	mvn test

cdc-test:
	set -a; . ./.env.test; set +a; mvn test -Dtest=CdcSearchInfrastructureIntegrationTest

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

test-elasticsearch-status:
	set -a; . ./.env.test; set +a; curl -fsS "http://$${ELASTICSEARCH_HOST}:$${ELASTICSEARCH_PORT}/_cluster/health?pretty"

test-await: test-up
	@set -a; . ./.env.test; set +a; \
	for attempt in $$(seq 1 30); do \
		if curl -fsS "http://$${ELASTICSEARCH_HOST}:$${ELASTICSEARCH_PORT}/_cluster/health?wait_for_status=yellow&timeout=1s" >/dev/null 2>&1 \
			&& $(TEST_COMPOSE) exec -T mongodb mongosh --quiet --eval "rs.status().myState" | grep -qx 1; then \
			echo "MongoDB replica set and Elasticsearch are ready."; exit 0; \
		fi; \
		sleep 2; \
	done; \
	echo "Timed out waiting for MongoDB replica set or Elasticsearch." >&2; exit 1

test-run: test-await
	set -a; . ./.env.test; set +a; SPRING_PROFILES_ACTIVE=test mvn spring-boot:run; status=$$?; if [ $$status -eq 130 ] || [ $$status -eq 143 ]; then exit 0; fi; exit $$status

test-cdc-run: test-await
	set -a; . ./.env.test; set +a; SPRING_PROFILES_ACTIVE=test mvn spring-boot:run; status=$$?; if [ $$status -eq 130 ] || [ $$status -eq 143 ]; then exit 0; fi; exit $$status

test-build-run: test-await
	set -a; . ./.env.test; set +a; mvn clean install -DskipTests && (SPRING_PROFILES_ACTIVE=test mvn spring-boot:run; status=$$?; if [ $$status -eq 130 ] || [ $$status -eq 143 ]; then exit 0; fi; exit $$status)

test-logs:
	$(TEST_COMPOSE) logs -f mongodb elasticsearch

test-clean:
	$(TEST_COMPOSE) down -v --remove-orphans

config-local:
	$(LOCAL_COMPOSE) config

config-test:
	$(TEST_COMPOSE) config
