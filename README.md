# Moodly

Moodly is a modular-monolith Habit & Mood Tracker built with Spring Boot, MongoDB, Elasticsearch CDC, and self-issued JWT authentication.

## Local setup

Requirements: Java 25, Maven, and Docker Desktop.

Before first use, copy `.env.local.example` to `.env.local` and `.env.test.example` to `.env.test` if the environment files are not already present.

1. Start MongoDB as a single-node replica set:

   ```bash
   make local-up
   ```

2. Verify MongoDB:

   ```bash
   make local-status
   make local-replica-status
   ```

3. Run the application:

   ```bash
   make local-run
   ```

4. Open `requests/moodly.http` in an HTTP client and send requests with the temporary `X-User-Id` header. Phase 3 will replace this header with JWT authentication.

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
