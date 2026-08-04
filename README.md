# Moodly

Moodly is a modular-monolith Habit & Mood Tracker built with Spring Boot, MongoDB, Elasticsearch CDC, and self-issued JWT authentication.

## Local setup

Requirements: Java 25, Maven, and Docker Desktop.

1. Start MongoDB as a single-node replica set:

   ```bash
   docker compose up -d
   ```

2. Verify MongoDB:

   ```bash
   docker compose ps
   docker compose exec mongodb mongosh --quiet --eval "rs.status().members[0].stateStr"
   ```

3. Run the application:

   ```bash
   mvn spring-boot:run
   ```

4. Open `requests/moodly.http` in an HTTP client and send requests with the temporary `X-User-Id` header. Phase 3 will replace this header with JWT authentication.

## Test

```bash
mvn test
```

See `docs/blueprint/moodly-blueprint.md` for the phase plan, MongoDB pipeline exercises, and branch/commit workflow.
