# Shared Backend Port and Infrastructure Session Verification

## Final Behavior

Local and manual backends use the same SERVER_PORT (8080 by default) and run one
backend session at a time. Docs use SERVER_PORT=8081 from the docs fixture file.
JUnit/MockMvc integration does not need a dedicated HTTP port. The obsolete
MOODLY_MANUAL_TEST_PORT was removed from the local template and private local file;
all other private env assignments, including credentials, were preserved.

Test database moodly_test and index daily_entries_search_test stay isolated from
local data. Manual tests reset these resources before backend launch and after
exit, startup failure, or normal Ctrl+C. Automated tests retain per-context-group
reset plus session cleanup. Hosted accounts/media are not deleted.

## Ownership Model

InfrastructureSessions coordinates consumers under ignored .moodly-infrastructure/.
A short OS registry lock serializes joins, startup, pinning, release, and operator
stop/down. State records only participant tokens, PID/start time, session kind,
automatically owned service names, and pin status. No env or credential is stored.

The final consumer stops only services that tooling started automatically.
Preexisting services remain running. A partial preexisting stack preserves the
service it did not start. Explicit make local-up pins the stack, including during
an active session. Operator stop/down/clean rejects active consumers. Session
cleanup calls stop only; it never removes containers/networks or deletes volumes.

The existing .moodly-test.lock still prevents manual/automated test-data collisions.
Registry backend-session exclusion prevents local/manual HTTP port conflicts.
A local backend and automated tests may share infrastructure concurrently because
their data namespaces differ. The final consumer owns automatic shutdown.

Shutdown ordering is backend writers/context -> test data -> infrastructure session
-> test lease. Cleanup failure fails the command; failed service stops retain pending ownership
for recovery. Force kill cannot run hooks; the next operation prunes stale PID/start-time
records. State/lock files must not be unlinked while sessions are active. Coordination
is per checkout; raw Docker commands and other checkouts bypass it.

## Changes and Commit Boundaries

1. Port/config commit: common backend SERVER_PORT, docs 8081, obsolete manual port
   removed, port/env guard expectations updated.
2. Lifecycle/cleanup commit: InfrastructureSessions, ownership regression tests,
   launcher and JUnit integration, guarded operator commands, shared test reset,
   and ignored registry state. local-status displays stopped as well as running containers.
3. Documentation commit: architecture, operating instructions, verify recipe, and
   this validation record.

## Executed Validation on 2026-11-11

| Scenario | Result |
| --- | --- |
| Cold full Maven verify | 265 tests, 0 failures/errors/skips; coverage/Spotless pass; auto-started MongoDB/Elasticsearch stopped afterward |
| Explicitly pinned full Maven verify | 265 tests, 0 failures/errors/skips; coverage/Spotless pass; dependencies remain running |
| Ownership regression suite | 11 tests cover warm/cold/partial startup, overlapping consumers, pinning, active operator rejection, failed startup, stale PID recovery, repeated close, and retry after stop failure |
| Manual backend startup | Public fixture env and local OIDC fixture; backend healthy on 8080 without hosted Auth0/Cloudinary calls |
| Manual pre-start cleanup | Previously seeded disposable test markers absent after startup |
| Active operator stop | Rejected while manual backend session active; services stay running |
| Manual Ctrl+C through Windows/Git Bash terminal | HTTP listener closes, reserved data resets, both automatically started services stop, registry participants/automatic ownership empty |
| Actual application startup failure | Invalid blank fixture issuer fails startup; test cleanup runs and automatically owned services stop |
| Local data preservation | Unique sentinel collection in the configured local DB and a separate local search-index sentinel survive all test cleanup scenarios |
| Documentation generation from stopped stack | Java resolves docs port 8081; OpenAPI generation succeeds; committed contract unchanged |

Only verification-owned local sentinel resources were removed after their
preservation checks. The temporary OIDC fixture container was removed. Local
MongoDB/Elasticsearch were left stopped; local volumes and old stopped test
containers/volumes were not deleted. Actual app code and hosting settings were not
deployed or changed. Windows/Git Bash interruption was exercised; hosted Linux CI
is still the authority for its own platform execution.

## Verify Again Once

With Docker Desktop running, close any active backend/manual test session first.
At the repository root in Git Bash or WSL:

```bash
make local-stop
./mvnw --batch-mode verify
make local-status
```

Expect BUILD SUCCESS and 265 tests. With the cold unpinned stack above, MongoDB and
Elasticsearch should show Exited afterward. Data volumes are preserved. If you run
make local-up first, that deliberately pins the stack and verify will leave it up.
For PowerShell, .\mvnw.cmd --batch-mode verify is the wrapper equivalent; Make
commands that call Bash still require Git Bash/WSL (or Bash on PATH).

Run make openapi separately to regenerate/compare the API contract. Backend usage
is make local-run for retained local data or make test-run / make test-search-run
for disposable test data. Ctrl+C exits the backend and applies ownership cleanup.

## Windows Make Recipe Shell Follow-Up

The Makefile explicitly declares Bash and Bash shell flags for its recipes. Native
Windows GNU Make can otherwise use cmd.exe even when invoked from Git Bash; this
breaks ./mvnw, single-quoted test selectors, and environment-assignment recipes.

The actual make verify target was executed through Windows GNU Make in Git Bash:
265 tests passed, along with coverage and Spotless. The isolated Windows Ctrl+C
probe was repeated with the same shell flags; clean interrupted cleanup still did
not emit a Make recipe Error 130. Make may retain its own SIGINT exit status as
documented in [Backend Ctrl+C Exit Handling](interrupt-exit-status.md).
