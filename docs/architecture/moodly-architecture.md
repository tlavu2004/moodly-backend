# Moodly Module Boundaries

Moodly is a modular monolith: one Spring Boot application and one deployable unit.

## Module ownership

- `modules.auth` owns users, password hashing, JWT issuance/verification, and Spring Security integration.
- `modules.habits` owns habit lifecycle operations.
- `modules.entries` owns daily entries and mood logging.
- `modules.stats` owns streak and aggregation-based statistics.
- `modules.search` owns Elasticsearch index models and the search API.
- `modules.cdc` owns MongoDB Change Stream consumption and MongoDB-to-Elasticsearch synchronisation.
- `shared` contains only genuinely cross-cutting contracts, errors, and configuration.

## Dependency rules

- Controllers call their own module's application services, never another module's repository.
- Repositories are private to their owning module's infrastructure package.
- Cross-module communication uses explicit application-service interfaces or small contracts.
- The `cdc` module may consume entry change events and write to the search adapter, but it must not contain entry business rules.
- The `search` module is a derived read index; MongoDB remains the source of truth.
- Avoid direct imports from another module's `api` or `infrastructure` package.

These rules are documented now and should be enforced with an architecture test once the first controllers and repositories are added.
