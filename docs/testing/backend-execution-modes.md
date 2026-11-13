# Backend Execution Modes

## One Workstation Stack

`moodly-backend-local` owns MongoDB, Elasticsearch, and an optional `backend` service. The backend image is `moodly-backend:local`; existing MongoDB/Elasticsearch container and physical volume names are retained. The FE project is managed in its own repository. Local, test, and production remain the only deployment environments.

| Mode | Command | Data | Backend Location |
| --- | --- | --- | --- |
| Host development | `make local-run` | Retained local namespace | Maven on host |
| Host manual test | `make test-run` | Reserved test namespace | Maven on host |
| Container development | `make local-container-run` | Retained local namespace | Optional Compose backend |
| Container manual test | `make test-container-run` | Reserved test namespace | Optional Compose backend |
| Automated integration | `make verify` | Reserved test namespace | JUnit/MockMvc, no backend container |

Build the image once with `make local-container-build`, or use `make local-container-build-run` to build and launch local mode. Run commands from Git Bash on Windows, or WSL with its own Java 25 installation. Native PowerShell may resolve bare `bash` to WSL; do not assume that WSL uses the Windows `JAVA_HOME`. All modes use the selected infrastructure env file through the existing tooling; set `MOODLY_INFRA_ENV_FILE` to select a different file. Container launch deliberately uses `--no-build`, so a missing image fails instead of unexpectedly compiling during startup.

## Configuration and Network Boundaries

The launcher explicitly passes the selected env-file path to Compose. The optional backend reads it as a runtime env file; no private dotenv file is copied into the image. Compose overrides only the container-facing coordinates, internal port, application profile, and owned data identities. The host still calls `http://127.0.0.1:8080` by default; both host modes and container modes use the configured host `SERVER_PORT` and run sequentially.

The container uses Compose DNS `mongodb` and `elasticsearch`, while host tooling uses loopback published ports. Its MongoDB URI uses `directConnection=true` only for this single-node development replica set. This avoids discovering a host-only advertised `localhost` address without modifying the existing replica-set configuration. Do not copy this connection policy into Atlas production; see [MongoDB directConnection guidance](https://www.mongodb.com/docs/manual/reference/connection-string-options/#directconnection).

The Compose profile is a service selector, not a new deployment environment. Ordinary infrastructure commands target only MongoDB and Elasticsearch. [Compose profiles](https://docs.docker.com/compose/how-tos/profiles/) keep the app opt-in. Raw `docker compose up backend` bypasses launcher ownership and replica-set initialization; use the supported Make commands.

`MOODLY_CONTAINER_MODE`, `MOODLY_CONTAINER_PROFILE`, `MOODLY_CONTAINER_DATABASE`, and `MOODLY_CONTAINER_INDEX` are launcher-controlled interpolation settings. Do not persist overrides in private dotenv files: manual mode must retain the exact reserved `moodly_test` database and `daily_entries_search_test` index. The same test lease guards host manual, container manual, and automated integration.

## Lifecycle and Recovery

Startup validates the port and joins the infrastructure registry before starting missing dependencies. It waits for MongoDB ping, initializes an absent replica set, waits for PRIMARY/Elasticsearch health, then starts the backend and awaits its Docker healthcheck. An existing replica set is never automatically reconfigured. Container startup and cleanup are serialized so interruption cannot create a backend after cleanup has already run.

The container launcher stays in the foreground. It prints lifecycle progress; follow application logs in another terminal with `make local-container-logs`. Ctrl+C stops the backend gracefully before manual test reset and infrastructure release. `make local-up` pins dependencies; ending a backend/test session preserves pinned or preexisting dependencies and those still used by another consumer. No normal cleanup deletes volumes.

Host and container backends cannot claim the shared port simultaneously. An IDE process on that port is rejected too. An IDE that starts outside the launcher has no registry lease: explicitly pin infrastructure with `make local-up` and stop it yourself after finishing IDE work.

Force termination cannot run cleanup hooks. A surviving backend container is an independent liveness signal: it blocks another backend and infrastructure stop/down/clean, and preserves auto-owned dependencies even after the launcher's PID record becomes stale. A surviving manual-test container also blocks automated test entry, so a released OS file lock cannot authorize reset while its writers still run.

Use `make local-container-stop` to stop an orphaned container and release idle, automatically owned dependencies. It refuses while a backend launcher is active; use Ctrl+C in that launcher instead. Recovery does not promise a reset after a hard kill: the next test session clears its reserved data before starting. Never delete registry/lock files to bypass a live consumer.

## Frontend CORS Contract

The local template allows explicit origins for Vite dev on port 5173 and container/Playwright on port 4173, for both `localhost` and `127.0.0.1`. Existing private env files retain their own values until explicitly synchronized. This does not update Auth0 callback/logout settings or prove those hosted settings match. Review them separately before authenticated browser E2E.

A browser must use a host-reachable API URL. Compose DNS names are for server-to-server traffic and must not be embedded in a browser bundle. FE configuration, browser session isolation, and authenticated E2E remain a separate frontend change.

## Verification

Run `make verify` for unit/integration, coverage, and formatting. Build the real Dockerfile and exercise the optional container mode with local synthetic OIDC configuration. Check occupied-port rejection, host/container exclusivity, manual reset isolation, startup failure cleanup, Ctrl+C, surviving-container recovery, and preservation of pinned/preexisting dependencies. Record what was actually executed; a local pass does not imply CI or hosted E2E passed.

Business dates continue to use the existing `MoodlyTime.ZONE` (`Asia/Ho_Chi_Minh`) rather than the container OS default. OS/log timezone alignment, documentation language normalization, and Render/release deployment changes are outside this implementation.
