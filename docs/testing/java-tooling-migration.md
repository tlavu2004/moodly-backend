# Java Development Tooling Migration

## Decision

Use one shared infrastructure definition and one local runtime env source. Keep
application test fixtures in Spring test resources. Implement namespace guards,
readiness, process lifetime, and the test lease in Java, reusing project dependencies.
No new Maven library dependency or production runtime component is introduced.

## File Inventory

| Status | File | Purpose |
| --- | --- | --- |
| Added | `src/test/java/com/tlavu/moodly/support/SharedInfrastructure.java` | Shared Java Compose/env parser, startup, target guard, and FileLock |
| Added | `src/test/java/com/tlavu/moodly/support/DevelopmentLauncher.java` | CLI commands for local/manual backends, infrastructure, docs port, and test cleanup |
| Added | `src/test/java/com/tlavu/moodly/support/SharedInfrastructureTest.java` | Nine JUnit ownership, lease, port, and environment guard tests |
| Added | `src/test/java/com/tlavu/moodly/support/SharedInfrastructureIntegrationTest.java` | One real Compose dotenv round-trip test without data reset |
| Added | `scripts/docker/run-tooling.sh` | Maven Wrapper classpath bootstrap and Java launch, including JAVA_HOME support |
| Converted | `scripts/docker/run-local-backend.sh` | Thin shell adapter to Java local mode |
| Converted | `scripts/docker/run-test-backend.sh` | Thin shell adapter to Java manual mode |
| Converted | `scripts/docker/start-infrastructure.sh` | Thin shell adapter to Java infrastructure startup |
| Converted | `src/test/java/com/tlavu/moodly/support/ComposeTestEnvironment.java` | Direct shared Java calls; no Python process or duplicate bootstrap |
| Converted | `src/test/resources/application-test.yaml` | Deterministic non-secret fixtures restored here; no dotenv template import |
| Converted | `src/main/resources/scripts/openapi/generate-openapi.sh` | Java reads the docs port; JSON normalization remains Python |
| Converted | `src/main/resources/scripts/mongodb/clean-mongodb.sh` | Test cleanup uses the same Java lease and reserved database |
| Converted | `.github/workflows/verify.yml` | Guards run inside the normal JUnit suite; standalone Python guard step removed |
| Removed | `scripts/docker/shared-infrastructure.py` | Replaced by shared Java tooling |
| Removed | `scripts/docker/test_shared_infrastructure.py` | Replaced by JUnit tests |
| Removed | `.env.test.example` | No separate runtime/test-fixture dotenv overlay |
| Retained | `compose.local.yaml` | The only workstation Compose stack |
| Retained | `.env.local.example` | Single local runtime/infrastructure template; adds manual backend port |
| Retained | `.moodly-test.lock` mechanism | Shared Java FileLock outside target/; generated file remains ignored |
| Removed locally | Private `.env.test` | Deleted after checking key names; contained only obsolete non-secret test settings |
| Retained | `.env.docs.example` and optional `.env.docs` support | Documentation tooling fixtures, not another infrastructure stack |
| Retained | `scripts/docker/smoke-test.py` | Image smoke orchestration is outside the shared-stack migration |

The earlier manual-port setting was removed by the later session migration.
Local and manual now use the same SERVER_PORT; other assignments, including
credentials, are preserved. Private
env files are not committed. The retired test file was subsequently removed after
a key-name-only check confirmed it contained no credential keys. The launcher and
JUnit configuration do not depend on it.

## Runtime Behavior (Updated by Session Ownership Migration)

- Local backend: SERVER_PORT from the selected local env file, normally 8080.
- Manual test backend: the same SERVER_PORT, normally 8080; run one backend at a time.
- Docs tooling: SERVER_PORT from docs fixture properties, normally 8081.
- Local namespaces retain their existing identities and data.
- Manual and automated test namespaces remain fixed as moodly_test and
  daily_entries_search_test. Local collisions and unsafe targets are rejected.
- Manual mode inherits local CDC/search/rate-limit settings and explicit caller
  overrides. There is no second dotenv file overriding these settings.
- The last infrastructure consumer stops only automatically owned services.
  Preexisting or explicitly pinned services remain running. Manual test data is
  disposable and resets before launch and after exit.
- A legacy clean-mongodb.sh .env.test argument still selects guarded test cleanup;
  it does not read the retired env file. Prefer clean-mongodb.sh test.

The bootstrap compiles test-side tooling and resolves the existing dependency
classpath through Maven Wrapper before starting Java. Repeated calls use Maven's
incremental compilation/cache. The JUnit harness calls shared Java code directly,
so it does not invoke the bootstrap or recursively run Maven.

## Verification

On 2026-11-11, full Maven verify passed 254 JUnit tests (zero failures/errors/skips),
coverage gates, packaging, and Spotless on Windows with Docker Desktop. This includes
all original 244 Java tests plus nine Java guard tests and one real Compose parser
test. The seven former Python guard tests are replaced, not counted as JUnit tests.

Git Bash successfully launches the Java tooling and reads its non-secret config.
A native Java launcher probe starts Windows Maven Wrapper with --version and exits
cleanly. Docs generation reads its port through Java and reproduces the committed
OpenAPI contract without changes. Actionlint and shell syntax checks pass.

The guard suite also verifies literal dollar signs, pipes, quotes, multiline values,
key scanning, invalid targets, local/test collisions, port validity, and lease
contention/release. Additional invalid-port cases were checked with a targeted
rerun of all ten new tests after the full-suite run.

Linux/WSL execution of the Java launcher is left to hosted CI or a Linux developer
machine; it is not claimed as locally exercised on this Windows host. Hosted Auth0,
Cloudinary uploads, browser E2E, and a new image publish/deploy are outside this
migration. Python still formats OpenAPI JSON and CI reports and orchestrates image
smoke tests; backend unit/integration and shared-stack tooling use Java.
