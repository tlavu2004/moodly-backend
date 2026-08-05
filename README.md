# Moodly

Moodly is a modular-monolith Habit & Mood Tracker built with Spring Boot, MongoDB, Elasticsearch CDC, and self-issued JWT authentication.

## Local setup

Requirements: Java 25, Maven, and Docker Desktop.

Before first use, copy `.env.local.example` to `.env.local` and `.env.test.example` to `.env.test` if the environment files are not already present.

1. Start MongoDB as a single-node replica set and Elasticsearch:

   ```bash
   make local-up
   ```

2. Verify MongoDB:

   ```bash
   make local-status
   make local-replica-status
   ```

   Elasticsearch is available at `http://localhost:9200`. Its data is kept in the
   `moodly-elasticsearch-local-data` Docker volume.

3. Run the application:

   ```bash
   make local-run
   ```

   On startup, Moodly creates `daily_entries_search` only when it does not already
   exist. The mapping is deliberately not mutated at startup, so mapping changes
   remain an explicit migration decision.

   Verify the cluster, index, and mapping after the application has started:

   ```bash
   make local-elasticsearch-status
   curl http://localhost:9200/_cat/indices/daily_entries_search?v
   curl http://localhost:9200/daily_entries_search/_mapping?pretty
   ```

   The Change Stream listener resumes from its MongoDB-stored checkpoint after a
   restart. To rebuild the derived index deliberately, set
   `MOODLY_CDC_MAINTENANCE_KEY` in `.env.local`, then call:

   ```bash
   curl -X POST http://localhost:8080/internal/cdc/reindex \
     -H "X-Maintenance-Key: your-local-secret"
   ```

   This temporary header guard will be replaced by authenticated admin access in
   Phase 3.

   Failed Elasticsearch deliveries are retried three times with exponential
   backoff. Exhausted events are retained in MongoDB's `cdc_dead_letters`
   collection and can be replayed with
   `POST /internal/cdc/dead-letters/{id}/replay` using the same maintenance
   header. CDC state and its failed-event count are available at
   `GET /actuator/health`.

   The CDC verification scenarios in `docs/testing/moodly.http` cover insert,
   update, reindex/duplicate delivery, dead-letter replay, and health. The
   delete scenario uses `mongosh` until an entry-delete API is introduced.

4. Open `docs/testing/moodly.http` with the VS Code REST Client extension and send requests with the temporary `X-User-Id` header. Phase 3 will replace this header with JWT authentication.

## Test

`mvn test` starts an isolated `mongo:8.3.7` replica set with Testcontainers; it does not use the local Docker Compose database. Docker Desktop must be running.

```bash
make test
```

For a manually inspectable test database, start the separate test Compose environment on port `27018`:

```bash
make test-up
```

See `docs/blueprint/moodly-blueprint.md` for the phase plan, MongoDB pipeline exercises, and branch/commit workflow.
