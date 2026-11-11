# Shared Infrastructure Migration Verification

## Scope

Verification performed on 2026-11-11 on Windows 11 with Docker Desktop, Java 25,
Maven Wrapper 3.9.16, Python 3, and Git Bash. This change retains one workstation
Compose project, `moodly-local`, and reserves `moodly_test` /
`daily_entries_search_test` for disposable test data. No real env file was edited.
No retained local volume or old test volume was deleted. No image was published
and no Render/GitHub service setting was changed.

## Results

| Check | Result |
| --- | --- |
| Full Java test suite | 244 tests, 0 failures, 0 errors, 0 skipped; includes all 50 infrastructure/context methods |
| Python target/lease guards | 5 tests pass, including collisions, unknown/wildcard targets, inherited overrides, public binding rejection, and cross-process contention/release |
| Coverage | JaCoCo instruction and branch thresholds pass |
| Formatting and packaging | Spotless and Maven verify quality/package goals pass |
| Workflow syntax | Actionlint passes for verify, PR CI, and release workflows |
| Shell syntax | Bash parsing passes for bootstrap, local runner, test runner, and MongoDB cleanup script |
| Native launcher compatibility | Git Bash to Python to Bash preserves command paths and enforced test namespace/HTTP port |
| Java/Python lock interoperability | A Python-held lease blocks Java FileLock; Java acquires after Python releases |
| Local data preservation | A unique sentinel collection in the configured local database and a unique local search index survive complete test cleanup |
| Shared infrastructure lifetime | MongoDB and Elasticsearch remain running and healthy after the suite |

Two full Java test runs passed their functional and coverage checks. Their overall
verify commands initially failed the formatting gate because the modified Java
harness had LF endings instead of the Windows checkout's expected line endings.
The first file-filtered formatting command matched no file on Windows. Running the
repository formatter without that filter changed only the harness. A subsequent
`./mvnw -DskipTests verify` passed packaging, coverage from those executions, and
Spotless; Java tests were not needlessly repeated for an end-of-line-only change.

Only the uniquely owned sentinel resources were removed after the preservation
check. Shared containers were left running intentionally. Guard/unit checks do not
reset application data. Full integration cleanup was limited to the two reserved
test namespaces authorized for disposal.

## Remaining Validation Boundaries

The GitHub workflow was linted locally; its hosted Linux execution will occur on
PR/release according to existing triggers. WSL/Linux launcher interruption was not
exercised on this Windows host. Windows normal manual interruption includes process
tree termination, but a force-killed launcher can orphan child processes; inspect
and stop such children before reusing fixed test namespaces.

Hosted Auth0 login, Cloudinary uploads, and browser E2E were not rerun for this
infrastructure migration. Automated integration continues to use test fixtures.
Container runtime/application code did not change, so the earlier image smoke
result is not presented as a newly executed smoke test for this migration.

A lease is scoped to this checkout. Concurrent checkouts/hosts require separate
namespace allocation or an external lease service. Logical namespaces on these
unauthenticated engines are not a security boundary against untrusted clients.

## Related Documents

- [Shared Development Infrastructure and Test Data Isolation](../architecture/shared-infrastructure-isolation.md)
- [Release and Production Deployment](../deployment/release.md)
