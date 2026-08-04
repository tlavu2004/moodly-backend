# Moodly — Habit & Mood Tracker

> A small application for learning through a practical problem: tracking daily habits and moods.

---

## 1. Overview

**Application name:** `Moodly`  
**Goal:** Learn MongoDB, Change Data Capture (CDC), Elasticsearch, and JWT authentication through one self-contained application. No UI is required; endpoints are exercised through a `.http` file.  
**Estimated duration:** 3 evenings—one evening for each learning phase.  
**Suggested stack:** A modular-monolith Spring Boot application + Spring Data MongoDB + Elasticsearch + Spring Security. One deployable owns all modules; no microservices are required.

### Git Workflow

**Branch strategy:** One short-lived branch per phase, created directly from the latest `main`.  
**Commit convention:** [Conventional Commits](https://www.conventionalcommits.org/): `type(scope): concise imperative summary`. Keep each commit buildable and focused; do not mix infrastructure, feature behavior, and broad refactors in one commit.  
**When to create a branch:** Create the phase branch immediately before starting that phase, after its prerequisite phase has been Squash Merged into `main`.  
**Merge strategy:** Squash Merge each completed phase branch directly into `main`. Include the phase's automated/manual tests in the same branch and squash them with the implementation; do not create separate test branches or standalone test commits on `main`.

Before creating the first phase branch, make two small commits on `main`:

1. Commit this file as `docs/blueprint/moodly-blueprint.md` with: `docs: add Moodly learning blueprint`.
2. Commit the existing generated Spring Boot skeleton with: `chore(app): initialise Moodly project`.

Create the first phase branch with:

```bash
git switch main
git pull
git switch -c feature/mongodb-core
```

The first commit should be the Phase 1 bootstrap checkpoint. Commit the later checkpoints within the same phase branch, then squash them into one completed-phase commit on `main`.

#### Phase branches

Create each branch from the updated `main` after the previous phase has been completed:

```text
main
├── feature/mongodb-core          # Phase 1; squash merge to main
├── feature/cdc-elasticsearch     # Phase 2; starts from updated main
└── feature/jwt-auth              # Phase 3; starts from updated main
```

- [ ] Create `feature/mongodb-core` from `main`; complete Phase 1 plus its tests, then Squash Merge as `feat(mongodb): add Moodly MongoDB core`.
- [ ] Create `feature/cdc-elasticsearch` from the updated `main`; complete Phase 2 plus resilience/search tests, then Squash Merge as `feat(search): add CDC-backed Elasticsearch search`.
- [ ] Create `feature/jwt-auth` from the updated `main`; complete Phase 3 plus authentication/isolation tests, then Squash Merge as `feat(auth): add self-issued JWT authentication`.

This leaves `main` with five meaningful commits: the blueprint, the initial project skeleton, then one squashed commit for each phase. Use merge commits instead only if preserving every intermediate checkpoint and its detailed development history matters more than keeping `main` concise.

### Modular Monolith Structure

Keep one Spring Boot application and one deployment unit. Separate code by business capability rather than by global technical layers, so each module owns its API, application logic, domain model, and persistence adapters where useful.

```text
com/tlavu/moodly
├── modules/
│   ├── auth/       # users, password hashing, JWT, Spring Security integration
│   ├── habits/     # habit lifecycle
│   ├── entries/    # daily entries and mood logging
│   ├── stats/      # streak and aggregation-based statistics
│   ├── search/     # Elasticsearch index model and search API
│   └── cdc/        # Change Stream listener, retry, DLQ, reindexing
└── shared/         # small cross-cutting contracts, errors, configuration
```

Modules communicate through in-process Java interfaces or explicit application services—not HTTP, message brokers, or independent deployments. Keep MongoDB as the source of truth; the `cdc` module is an internal asynchronous adapter that maintains the Elasticsearch index.

### What can a user do?

1. Mark habits as completed for a given day (potentially multiple times a day).
2. Record an end-of-day mood once per day—a score from 1 to 5 plus an optional note.
3. View statistics: current streaks, mood trends, and frequently missed habits. This is the section that uses aggregation pipelines most heavily.

---

## 2. Schema Design

### Collection: `habits`

A list of habits a user wants to track. These change infrequently.

```javascript
const habit = {
  _id: "exercise",
  userId: "user_123",
  name: "Exercise",
  icon: "🏃",
  targetFrequency: "daily",
  active: true
};
```

### Collection: `daily_entries`

Each document represents one day for one user. It demonstrates the flexibility of a document database particularly well.

```javascript
const dailyEntry = {
  _id: ObjectId("..."),
  userId: "user_123",
  date: ISODate("2026-08-03"),
  mood: {
    score: 4,
    tags: ["productive", "tired"],
    note: "I was tired today because I stayed up late."
  },
  habits: [
    { habitId: "exercise", done: true, note: "30-minute run" },
    { habitId: "reading", done: true, note: null },
    { habitId: "meditation", done: false, note: null }
  ],
  createdAt: ISODate("2026-08-03T21:00:00Z"),
  updatedAt: ISODate("2026-08-03T21:05:00Z")
};
```

### Collection: `users` (introduced in Phase 3)

Application-owned identities. Passwords are never stored in plaintext.

```javascript
const user = {
  _id: ObjectId("..."),
  email: "user@example.com",
  passwordHash: "$2a$...",
  createdAt: ISODate("2026-08-03T09:00:00Z"),
  updatedAt: ISODate("2026-08-03T09:00:00Z")
};
```

**Indexes to create:**

```javascript
db.daily_entries.createIndex({ userId: 1, date: -1 }, { unique: true });
db.habits.createIndex({ userId: 1, active: 1 });
db.users.createIndex({ email: 1 }, { unique: true });
```

The three document examples above are assigned to variables intentionally: this keeps the `javascript` code fences valid for IDE JavaScript parsers while remaining executable MongoDB-shell-style examples.

---

## 3. Minimum API

| Method   | Endpoint                        | Description                                                                    |
|----------|---------------------------------|--------------------------------------------------------------------------------|
| `POST`   | `/habits`                       | Create a habit.                                                                |
| `GET`    | `/habits`                       | Get active habits.                                                             |
| `PATCH`  | `/entries/today`                | Mark or unmark one habit for today (upsert).                                   |
| `PUT`    | `/entries/today/mood`           | Record today's mood.                                                           |
| `GET`    | `/entries?from=&to=`            | Get entries within a date range.                                               |
| `GET`    | `/habits/{id}/streak`           | Calculate a habit's current streak.                                            |
| `GET`    | `/stats/mood-trend?period=week` | Get the weekly mood trend.                                                     |
| `GET`    | `/stats/most-missed-habits`     | Get the most frequently missed habits.                                         |
| `GET`    | `/entries/search?q=&from=&to=`  | Search indexed mood notes, habit notes, and mood tags (introduced in Phase 2). |
| `POST`   | `/auth/register`                | Register a user (introduced in Phase 3).                                       |
| `POST`   | `/auth/login`                   | Authenticate and receive access and refresh tokens (introduced in Phase 3).    |
| `POST`   | `/auth/refresh`                 | Exchange a valid refresh token for a new access token (introduced in Phase 3). |

---

## 4. Detailed Implementation Checklist

### Testing Cadence

Complete and test one phase before starting the next phase. Keep tests in the same phase branch as the implementation and include them in that phase's Squash Merge; do not postpone all testing until Phase 3.

| Phase                        | Test immediately after                     | Minimum verification                                                                                                                        |
|------------------------------|--------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| Phase 1 — MongoDB Core       | Each CRUD, aggregation, and streak slice.  | `.http` scenarios, `mongosh` pipeline checks, validation/error cases, and focused automated tests for important application logic.          |
| Phase 2 — CDC Search         | The Change Stream listener and search API. | Insert/update/delete synchronisation, duplicate delivery, retry/DLQ, listener restart, Elasticsearch outage, reindex, and user filtering.   |
| Phase 3 — JWT Authentication | The `SecurityContext` migration.           | Register/login/refresh, invalid and expired tokens, refresh rotation/revocation, and cross-user isolation across MongoDB and Elasticsearch. |

**Per-phase loop:** implement one coherent slice → verify manually with `.http` or `mongosh` → add focused automated tests → run the phase verification scenarios → commit checkpoints → Squash Merge the completed phase.

### Project Setup & Status (Cross-Phase Track)

This track applies to the whole project rather than to Phase 1 alone. Update it whenever a foundational capability becomes available.

**Current setup status (checked 2026-08-03):** The Spring Boot skeleton, Maven files, application entry point, and basic context test exist. MongoDB is not connected yet, and no feature module implementation has started.

| Area                                 | Status            | Evidence / next step                                                                       |
|--------------------------------------|-------------------|--------------------------------------------------------------------------------------------|
| Spring Boot application skeleton     | `[x]` Setup       | Create the Spring Boot application structure.                                              |
| MongoDB dependency                   | `[x]` Setup       | Add the MongoDB starter dependency to `pom.xml`.                                           |
| Web and validation dependencies      | `[x]` Setup       | Add WebMVC and Validation starters.                                                        |
| Modular-monolith packages            | `[x]` Setup       | Feature package markers and `docs/architecture.md` exist.                                  |
| MongoDB Docker/replica set           | `[x]` Verified    | `mongo:8.3.7` and idempotent `mongodb-init` are running successfully.                      |
| MongoDB connection                   | `[x]` Verified    | `mongosh` reports `rs0` with one `PRIMARY` member.                                         |
| Domain models and repositories       | `[ ]` Not started | Implement Phase 1 persistence.                                                             |
| Habit/entry APIs and `.http` tests   | `[ ]` Not started | Implement and exercise the core endpoints.                                                 |
| Aggregations, streak, and statistics | `[ ]` Not started | Implement Phase 1 stats.                                                                   |
| Elasticsearch and CDC                | `[ ]` Not started | Implement Phase 2 after MongoDB Change Streams prerequisites.                              |
| JWT authentication and API migration | `[ ]` Not started | Implement Phase 3 after the core APIs exist.                                               |

### Phase 1 — MongoDB Core (Evening 1)

#### Setup (30 minutes)

- [x] Create one Spring Boot application.
- [x] Add the MongoDB starter dependency to `pom.xml`.
- [x] Create the initial feature-module package structure (`auth`, `habits`, `entries`, `stats`, `search`, `cdc`, and `shared`) inside the single Spring Boot application.
- [x] Document explicit module boundaries in `docs/architecture.md`; enforce them with an architecture test once controllers and repositories are added.
- [x] Define the reproducible MongoDB replica-set Docker Compose configuration below, mapped to port 27017.
- [x] Configure `spring.data.mongodb.uri` in `application.yaml`.
- [x] Verify the connection through `mongosh`: MongoDB is healthy and replica set `rs0` reports one `PRIMARY` member.

> [!Note]
> `mongod --replSet rs0` enables replica-set mode but does not initialize a replica set by itself. `rs.initiate()` creates the single-node replica-set configuration required by MongoDB Change Streams in Phase 2; the `mongodb-init` service checks the status first so repeated `docker compose up` runs remain safe and idempotent.

**Commit checkpoint:** `chore(application): initialize Moodly application with MongoDB setup and modular structure`

#### Model and Repository (30–45 minutes)

- [x] Create a `Habit` class for the `habits` document.
- [x] Create a `DailyEntry` class with nested `Mood` and `HabitLog` classes for the `daily_entries` document.
- [x] Create `HabitRepository extends MongoRepository<Habit, String>`.
- [x] Create `DailyEntryRepository extends MongoRepository<DailyEntry, String>`.
- [x] Create a unique `(userId, date)` index.
- [x] Run `DailyEntryRepositoryIntegrationTest` against the local MongoDB instance to observe the duplicate-key error.

**Commit checkpoint:** `feat(mongodb): implement DailyEntry and Habit entities with repositories`

#### Core API (45–60 minutes)

- [x] Implement `POST /habits` to create a habit.
- [x] Implement `GET /habits` to return active habits.
- [x] Implement `PATCH /entries/today` to upsert a habit log in today's entry, using repository lookup followed by save.
- [x] Implement `PUT /entries/today/mood` to set today's mood.
- [x] Implement `GET /entries?from=&to=` to query entries by date range.
- [x] Write `requests/moodly.http` to exercise all endpoints above with named variables and repeatable request scenarios.

**Commit checkpoint:** `feat(entries): add DailyEntry service and controller with habit and mood management`

#### Aggregation Pipeline (60–90 minutes; core learning section)

- [ ] Implement `GET /stats/mood-trend?period=week` using `$group` by week (`$week` or `$dateTrunc`) and `$avg`.
- [ ] Implement `GET /stats/most-missed-habits` using `$unwind`, `$match`, `$group`, and `$sort`.
- [ ] Implement `GET /habits/{id}/streak` by loading entries in descending date order and calculating the streak in the application layer. MongoDB aggregation is not a particularly clear fit for consecutive-day streaks.
- [ ] Test every pipeline directly in `mongosh` before implementing it in Java, so that each stage's output is understood.

**Commit checkpoint:** `feat(stats): add mood trend, missed habit, and streak statistics`

#### Polish (optional, if time remains)

- [ ] Add basic validation: mood score must be 1–5; date must not be in the future.
- [ ] Add a minimal `GlobalExceptionHandler`.

**Commit checkpoint:** `feat(validation): validate requests and standardise API errors`

- [ ] Write a concise README explaining how to run the project.

**Commit checkpoint (only if implemented):** `docs(readme): document local setup and API usage`

### Phase 2 — Elasticsearch via MongoDB Change Streams (CDC Pattern) (Evening 2)

Elasticsearch is a derived search index only. MongoDB remains the source of truth; the request/response flow must not write directly to Elasticsearch.

#### Infrastructure and Index Setup

- [ ] Update Docker Compose to run MongoDB as a single-node replica set, because Change Streams require a replica set or sharded cluster. Initialize the replica set and verify it with `rs.status()`.
- [ ] Add a pinned Elasticsearch Docker image and a persistent development volume. Configure a single-node development cluster and document its local port.
- [ ] Add the Elasticsearch Java client or Spring Data Elasticsearch dependency and configure the client in `application.yaml`.
- [ ] Create a `daily_entries_search` index with an explicit mapping. Use the MongoDB entry `_id` as the Elasticsearch document ID.
- [ ] Map `userId` and `date` as exact/filterable fields, `mood.score` as a numeric field, and `mood.note`, `habits.note`, and `mood.tags` as searchable fields. Add keyword subfields only where filtering or aggregation is needed.
- [ ] Create the index and mapping through an idempotent startup component or a versioned setup script; verify them with Elasticsearch's index and mapping APIs.

**Commit checkpoint:** `chore(search): add Elasticsearch and MongoDB replica-set infrastructure`

#### Change Stream Synchronisation Service

- [ ] Create a dedicated internal `cdc` module responsible only for synchronising `daily_entries` into Elasticsearch; it is part of the same deployable modular monolith, not an independently deployed service.
- [ ] Start a Change Stream listener using Spring Data MongoDB (for example, `MongoMessageListenerContainer`) against the `daily_entries` collection.
- [ ] Configure `fullDocument: UPDATE_LOOKUP` so update events can be indexed from the complete current MongoDB document.
- [ ] Handle `insert`, `update`, and `replace` by transforming the full document into one search document and indexing it with a deterministic ID. Handle `delete` by removing the matching Elasticsearch document.
- [ ] Keep the transformation deliberately denormalized: include searchable mood content, habit notes, tags, date, and `userId` in the search document. Do not make Elasticsearch the data source for entry details.
- [ ] Persist the last successfully processed resume token in a small MongoDB collection. On restart, resume from that token; if it is no longer valid, log the condition and run a controlled reindex.
- [ ] Build an explicit reindex command or protected maintenance endpoint that reads all MongoDB `daily_entries` in batches and rebuilds the Elasticsearch index.

**Commit checkpoint:** `feat(cdc): sync daily entries to Elasticsearch through change streams`

#### Failure Handling and Verification

- [ ] Make Elasticsearch writes idempotent so a duplicate Change Stream delivery is safe.
- [ ] Retry transient Elasticsearch failures with bounded exponential backoff and clear structured logs.
- [ ] After retries are exhausted, save the event metadata, error, attempt count, and payload or document ID to a temporary MongoDB dead-letter collection. Provide a small replay mechanism after the fault is resolved.
- [ ] Monitor listener health and failed-event count; fail or degrade clearly if the listener cannot be established.

**Commit checkpoint:** `feat(cdc): add retry, dead-letter handling, and reindex recovery`

- [ ] Test insert, update, delete, listener restart, Elasticsearch outage, duplicate delivery, and reindex recovery using the `.http` file and Docker logs.

**Commit checkpoint:** `test(cdc): cover change stream recovery and Elasticsearch failures`

#### Search API

- [ ] Implement `GET /entries/search?q=&from=&to=` against Elasticsearch, with required `q` validation and optional date filters.
- [ ] Filter every search query by the current user's `userId`. Until Phase 3 is complete, keep this temporary user-context mechanism isolated so it can be replaced by `SecurityContext`.
- [ ] Return search-oriented fields (highlight/snippet, entry ID, date, matching content) and fetch MongoDB only when an authoritative full entry is required.
- [ ] Document eventual consistency: an entry can be saved to MongoDB before it appears in search results.

**Commit checkpoint:** `feat(search): add entry search API`

#### CDC Trade-offs

| Concern           | Change Streams / CDC                                                                                | Direct synchronous Elasticsearch write                                                  |
|-------------------|-----------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|
| Request latency   | MongoDB writes complete without waiting for Elasticsearch.                                          | Each write waits for both systems.                                                      |
| Consistency       | Eventual consistency; search may lag briefly.                                                       | Results can appear immediately when both writes succeed.                                |
| Failure isolation | Elasticsearch outages do not block core writes; retry and replay are required.                      | An outage can fail or delay the user request unless degraded behaviour is added.        |
| Complexity        | Requires a replica set, listener lifecycle, resume tokens, idempotency, retry, DLQ, and reindexing. | Simpler initially, but dual-write consistency becomes the application's responsibility. |
| Recovery          | Replay and full reindex can rebuild a derived index from MongoDB.                                   | Must reconcile partial dual writes and missed updates manually.                         |

### Phase 3 — Self-Issued JWT Authentication (Evening 3)

Moodly owns its users and authentication flow. It issues and verifies its own tokens and does not depend on an external project or identity provider.

#### User Registration and Credentials

- [ ] Create the `users` collection and its unique email index. Define a `User` document with an internal ID, normalized email, password hash, and timestamps.
- [ ] Add `POST /auth/register`; validate email format and password requirements, normalize email consistently, reject duplicate emails, and never return `passwordHash`.
- [ ] Hash passwords with BCrypt using Spring Security's `PasswordEncoder`; never log, return, or store plaintext passwords.
- [ ] Add `POST /auth/login`; verify the password hash and return a short-lived access token plus a refresh token.

**Commit checkpoint:** `feat(auth): add user registration and password login`

#### Token Issuance and Verification

- [ ] Select and document one signing strategy: a sufficiently long random HMAC secret for local learning, or an asymmetric key pair when practising key rotation and verification separation.
- [ ] Keep secrets and token lifetimes outside source control through environment variables or local configuration. Do not hard-code them in Java or commit them to the repository.
- [ ] Include only necessary claims in the access token: subject/user ID, issued-at time, expiry, and optionally issuer/audience. Do not include a password, password hash, or sensitive profile data.
- [ ] Implement access-token creation and verification, including signature, expiry, issuer/audience (if configured), and malformed-token handling.
- [ ] Implement refresh-token rotation. Store a hashed refresh-token identifier or token record server-side with user ID, expiry, and revocation status, so logout or compromise can invalidate it.
- [ ] Add `POST /auth/refresh` to validate a refresh token, revoke/rotate the previous record, and issue a new access-token/refresh-token pair.

**Commit checkpoint:** `feat(auth): issue and rotate JWT access and refresh tokens`

#### Spring Security Integration and API Migration

- [ ] Configure a stateless Spring Security filter chain: permit `/auth/register`, `/auth/login`, and `/auth/refresh`; require authentication for all habit, entry, statistics, and search endpoints.
- [ ] Add a JWT authentication filter that reads the Bearer token, verifies it, creates an authenticated principal, and stores it in `SecurityContext`.
- [ ] Extract `userId` exclusively from the authenticated principal or `SecurityContext`; remove the Phase 1 assumed/header-provided user ID from controllers, request DTOs, and service interfaces.
- [ ] Update every MongoDB query and Elasticsearch query to scope results and writes to the authenticated `userId`.
- [ ] Return consistent `401 Unauthorized` responses for missing, expired, malformed, or invalid tokens, and `403 Forbidden` only for authenticated users lacking permission.
- [ ] Extend the `.http` file with registration, login, refresh, authenticated CRUD, cross-user isolation, expired token, invalid token, and revoked refresh-token scenarios.

**Commit checkpoint:** `feat(security): secure modules with JWT user context`

#### Basic JWT Practices

- [ ] Use a short access-token lifetime (for example, 10–30 minutes) and a longer but finite refresh-token lifetime appropriate for a learning project.
- [ ] Treat refresh tokens as credentials: transmit them only over HTTPS outside local development, do not log them, and revoke them on logout or suspected compromise.
- [ ] Add a short README section covering local secret setup, token lifetime configuration, and the difference between access and refresh tokens.

**Commit checkpoint:** `docs(auth): document JWT secrets, lifetimes, and refresh-token handling`

---

## 5. Possible Extensions (for deeper exploration)

### Data extensions

- **Track by time rather than by day:** Replace `daily_entries` with `mood_logs` containing detailed timestamps. Learn time-series schema design and consider MongoDB Time Series Collections.
- **Flexible habit targets:** Track quantities instead of only done/not done—for example, “drink water: 6/8 glasses.” This explores semi-structured data modeling.
- **Roles and authorization:** Add roles such as `USER` and `ADMIN` only after the single-user ownership model is complete.

### MongoDB feature extensions

- **Change Streams for notifications:** Reuse the CDC foundation to send a notification after a new entry is indexed.
- **TTL Index:** Automatically remove unfinished draft entries after a defined number of days.
- **Text Search Index:** Search full text in mood and habit notes.
- **`$lookup`:** If `habits` and `daily_entries` are separated completely, use `$lookup` to join them and compare that experience with embedding.
- **Schema Validation:** Use MongoDB `$jsonSchema` to enforce document structure and compare it with application-level Spring `@Valid` validation.

### Architecture extensions

- Apply Clean Architecture layers (`domain` / `application` / `infrastructure`) to observe how a document database changes the persistence layer compared with a relational database.
- Implement a filterable entry-list feature twice—once with a repository query and once with a MongoDB aggregation—to compare the modeling trade-offs.

---

## 6. Learning Notes

These are the key differences to take away after completing the project:

| Aspect                   | Relational Database                               | Moodly (MongoDB)                                                  |
|--------------------------|---------------------------------------------------|-------------------------------------------------------------------|
| Schema                   | Fixed; changes require migrations.                | Flexible; each document may differ.                               |
| Data relationships       | Joins through foreign keys.                       | Embedding through nested arrays and objects.                      |
| Statistical calculations | SQL `GROUP BY`, `JOIN`.                           | Aggregation Pipeline (`$group`, `$unwind`, `$match`).             |
| Best fit                 | Clearly structured data and complex transactions. | Semi-structured data, read-heavy workloads, and flexible schemas. |
