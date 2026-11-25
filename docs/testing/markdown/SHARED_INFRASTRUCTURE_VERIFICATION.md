# Shared Infrastructure Verification

## Current Contract

One Compose project, `moodly-backend-local`, serves host development, optional backend container sessions, and integration tests. MongoDB and Elasticsearch retain their established physical volumes. Tests own only database `moodly_test` and concrete index `daily_entries_search_test`; local data is retained.

This is the current verification procedure. Earlier Python harness experiments, test totals, and the retired separate test stack are historical implementation evidence, not current setup requirements. See [Backend Execution Modes](BACKEND_EXECUTION_MODES.md) for commands, network boundaries, ownership, and recovery.

## Prerequisites

- Docker Desktop with the Linux engine running.
- Java 25 and Maven Wrapper. On Windows use Git Bash; WSL needs its own Java 25 installation.
- Private local configuration created from `.env.local.example`, or the tracked template selected explicitly for automated fixtures.
- Python 3 for image smoke and release/OpenAPI helpers; backend unit/integration and local infrastructure tooling use Java.

## Routine Verification

```bash
make verify
make local-status
```

Verify must pass tests, coverage, and formatting. From a stopped stack, the harness starts dependencies and stops the ones it owns after the final session. Already running or explicitly pinned dependencies remain running. Exact counts belong to the current Surefire report and CI run, not a permanent number in this document.

```bash
make local-up
make verify
make local-status
make local-stop
```

The explicit pin in this sequence keeps the infrastructure running until the final operator stop. No command above deletes volumes. Close backend/test sessions before operator stop/down/clean.

## Data and Concurrency Checks

- Reject local database/index collisions with reserved test identities before any reset.
- Reject wildcard, alias, list, and unknown cleanup targets.
- Coordinate host manual, container manual, and automated integration with the same test lease.
- Permit local backend and automated tests to share engines while using distinct data namespaces.
- Reject simultaneous host/container backends on the common HTTP port.
- Preserve dependencies while another consumer or an orphaned backend container remains active.
- Block automated reset while a manual-test backend container still owns the test namespaces, even if its launcher was force-killed.
- Verify cleanup after startup failure and interruption, and surface cleanup failures as errors.

For a local-data preservation check, create a uniquely identified sentinel, verify it survives tests, then remove only that sentinel. This check requires explicit authorization to write local data; do not inspect or clear arbitrary local collections.

## Ports and Configuration

Host and manual backends use `SERVER_PORT`, normally 8080, sequentially. The optional container also exposes this host port and listens internally on 8080. Docs tooling uses 8081. JUnit/MockMvc integration needs no separate HTTP port. FE Vite dev normally uses 5173; FE container/Playwright normally uses 4173. FE-owned configuration is verified in its own repository.

The selected `.env.local` or `MOODLY_INFRA_ENV_FILE` supplies runtime/infrastructure settings. Automated fixtures belong in `application-test.yaml`; the retired `.env.test` overlay is not read. Do not print resolved Compose configuration containing credentials. Use `make validate-local` for a quiet validation.

## Compose Project Migration

Physical volumes retain their `moodly-local_` prefix explicitly, preventing the project rename from selecting empty databases. Container names are also retained. This storage prefix does not imply a second running stack.

Close all backend/test sessions and confirm no live or pinned consumer needs the old project before migration. For the earlier project name, remove only its containers/network:

```bash
docker compose -p moodly-backend --env-file .env.local -f compose.local.yaml down
```

Use `-p moodly-local` instead if migrating the oldest project name. Never add `-v` or run volume prune for a naming migration. The next supported launcher creates `moodly-backend-local`; verify its mounts reference the original named volumes.

## Container and Hosted Checks

Build with `make local-container-build`, then run `make local-container-run` or `make test-container-run`. Follow application logs with `make local-container-logs`; Ctrl+C in the owning launcher closes the session. Use `make local-container-stop` only for orphan recovery.

Production image smoke uses independent, disposable fixtures. Hosted Auth0/Cloudinary E2E requires dedicated identities and separately authorized cleanup. Neither local verification nor a published image proves a Render service has been deployed successfully.
