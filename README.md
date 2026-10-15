# Moodly

Moodly is a modular-monolith Habit & Mood Tracker built with Spring Boot, MongoDB, Elasticsearch CDC, and Auth0-validated JWT authentication.

## Local setup

Requirements: Java 25, Maven, and Docker Desktop.

### Auth0 startup network failures

JWT discovery and JWKS downloads require HTTPS access to your Auth0 tenant even
when the local Docker services are healthy. The backend uses 10-second connect
and read timeouts, with at most three attempts and a 500 ms delay for connection
refusals/timeouts. Persistent failures still stop startup; issuer, audience,
signature and expiry validation remain mandatory. HTTP errors and TLS certificate
errors are not retried.

Optional Spring properties: `moodly.auth0.http.connect-timeout`,
`moodly.auth0.http.read-timeout`, `moodly.auth0.http.max-attempts` (1–5), and
`moodly.auth0.http.retry-delay`. Their environment equivalents are
`MOODLY_AUTH0_HTTP_CONNECT_TIMEOUT`, `MOODLY_AUTH0_HTTP_READ_TIMEOUT`,
`MOODLY_AUTH0_HTTP_MAX_ATTEMPTS`, and `MOODLY_AUTH0_HTTP_RETRY_DELAY`.
No new secret is required. If all attempts fail, check network/proxy/firewall
access to Auth0 rather than repeatedly restarting Docker or disabling JWT security.

Before first use, copy `.env.local.example` to `.env.local` and `.env.test.example` to `.env.test` if the environment files are not already present. `.env.local` is ignored by Git; it is also where Auth0 and Cloudinary development credentials belong.

### Auth0 and Cloudinary local services

Before starting the Phase 3 backend, configure the hosted development services and fill the corresponding blank values in `.env.local`:

- In Auth0, create a Moodly API with a stable identifier and use it as `AUTH0_AUDIENCE`. Set `AUTH0_ISSUER_URI` to the tenant issuer URL, including `https://` and its trailing slash. Create a local SPA client, then add the local frontend URL (for example, `http://localhost:3000`) to its allowed callback, logout, and web-origin URLs.
- In Cloudinary, create the signed `moodly_local_signed` upload preset, accept only `jpg`, `jpeg`, `png`, and `webp`, store its Media Library assets in `moodly/local`, and disable overwrite. Set its values as `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`, and `CLOUDINARY_UPLOAD_PRESET`. Never copy `CLOUDINARY_API_SECRET` into frontend configuration.
- Set `CLOUDINARY_FOLDER` to `moodly/local`. The Phase 3 backend will append the resource type and owner, for example `users/{userId}/avatar/{uuid}`. Set `APP_CORS_ALLOWED_ORIGINS` to the exact comma-separated local frontend origins allowed to call the API. Do not add production URLs here yet.

`make local-run` exports these variables before launching the `local` profile. The Auth0 issuer, API audience, Cloudinary settings, and CORS origins are deliberately bound only by `application-local.yaml`; production configuration is deferred to Phase 3 deployment preparation.

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

   `GET /entries/search?q=...` queries the derived Elasticsearch index. It is
   eventually consistent: a newly saved MongoDB entry can take a short time to
   appear in search results.

   The CDC verification scenarios in `docs/testing/moodly.http` cover insert,
   update, reindex/duplicate delivery, dead-letter replay, and health. The
   delete scenario uses `mongosh` until an entry-delete API is introduced.

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

`POST /habits` creates a new UUID for every accepted request and does not accept
an idempotency key. A client must not automatically replay it after a timeout or
lost response; it should read the habit list before a user chooses to submit it
again. `PATCH /habits/{habitId}` and the archive/restore POSTs require the latest
habit `version`. A stale version returns HTTP 409 `CONFLICT`; read the current
habit before deciding on another change.

`PUT /entries/today/mood` and `PATCH /entries/today` set the requested state.
Repeating the same body leaves the same visible mood or habit-log state, but
updates metadata and can emit another CDC event. These entry writes have no
version check against concurrent changes. Avatar upload signature/confirmation
also has no general replay guarantee; each signature allocates a new public ID
and confirmation consumes its pending upload.

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
