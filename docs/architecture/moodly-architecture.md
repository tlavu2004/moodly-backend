# Moodly Module Boundaries

Moodly is a modular monolith: one Spring Boot application and one deployable unit.

Each feature module uses `controller`, `service`, `model`, and `repository` packages
where needed. Modules may also have focused packages such as `config`, `integration`,
`listener`, or `monitoring`. HTTP controllers live in `controller`, and modules may
place request/response types in a sibling `dto` package. Use-case orchestration
lives in `service`; MongoDB documents and business value types live in `model`;
persistence access lives in `repository`.

The cross-cutting `shared` package uses `controller` for HTTP advice and DTOs,
`config` for Spring configuration, `filter` for request filtering, `error/code`
for error contracts and codes, `error/exception` for shared exceptions, and
`time` for business-time utilities.

## Module ownership

- `modules.auth` owns application profiles, Auth0 JWT validation, avatar lifecycle,
  and Spring Security integration.
- Within `modules.auth`, `security` resolves the verified identity and configures JWT validation,
  `job` runs avatar cleanup, `service/result` holds avatar use-case results, and
  `exception` holds avatar-specific errors.
- `modules.habits` owns habit lifecycle operations.
- `modules.entries` owns daily entries and mood logging.
- `modules.stats` owns streak and aggregation-based statistics.
- `modules.search` owns Elasticsearch index models and the search API.
- `modules.cdc` owns MongoDB Change Stream consumption and MongoDB-to-Elasticsearch synchronization.
- `shared` contains only genuinely cross-cutting contracts, errors, and configuration.

## Dependency rules

- Controllers call their own module's services, never another module's repository.
- Repositories live in their owning module's `repository` package.
- Cross-module communication uses explicit application-service interfaces or small contracts.
- The `cdc` module may consume entry change events and write to the search adapter, but it must not contain entry business rules.
- The `search` module is a derived read index; MongoDB remains the source of truth.
- Avoid direct imports from another module's `controller`, `repository`, `config`,
  or `integration` package.

These are intended boundaries. Existing cross-module repository imports remain for a
later behavior-preserving refactor; package moves alone do not enforce the rules.
