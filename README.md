# Moodly

Moodly is a modular-monolith Habit & Mood Tracker built with Spring Boot, MongoDB, Elasticsearch CDC, and Auth0-validated JWT authentication.

Planned BE + FE work: [mood trend period navigation](docs/roadmap/mood-trend-periods.md).

Backend exception handling follows the [Moodly Exception Standard](docs/blueprint/exception-standardization-blueprint.md).

## Keep the OpenAPI document current

When changing controllers, request/response DTOs, OpenAPI annotations, or response type names, regenerate `docs/api/moodly-openapi.json`, review its diff, and commit it with the code change. Schema names can change even when endpoint paths do not. CI regenerates this file and fails if it differs from the committed version.

From the repository root, run `bash src/main/resources/scripts/openapi/generate-openapi.sh` on Linux. In Windows Git Bash, use the following command if the checked-out script has CRLF line endings:

```bash
sed 's/\r$//' src/main/resources/scripts/openapi/generate-openapi.sh | bash
git diff -- docs/api/moodly-openapi.json
```

## Local setup

Requirements: Java 25, Maven, and Docker Desktop.

### Auth0 startup network failures

JWT discovery and JWKS downloads require HTTPS access to your Auth0 tenant even when the local Docker services are healthy. The backend uses 10-second connect and read timeouts, with at most three attempts and a 500 ms delay for connection refusals/timeouts. Persistent failures still stop startup; issuer, audience, signature and expiry validation remain mandatory. HTTP errors and TLS certificate errors are not retried.

Optional Spring properties:
   - `moodly.auth0.http.connect-timeout`
   - `moodly.auth0.http.read-timeout`
   - `moodly.auth0.http.max-attempts` (1–5)
   - `moodly.auth0.http.retry-delay`.

Their environment equivalents are
   - `MOODLY_AUTH0_HTTP_CONNECT_TIMEOUT`
   - `MOODLY_AUTH0_HTTP_READ_TIMEOUT`
   - `MOODLY_AUTH0_HTTP_MAX_ATTEMPTS`
   - `MOODLY_AUTH0_HTTP_RETRY_DELAY`.

No new secret is required. If all attempts fail, check network/proxy/firewall access to Auth0 rather than repeatedly restarting Docker or disabling JWT security.

Before first use, copy `.env.local.example` to `.env.local` and `.env.test.example` to `.env.test` if the environment files are not already present. `.env.local` is ignored by Git; it is also where Auth0 and Cloudinary development credentials belong.

### Auth0 and Cloudinary local services

Before starting the Phase 3 backend, configure the hosted development services and fill the corresponding blank values in `.env.local`:

- In Auth0, create a Moodly API with a stable identifier and use it as `AUTH0_AUDIENCE`. Set `AUTH0_ISSUER_URI` to the tenant issuer URL, including `https://` and its trailing slash. Create a local SPA client, then add the local frontend URL (for example, `http://localhost:3000`) to its allowed callback, logout, and web-origin URLs.
- In Cloudinary, create the signed `moodly_local_signed` upload preset, accept only `jpg`, `jpeg`, `png`, and `webp`, store its Media Library assets in `moodly/local`, and disable overwrite. Set its values as `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`, and `CLOUDINARY_UPLOAD_PRESET`. Never copy `CLOUDINARY_API_SECRET` into frontend configuration.
- Set `CLOUDINARY_FOLDER` to `moodly/local`. The Phase 3 backend will append the resource type and owner, for example `users/{userId}/avatar/{uuid}`. Set `APP_CORS_ALLOWED_ORIGINS` to the exact comma-separated local frontend origins allowed to call the API. Do not add production URLs here yet.

`make local-run` exports these variables before launching the `local` profile. For production, activate `prod` (or `production`, which includes `prod`) explicitly and supply the settings in `application-prod.yaml` through the deployment environment. Production requires `MONGODB_URI` and `ELASTICSEARCH_URIS` rather than the local host/port variables, and disables Swagger and the E2E reset endpoint. CDC maintenance requires both an Auth0 JWT with `MOODLY_CDC_ADMIN_AUTHORITY` (default `SCOPE_moodly:admin`) and the existing `X-Maintenance-Key` header. Grant that authority only to operators; keep the maintenance key private.

1. Start MongoDB as a single-node replica set and Elasticsearch:

   ```bash
   make local-up
   ```

   Each local/test Compose stack contains only MongoDB and Elasticsearch. `make local-up`
   and `make test-up` initialize the single-node replica set inside the running MongoDB
   container, then wait for MongoDB to become PRIMARY and Elasticsearch to be healthy.
   The operation is safe to repeat; `local-start` and `test-start` use the same flow.
   No stopped initialization container is left in Docker Desktop. Run these Make targets
   from a Bash-capable environment (Git Bash on Windows), at the repository root.
   Alternatively run `bash scripts/docker/start-infrastructure.sh local` (or `test`).
   On a fresh volume, use this flow rather than Docker Desktop's Start button or bare
   `docker compose up`. Once initialized, the replica-set configuration persists in the
   MongoDB volume and Docker Desktop Stop/Start works normally. MongoDB's healthcheck
   reports healthy only when it is writable PRIMARY.

2. Verify MongoDB:

   ```bash
   make local-status
   make local-replica-status
   ```

   Elasticsearch is available at `http://localhost:9200`. Its data is kept in the `moodly-elasticsearch-local-data` Docker volume.

3. Run the application:

   ```bash
   make local-run
   ```

   When the backend exits or you press Ctrl+C, `local-run` and `local-build-run`
   stop the local stack and keep its containers and volumes. Use `make local-up`
   to start the services independently, then `make local-stop` when finished.

   On startup, Moodly creates `daily_entries_search` only when it does not already exist. The mapping is deliberately not mutated at startup, so mapping changes remain an explicit migration decision.

   Verify the cluster, index, and mapping after the application has started:

   ```bash
   make local-elasticsearch-status
   curl http://localhost:9200/_cat/indices/daily_entries_search?v
   curl http://localhost:9200/daily_entries_search/_mapping?pretty
   ```

   On its first start without a checkpoint, the Change Stream listener rebuilds the derived index from MongoDB before listening for changes. It resumes from its MongoDB-stored checkpoint after a restart. To rebuild the derived index deliberately, set `MOODLY_CDC_MAINTENANCE_KEY` in `.env.local`, then call:

   ```bash
   curl -X POST http://localhost:8080/internal/cdc/reindex \
     -H "X-Maintenance-Key: your-local-secret"
   ```

   Outside production, the maintenance header is the guard. Production also requires the operator JWT described above.

   Failed Elasticsearch deliveries are retried three times with exponential backoff. Exhausted events are retained in MongoDB's `cdc_dead_letters` collection and can be replayed with `POST /internal/cdc/dead-letters/{id}/replay` using the same maintenance header. CDC health is `DEGRADED` while dead letters remain; alert on this status and replay or rebuild the projection. A stopped listener or unavailable dead-letter count reports `DOWN`. In production, health details are hidden from public callers.

   `GET /entries/search?q=...` queries the derived Elasticsearch index. It is eventually consistent: a newly saved MongoDB entry can take a short time to appear in search results.

   Pending avatar deletions are retried when the asset is no longer referenced. A task for an avatar that remains active is checked again every five minutes and discarded after 24 hours without deleting the asset; a later replacement creates a new task.

   The CDC verification scenarios in `docs/testing/moodly.http` cover insert, update, reindex/duplicate delivery, dead-letter replay, and health. The delete scenario uses `mongosh` until an entry-delete API is introduced.

4. Open `docs/testing/moodly.http` with the VS Code REST Client extension, set `@accessToken` to an Auth0 access token for the Moodly API audience, then send authenticated requests. The API no longer accepts `X-User-Id`; ownership is derived only from the JWT `sub` claim.

5. After Auth0 authenticates the SPA, call `PUT /auth/profile` once to idempotently bootstrap the application-owned profile. Other protected endpoints resolve ownership from the verified JWT and never create users as a hidden side effect.

### Reset the local Playwright account

The E2E reset endpoint is disabled by default and is not registered with the `prod` or `production` profile. To enable it for local Playwright runs, set `MOODLY_E2E_RESET_ENABLED=true`, choose a long random `MOODLY_E2E_RESET_TOKEN`, and set `MOODLY_E2E_USER_ID` to the Auth0 `sub` of the dedicated synthetic E2E account in `.env.local`. The account subject is read only from server configuration; the request cannot select a user. Keep the reset token in `.env.local` and out of frontend source and Git.

Start the local backend, then call the endpoint twice to verify repeatability:

```powershell
$tokenLine = Get-Content .env.local | Where-Object { $_ -like 'MOODLY_E2E_RESET_TOKEN=*' } | Select-Object -First 1
$resetToken = $tokenLine.Substring('MOODLY_E2E_RESET_TOKEN='.Length).Trim().Trim('"').Trim("'")
$headers = @{ 'X-E2E-Reset-Token' = $resetToken }
1..2 | ForEach-Object {
  Invoke-RestMethod -Method Post -Uri http://localhost:8080/internal/e2e/reset -Headers $headers |
    ConvertTo-Json -Depth 5 -Compress
}
```

Both responses should report the same configured `userId`, `habits: 2`, `entries: 3`, and `searchIndexing: "verified"`. The operation replaces only that subject's Moodly habits and daily entries, then verifies all seeded entries in Elasticsearch before returning success. It does not delete the Auth0 account or its application profile. If the endpoint is disabled, it returns `404`; a missing or incorrect token returns `403`.

### Mutation replay contract

`POST /habits` creates a new UUID for every accepted request and does not accept an idempotency key. A client must not automatically replay it after a timeout or lost response; it should read the habit list before a user chooses to submit it again. `PATCH /habits/{habitId}` and the archive/restore POSTs require the latest habit `version`. A stale version returns HTTP 409 `CONFLICT`; read the current habit before deciding on another change.

`PUT /entries/today/mood` and `PATCH /entries/today` set the requested state. Repeating the same body leaves the same visible mood or habit-log state, but updates metadata and can emit another CDC event. Concurrent updates to different fields or habit logs are applied atomically; simultaneous updates to the same field follow last-writer-wins semantics. Avatar replacement uses a conditional profile update and retries competing replacements. Avatar upload signature/confirmation still has no general replay guarantee; each signature allocates a new public ID and confirmation consumes its pending upload.

### Habit streak query cost

`GET /habits/{habitId}/streak` reads the authenticated user's entries on or before today for each requested habit. The frontend currently calls it once per active habit, in parallel. This repeated history read is accepted for small habit lists; a future summary endpoint should calculate all streaks from one history read if staging latency or habit counts exceed the frontend README's temporary threshold.

## Test

Automated integration tests use the existing `moodly-test` Compose stack: exactly
one MongoDB replica-set container and one Elasticsearch container. Docker Desktop
(or Docker Engine with Compose v2 in CI) must be running. No Testcontainers or Ryuk
containers are created. JUnit itself runs on the Maven/IDE JVM, outside Docker.

```bash
make test       # run tests
make verify     # tests, packaging, coverage and formatting checks
make cdc-test   # CDC/search integration tests only
```

Direct `mvn test`, `mvn verify`, and IDE JUnit runs use the same Compose bootstrap
and session cleanup. Only integration tests needing infrastructure start the stack;
unit tests do not need Docker. `.env.test` supplies infrastructure settings when
present; `config/docker-test.env` provides non-secret defaults for fresh checkouts
and CI. Set `MOODLY_TEST_ENV_FILE` to select a different infrastructure env file.
Automated tests never load `.env.local` or call real Auth0/Cloudinary services.

The first integration context starts both Compose services and initializes the
replica set. Tests use the fixed database from `MONGODB_DATABASE` (its name must
contain `test`) and the fixed index from `MOODLY_SEARCH_INDEX_NAME` (default:
`daily_entries_search`). All data in these two resources is disposable, including
anything created during manual `make test-run` sessions.

A group is one integration test class. Before each group, the database and index
are dropped so stale data from an interrupted run cannot affect tests. Startup
recreates MongoDB collection indexes and, for search/CDC groups, the configured
Elasticsearch mapping. After each group, the CDC listener is stopped, the context
is closed and evicted, and the database/index are dropped again. Replay and resume
token scenarios keep their state within the group. Other databases/indexes are not
reset. No generated `moodly_it_*` database or index is needed.

At session completion the reset is repeated and the test stack is stopped; both
containers and their volumes remain available. JVM shutdown is a cleanup fallback;
`make test-stop` can stop the stack after a forced process kill or IDE termination.

Run one test process at a time: a file lock rejects concurrent test processes.
Do not run a manual backend on `moodly-test` during automated tests, since the test
session owns the stack lifecycle. `moodly-local` remains separately managed.
The test context cache remains limited to one context to bound JVM memory, while
both Compose containers stay running across context changes.

To run the backend against the manual test stack, use `make test-run` (or
`make test-build-run` to build first). These commands load development Auth0,
Cloudinary and CORS settings from `.env.local`, then override infrastructure
settings from `.env.test`. The `compose-test` profile includes `local`; the
JUnit-only `test` profile and its fake credentials are not used. Both environment
files must exist. Any Auth0/Cloudinary calls use real development services.
The backend runs in the terminal, and the complete test Compose stack is stopped
when the command exits, fails to start, or is interrupted with Ctrl+C.
`test-cdc-run` is an alias of this same flow; CDC follows `MOODLY_CDC_ENABLED`.
For CDC/search exercises, set `MOODLY_CDC_ENABLED=true` in `.env.test` or run
`MOODLY_CDC_ENABLED=true make test-run` in Git Bash. With CDC disabled, the
existing CDC health indicator reports DOWN even when MongoDB and Elasticsearch
are UP; the manual runner does not change this health contract.

`make test-up` intentionally leaves the manual stack running for inspection;
finish that session with `make test-stop`. It is not a prerequisite for automated tests.

See `docs/blueprint/moodly-blueprint.md` for the phase plan, MongoDB pipeline exercises, and branch/commit workflow.
