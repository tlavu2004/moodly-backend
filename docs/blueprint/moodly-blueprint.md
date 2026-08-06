# Moodly — Habit & Mood Tracker

> A small application for learning through a practical problem: tracking daily habits and moods.

---

## 1. Overview

**Application name:** `Moodly`  
**Goal:** Learn MongoDB, Change Data Capture (CDC), Elasticsearch/OpenSearch, Auth0-backed JWT authentication, and Cloudinary avatar delivery through a small deployed demo. Endpoints are exercised through a `.http` file; a minimal frontend is optional.
**Estimated duration:** 3 evenings—one evening for each learning phase.  
**Suggested stack:** A modular-monolith Spring Boot application + Spring Data MongoDB + Elasticsearch/OpenSearch + Spring Security. For the deployed demo use Vercel (optional frontend), Render (backend and CDC listener), MongoDB Atlas Free, Bonsai Free Sandbox, Auth0 Free, and Cloudinary Free. One deployable owns all backend modules; no microservices are required.

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
└── feature/auth0-cloudinary       # Phase 3; starts from updated main
```

- [ ] Create `feature/mongodb-core` from `main`; complete Phase 1 plus its tests, then Squash Merge as `feat(mongodb): add Moodly MongoDB core`.
- [ ] Create `feature/cdc-elasticsearch` from the updated `main`; complete Phase 2 plus resilience/search tests, then Squash Merge as `feat(search): add CDC-backed Elasticsearch search`.
- [ ] Create `feature/auth0-cloudinary` from the updated `main`; complete Phase 3 plus authentication/isolation and avatar-storage tests, then Squash Merge as `feat(auth): add Auth0 authentication and Cloudinary avatars`.

This leaves `main` with five meaningful commits: the blueprint, the initial project skeleton, then one squashed commit for each phase. Use merge commits instead only if preserving every intermediate checkpoint and its detailed development history matters more than keeping `main` concise.

### Modular Monolith Structure

Keep one Spring Boot application and one deployment unit. Separate code by business capability rather than by global technical layers, so each module owns its API, application logic, domain model, and persistence adapters where useful.

```text
com/tlavu/moodly
├── modules/
│   ├── auth/       # Auth0 identity integration and Spring Security resource-server integration
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

Application-owned profile data linked to an Auth0 identity. Credentials, sessions, and refresh tokens are owned by Auth0 and are never stored in Moodly.

```javascript
const user = {
  _id: ObjectId("..."),
  auth0Subject: "auth0|2b8d5c6f-...", // Auth0 JWT `sub`; unique
  email: "user@example.com",
  avatarPublicId: "moodly/avatars/2b8d5c6f-.../b3e1", // Cloudinary public ID; nullable
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

| Method  | Endpoint                        | Description                                                                                                         |
|---------|---------------------------------|---------------------------------------------------------------------------------------------------------------------|
| `POST`  | `/habits`                       | Create a habit.                                                                                                     |
| `GET`   | `/habits`                       | Get active habits.                                                                                                  |
| `PATCH` | `/entries/today`                | Mark or unmark one habit for today (upsert).                                                                        |
| `PUT`   | `/entries/today/mood`           | Record today's mood.                                                                                                |
| `GET`   | `/entries?from=&to=`            | Get entries within a date range.                                                                                    |
| `GET`   | `/habits/{id}/streak`           | Calculate a habit's current streak.                                                                                 |
| `GET`   | `/stats/mood-trend?period=week` | Get the weekly mood trend.                                                                                          |
| `GET`   | `/stats/most-missed-habits`     | Get the most frequently missed habits.                                                                              |
| `GET`   | `/entries/search?q=&from=&to=`  | Search indexed mood notes, habit notes, and mood tags (introduced in Phase 2).                                      |
| `POST`  | `/me/avatar/upload-signature`   | Create short-lived signed Cloudinary upload parameters for the authenticated user's avatar (introduced in Phase 3). |
| `GET`   | `/me/avatar`                    | Get authenticated user's avatar metadata and delivery URL (introduced in Phase 3).                                  |

All successful API responses use the same envelope: `{ "success": true, "data": ..., "timestamp": ... }`. Failed responses set `success` to `false` and provide details in `error` instead of `data`.

---

## 4. Detailed Implementation Checklist

### Testing Cadence

Complete and test one phase before starting the next phase. Keep tests in the same phase branch as the implementation and include them in that phase's Squash Merge; do not postpone all testing until Phase 3.

| Phase                          | Test immediately after                        | Minimum verification                                                                                                                      |
|--------------------------------|-----------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| Phase 1 — MongoDB Core         | Each CRUD, aggregation, and streak slice.     | `.http` scenarios, `mongosh` pipeline checks, validation/error cases, and focused automated tests for important application logic.        |
| Phase 2 — CDC Search           | The Change Stream listener and search API.    | Insert/update/delete synchronisation, duplicate delivery, retry/DLQ, listener restart, Elasticsearch outage, reindex, and user filtering. |
| Phase 3 — Auth0 and Cloudinary | The `SecurityContext` and avatar integration. | Valid/invalid Auth0 tokens, cross-user isolation across MongoDB and Elasticsearch, signed avatar upload, delivery, and asset ownership.   |

**Per-phase loop:** implement one coherent slice → verify manually with `.http` or `mongosh` → add focused automated tests → run the phase verification scenarios → commit checkpoints → Squash Merge the completed phase.

### Project Setup & Status (Cross-Phase Track)

This track applies to the whole project rather than to Phase 1 alone. Update it whenever a foundational capability becomes available.

**Current setup status (checked 2026-08-03):** The Spring Boot skeleton, Maven files, application entry point, and basic context test exist. MongoDB is connected and verified locally, and Phase 1 feature modules have started.

| Area                                        | Status            | Evidence / next step                                                          |
|---------------------------------------------|-------------------|-------------------------------------------------------------------------------|
| Spring Boot application skeleton            | `[x]` Setup       | Create the Spring Boot application structure.                                 |
| MongoDB dependency                          | `[x]` Setup       | Add the MongoDB starter dependency to `pom.xml`.                              |
| Web and validation dependencies             | `[x]` Setup       | Add WebMVC and Validation starters.                                           |
| Modular-monolith packages                   | `[x]` Setup       | Feature package markers and `docs/architecture.md` exist.                     |
| MongoDB Docker/replica set                  | `[x]` Verified    | `mongo:8.3.7` and idempotent `mongodb-init` are running successfully.         |
| MongoDB connection                          | `[x]` Verified    | `mongosh` reports `rs0` with one `PRIMARY` member.                            |
| Domain models and repositories              | `[ ]` Not started | Implement Phase 1 persistence.                                                |
| Habit/entry APIs and `.http` tests          | `[ ]` Not started | Implement and exercise the core endpoints.                                    |
| Aggregations, streak, and statistics        | `[ ]` Not started | Implement Phase 1 stats.                                                      |
| Elasticsearch and CDC                       | `[~]` In progress | Elasticsearch infrastructure and index mapping are ready; implement CDC next. |
| Auth0 authentication and Cloudinary avatars | `[ ]` Not started | Implement Phase 3 and deploy the demo after the core APIs exist.              |

### Phase 1 — MongoDB Core (Evening 1)

#### Setup (30 minutes)

- [x] Create one Spring Boot application.
- [x] Add the MongoDB starter dependency to `pom.xml`.
- [x] Create the initial feature-module package structure (`auth`, `habits`, `entries`, `stats`, `search`, `cdc`, and `shared`) inside the single Spring Boot application.
- [x] Document explicit module boundaries in `docs/architecture.md`; enforce them with an architecture test once controllers and repositories are added.
- [x] Define reproducible local and test MongoDB replica-set Compose configurations, using `.env.local` and `.env.test` respectively.
- [x] Configure the shared `application.yaml`, `application-local.yaml`, and `application-test.yaml` profiles. Maven integration tests use a Testcontainers-managed replica set rather than either Compose database.
- [x] Verify the connection through `mongosh`: MongoDB is healthy and replica set `rs0` reports one `PRIMARY` member.

> [!Note]
> `mongod --replSet rs0` enables replica-set mode but does not initialize a replica set by itself. `rs.initiate()` creates the single-node replica-set configuration required by MongoDB Change Streams in Phase 2; each `mongodb-init` service checks the status first so repeated Compose runs remain safe and idempotent. Maven integration tests use Testcontainers with the same pinned MongoDB image and a single-node replica set, keeping CI independent of either Compose environment.

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
- [x] Write `docs/testing/moodly.http` to exercise all endpoints above with named variables and repeatable request scenarios.

**Commit checkpoint:** `feat(entries): add DailyEntry service and controller with habit and mood management`

#### Aggregation Pipeline (60–90 minutes; core learning section)

- [x] Implement `GET /stats/mood-trend?period=week` using `$dateTrunc`, `$group`, and `$avg`.
- [x] Implement `GET /stats/most-missed-habits` using `$unwind`, `$match`, `$group`, and `$sort`.
- [x] Implement `GET /habits/{id}/streak` by loading entries in descending date order and calculating the streak in the application layer. MongoDB aggregation is not a particularly clear fit for consecutive-day streaks.
- [x] Test every pipeline directly in DataGrip/DBeaver and understand each stage's output.

#### Run the Pipelines in DataGrip or DBeaver

Connect either MongoDB client to the local replica set with this connection string:

```text
mongodb://localhost:27017/moodly?replicaSet=rs0
```

- **DataGrip:** create a MongoDB data source, paste the connection string, test the connection, then open a MongoDB query console for the `moodly` database.
- **DBeaver:** create a new MongoDB connection, use host `localhost`, port `27017`, database `moodly`, and set the replica-set name to `rs0` if the driver exposes that option. Open the MongoDB shell/editor after the connection succeeds.
- Run the mood-trend pipeline one stage at a time: first `$match`, then add `$project` with `$dateTrunc`, then `$group`/`$avg`, and finally `$sort`. Observe each intermediate result before adding the next stage.
- Run the most-missed pipeline one stage at a time: `$match`, `$unwind`, `$match` for `habits.done: false`, `$group`, then `$sort`.
- Streak is calculated in Java rather than an aggregation pipeline. In the console, inspect its input by running a `find` for the user and sorting `date: -1`; verify that a missing day or `done: false` should break the streak.

Use a dedicated test user (for example, `user_pipeline_demo`) and seed a few entries with mood scores plus both completed and missed habit logs. Delete only that test user's entries after the exercise.

#### Guided Pipeline Test

- **Step 1 — Seed isolated demo data.** In the MongoDB console, run the following once. It creates two weeks of mood data plus both completed and missed habits for `user_pipeline_demo`.

```javascript
const userId = "user_pipeline_demo";

// Seed three daily entries for one isolated demo user.
db.daily_entries.insertMany([
  {
    userId,
    date: ISODate("2026-07-27T00:00:00Z"),
    mood: { score: 3, tags: ["calm"], note: "First week" },
    habits: [
      { habitId: "exercise", done: true, note: null },
      { habitId: "reading", done: false, note: null }
    ]
  },
  {
    userId,
    date: ISODate("2026-07-28T00:00:00Z"),
    mood: { score: 5, tags: ["productive"], note: "Good day" },
    habits: [
      { habitId: "exercise", done: true, note: null },
      { habitId: "reading", done: false, note: null }
    ]
  },
  {
    userId,
    date: ISODate("2026-08-03T00:00:00Z"),
    mood: { score: 2, tags: ["tired"], note: "New week" },
    habits: [
      { habitId: "exercise", done: false, note: null }
    ]
  }
]);
```

- **Step 2 — Run and inspect the mood-trend stages.** Start with `$match`, then add `$project`/`$dateTrunc`, then `$group`/`$avg`, and finally `$sort`. Run each block separately and inspect its output before moving to the next stage.

```javascript
// Stage 1: keep only this user's entries that contain a mood score.
db.daily_entries.aggregate([
    {
        $match: {
            userId,
            "mood.score": { $exists: true }
        }
    }
]);

// Stage 2: project the score and calculate the Monday of its week.
db.daily_entries.aggregate([
    {
        $match: {
            userId,
            "mood.score": { $exists: true }
        }
    },
    {
        $project: {
            _id: 0,
            date: 1,
            score: "$mood.score",
            weekStart: {
                $dateTrunc: {
                    date: "$date",
                    unit: "week",
                    startOfWeek: "monday",
                    timezone: "Asia/Ho_Chi_Minh"
                }
            }
        }
    }
]);

// Stage 3: group by week and calculate average score and entry count.
db.daily_entries.aggregate([
    {
        $match: {
            userId,
            "mood.score": { $exists: true }
        }
    },
    {
        $group: {
            _id: {
                $dateTrunc: {
                    date: "$date",
                    unit: "week",
                    startOfWeek: "monday",
                    timezone: "Asia/Ho_Chi_Minh"
                }
            },
            averageScore: { $avg: "$mood.score" },
            entryCount: { $sum: 1 }
        }
    },
    { $sort: { _id: 1 } }
]);
```

- **Step 3 — Run and inspect the most-missed-habits stages.** Observe how `$unwind` turns each array element into a separate document, then see how the false entries are grouped and sorted.

```javascript
// Stage 1: keep all entries for this user.
db.daily_entries.aggregate([
    { $match: { userId } }
]);

// Stage 2: expand the habits array into one result per habit log.
db.daily_entries.aggregate([
    { $match: { userId } },
    { $unwind: "$habits" }
]);

// Stage 3: keep only missed habits.
db.daily_entries.aggregate([
    { $match: { userId } },
    { $unwind: "$habits" },
    { $match: { "habits.done": false } }
]);

// Stage 4: count misses by habit ID.
db.daily_entries.aggregate([
    { $match: { userId } },
    { $unwind: "$habits" },
    { $match: { "habits.done": false } },
    {
        $group: {
            _id: "$habits.habitId",
            missedCount: { $sum: 1 }
        }
    }
]);

// Stage 5: show the most frequently missed habits first.
db.daily_entries.aggregate([
    { $match: { userId } },
    { $unwind: "$habits" },
    { $match: { "habits.done": false } },
    {
        $group: {
            _id: "$habits.habitId",
            missedCount: { $sum: 1 }
        }
    },
    { $sort: { missedCount: -1 } }
]);
```

- **Step 4 — Inspect streak input.** Streak is calculated in Java rather than an aggregation pipeline, so inspect the entries in descending date order:

```javascript
// The application reads this order and checks consecutive completed days.
db.daily_entries.find(
    {
        userId,
        date: { $lte: new Date() }
    },
    {
        _id: 0,
        date: 1,
        habits: 1
    }
).sort({ date: -1 });
```

Verify that a missing day, a missing habit log, or `done: false` breaks the streak. After observing all outputs, clean up only the demo user's data:

```javascript
db.daily_entries.deleteMany({ userId: "user_pipeline_demo" });
```

**Commit checkpoint:** `feat(stats): add mood trend, missed habit, and streak statistics`

#### Polish (optional, if time remains)

- [x] Add basic validation: mood score must be 1–5; date ranges must not be reversed or in the future.
- [x] Add a minimal `GlobalExceptionHandler`.

**Commit checkpoint:** `feat(validation): add request validation and global exception handling`

- [x] Write a concise README explaining how to run the project.

**Commit checkpoint (only if implemented):** `docs(readme): document local setup and project overview`

#### Automated Test Coverage

- [x] Add unit tests for `HabitService`, `DailyEntryService`, and `EntryReadService`, covering creation, updates, timestamps, and repository delegation.
- [x] Add unit tests for `StatsService`, covering aggregation delegation and streak boundaries caused by an incomplete or missing habit log, a missing date, and no entries.
- [x] Keep repository and aggregation integration tests against a Testcontainers-managed MongoDB replica set.
- [x] Test `findByUserIdAndDateRange` directly against MongoDB for inclusive date boundaries, ascending sort order, and user isolation.
- [x] Add Phase 1 API integration tests covering the CRUD/statistics happy path, the standard `ApiResponse` envelope, and validation/error contracts: missing user header, invalid habit and mood payloads, malformed JSON, invalid date ranges, and an unsupported statistics period.
- [x] Run the complete suite through `make test` with Docker Desktop running.

**Commit checkpoint:** `test(mongo-core): enhance integration tests for MongoDB core services and API validation`

### Phase 2 — Elasticsearch via MongoDB Change Streams (CDC Pattern) (Evening 2)

Elasticsearch is a derived search index only. MongoDB remains the source of truth; the request/response flow must not write directly to Elasticsearch.

#### Infrastructure and Index Setup

- [x] Update Docker Compose to run MongoDB as a single-node replica set, because Change Streams require a replica set or sharded cluster. Initialize the replica set and verify it with `rs.status()`.
- [x] Add a pinned Elasticsearch Docker image and a persistent development volume. Configure a single-node development cluster and document its local port.
- [ ] Create a Bonsai Free Sandbox Elasticsearch/OpenSearch cluster for the deployed demo. Store its HTTPS endpoint and credentials only in the Render environment; retain local Docker Elasticsearch for development and Testcontainers for tests.
- [ ] Configure a `production` profile to use Bonsai. Verify index mappings and a full reindex against that remote cluster before deploying the backend.
- [x] Add the Elasticsearch Java client or Spring Data Elasticsearch dependency and configure the client in `application.yaml`.
- [x] Create a `daily_entries_search` index with an explicit mapping. Use the MongoDB entry `_id` as the Elasticsearch document ID.
- [x] Map `userId` and `date` as exact/filterable fields, `mood.score` as a numeric field, and `mood.note`, `habits.note`, and `mood.tags` as searchable fields. Add keyword subfields only where filtering or aggregation is needed.
- [x] Create the index and mapping through an idempotent startup component or a versioned setup script; verify them with Elasticsearch's index and mapping APIs.

**Commit checkpoint:** `feat(search): integrate Elasticsearch for daily entry indexing and setup`

#### Change Stream Synchronisation Service

- [x] Create a dedicated internal `cdc` module responsible only for synchronising `daily_entries` into Elasticsearch; it is part of the same deployable modular monolith, not an independently deployed service.
- [x] Start a Change Stream listener using Spring Data MongoDB (for example, `MongoMessageListenerContainer`) against the `daily_entries` collection.
- [x] Configure `fullDocument: UPDATE_LOOKUP` so update events can be indexed from the complete current MongoDB document.
- [x] Handle `insert`, `update`, and `replace` by transforming the full document into one search document and indexing it with a deterministic ID. Handle `delete` by removing the matching Elasticsearch document.
- [x] Keep the transformation deliberately denormalized: include searchable mood content, habit notes, tags, date, and `userId` in the search document. Do not make Elasticsearch the data source for entry details.
- [x] Persist the last successfully processed resume token in a small MongoDB collection. On restart, resume from that token; if it is no longer valid, log the condition and run a controlled reindex.
- [x] Build an explicit reindex command or protected maintenance endpoint that reads all MongoDB `daily_entries` in batches and rebuilds the Elasticsearch index.

**Commit checkpoint:** `feat(cdc): implement Change Data Capture for daily entries with Elasticsearch integration`

#### Failure Handling and Verification

- [x] Make Elasticsearch writes idempotent so a duplicate Change Stream delivery is safe.
- [x] Retry transient Elasticsearch failures with bounded exponential backoff and clear structured logs.
- [x] After retries are exhausted, save the event metadata, error, attempt count, and payload or document ID to a temporary MongoDB dead-letter collection. Provide a small replay mechanism after the fault is resolved.
- [x] Monitor listener health and failed-event count; fail or degrade clearly if the listener cannot be established.

**Commit checkpoint:** `feat(cdc): add retry mechanism, dead-letter handling, and health monitoring for CDC events`

- [ ] Test insert, update, delete, listener restart, Elasticsearch outage, duplicate delivery, and reindex recovery using the `.http` file and Docker logs.
- [ ] In the deployed demo, verify the CDC listener resumes from its stored resume token after a Render restart or free-tier sleep; document that indexing can be delayed while the service is asleep and provide reindex as recovery.

**Commit checkpoint:** `test(cdc): cover change stream recovery and Elasticsearch failures`

#### Search API

- [x] Implement `GET /entries/search?q=&from=&to=` against Elasticsearch, with required `q` validation and optional date filters.
- [x] Filter every search query by the current user's `userId`. Until Phase 3 is complete, keep this temporary user-context mechanism isolated so it can be replaced by `SecurityContext`.
- [x] Return search-oriented fields (highlight/snippet, entry ID, date, matching content) and fetch MongoDB only when an authoritative full entry is required.
- [x] Document eventual consistency: an entry can be saved to MongoDB before it appears in search results.

**Commit checkpoint:** `feat(search): implement entry search API with Elasticsearch integration`

#### Phase 2 Test Plan

Run this test plan before considering Phase 2 complete. Automated tests must not share MongoDB databases, Elasticsearch indices, or users with a developer's local data. Give each test suite a unique user ID and clean up its MongoDB documents, resume tokens, dead letters, and Elasticsearch documents/index after execution.

##### Test Infrastructure

- [x] Add an Elasticsearch Testcontainers configuration pinned to the same `${ELASTICSEARCH_VERSION}` used by local Compose. Keep the existing MongoDB Testcontainer in replica-set mode because Change Streams are part of the test subject.
- [x] Keep one lightweight `application-test.yaml` with CDC disabled for Phase 1 tests; enable CDC/index bootstrap and test-only collection/index names through explicit properties on the CDC Testcontainers suite.
- [x] Make asynchronous assertions poll with a bounded timeout rather than `Thread.sleep`; report the last observed MongoDB/Elasticsearch state when the timeout expires.
- [x] Provide helpers to create a `DailyEntry`, wait for an Elasticsearch document to appear/disappear, wait for a dead letter, and clear the test index. Use a unique test user ID for every scenario.

##### Unit Tests

- [x] `DailyEntrySearchDocument`: verify the denormalized projection preserves `userId`, date, mood score, mood note, tags, and habit notes; the writer supplies the MongoDB entry ID as the deterministic Elasticsearch document ID. Cover absent mood, empty/null tag lists, and empty/null habit lists without producing a null collection unexpectedly.
- [x] `DailyEntrySearchWriter`: verify index and delete requests use the configured index name and the MongoDB entry ID as the deterministic Elasticsearch document ID.
- [x] `DailyEntrySearchIndexManager`: verify create-if-missing does not overwrite an existing index, creates a missing index from the configured mapping resource, and recreate deletes then creates the same configured index.
- [x] `CdcDeliveryService`: verify successful upsert/delete delivery, transient `IOException` and 429/5xx Elasticsearch failures retry up to the configured limit, and non-transient 4xx failures do not retry.
- [x] `CdcDeliveryService`: after exhausted retries, verify the dead letter stores event ID, operation, entry ID, serialized payload when applicable, error, configured attempt count, and failure time; verify the monitor records the failure.
- [x] `CdcDeliveryService` replay: verify a successful replay deletes the dead letter and updates the monitor; a failing replay retains the record, increments attempts, records the new error/time, and raises a clear exception.
- [x] Refactor the retry delay behind an injectable sleeper/backoff collaborator before asserting retry paths, so unit tests never wait for the real exponential-backoff duration.
- [x] `DailyEntryReindexService`: verify it recreates the index once, reads every repository page using the configured batch size, indexes every entry exactly once, and returns the total indexed count for zero, one, and multiple pages.
- [x] `DailyEntryChangeStreamListener`: verify insert/update/replace route to upsert, delete routes to delete, only a successfully handled event advances the résumé token, and an unsupported operation does not alter the token.
- [x] `DailyEntryChangeStreamListener`: verify a `ChangeStreamHistoryLost`/code 286 failure removes the stale token, invokes reindex, registers a fresh stream, and reports healthy again; other listener failures must remain visible as unhealthy and must not trigger reindex.
- [x] `EntrySearchService`: verify the generated query always filters by `userId`, applies each optional date bound correctly, uses the configured index, returns an empty highlight map when Elasticsearch omits highlights, and maps `IOException` to the expected unavailable error.
- [x] `EntrySearchController` web slice: cover successful parameter forwarding, blank `q`, reversed dates, and user-header forwarding/isolation.
- [x] `CdcMaintenanceController` web slice: cover valid maintenance key, missing/wrong key returning the standard `FORBIDDEN` envelope, reindex response count, and replay endpoint delegation.

##### Integration Tests — MongoDB + Elasticsearch Testcontainers

- [x] Startup/index contract: start the CDC profile, assert the configured index exists, and inspect its mapping to verify exact `userId`/date fields and searchable `mood.note`, `mood.tags`, and `habits.note` fields.
- [x] Insert synchronization: save a new `DailyEntry` to MongoDB and await one Elasticsearch document with the same ID and the expected denormalized content.
- [x] Update/replace synchronization: change mood text, tags, habit notes, and score; await the updated Elasticsearch document and assert stale searchable values are gone.
- [x] Delete synchronization: delete a MongoDB entry and await Elasticsearch returning no document for its ID.
- [x] Duplicate delivery: invoke delivery twice with the same event/document and assert one Elasticsearch document remains, no duplicate is possible, and no dead letter is created.
- [x] Resume token/restart: process an event, capture the persisted token, recreate or restart the listener, then create another entry; assert the second event is indexed once and the token advances without a full reindex.
- [x] Reindex recovery: seed multiple MongoDB pages, remove/corrupt the derived index, call reindex, and assert the recreated index has every source entry exactly once and the returned count matches MongoDB.
- [x] Elasticsearch outage and recovery (manual Docker verification): verified against the isolated `moodly-test` stack. MongoDB accepted entry `6a740b70fa2fa5cf568632f7` while Elasticsearch was stopped; CDC retried three times with 200/400 ms backoff and stored DLQ `6a740b71fa2fa5cf568632f8` with payload, error, attempts, and failure time. After Elasticsearch recovered, replay removed the DLQ and restored the Elasticsearch document with HTTP 200.
- [x] Expired resume token recovery: cover the listener's history-lost branch with a focused listener test; add a container-level scenario only if the MongoDB oplog can be deterministically advanced to invalidate a token without making the suite flaky.
- [x] Search API end-to-end: index entries for two users, query through `GET /entries/search`, and assert full-text matches/highlights, optional `from`/`to` boundaries, no cross-user result leakage, blank/reversed parameter errors, and eventual-consistency polling before assertions.

##### Manual Verification — `docs/testing/moodly.http` and Docker

- [x] Start Docker Desktop and run `make local-up`, `make local-replica-status`, and `make local-elasticsearch-status`; verified MongoDB `PRIMARY` and Elasticsearch `yellow` with one node.
- [x] Execute create/update requests from `moodly.http`, copy the returned entry ID into `cdcEntryId`, and inspect `GET /_doc/{{cdcEntryId}}`; verified entry `6a740fd7f1c8543eac980109` with the expected mood, tags, and habit note.
- [x] Delete the entry with the documented `mongosh` command and confirm Elasticsearch returns 404 for the document; verified for entry `6a740fd7f1c8543eac980109`.
- [x] Restart only the application while retaining MongoDB/Elasticsearch, create another entry, and confirm the persisted resume token allows the listener to continue without missing events; verified token advancement and Elasticsearch HTTP 200 for entry `6a741008ec629352bbfbf365`.
- [x] Stop Elasticsearch, make a MongoDB entry change, inspect retry/DLQ/Actuator logs, restore Elasticsearch, then replay the dead letter through the protected endpoint and confirm the document returns.
- [x] Call protected reindex with a valid key, verify its count against MongoDB, call it again, and confirm the operation remains duplicate-safe. Verified two successive re-indexes returned 2, matching MongoDB count 2; a wrong key returned HTTP 403.
- [x] Search with a matching term, no-match term, date boundaries, and a second `X-User-Id`; verified one matching result with `<em>manual</em>` highlight, zero no-match results, and zero cross-user results after bounded CDC polling.

##### Required Execution Order

1. Run fast unit and web-slice tests on every change.
2. Run the MongoDB + Elasticsearch Testcontainers suite for CDC/search changes and in CI.
3. Run the manual outage/restart/reindex scenarios against `moodly-local` before the Phase 2 squash merge.
4. Mark the existing Phase 2 verification checkbox complete only after the automated and manual sections above pass.

#### CDC Trade-offs

| Concern           | Change Streams / CDC                                                                                | Direct synchronous Elasticsearch write                                                  |
|-------------------|-----------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|
| Request latency   | MongoDB writes complete without waiting for Elasticsearch.                                          | Each write waits for both systems.                                                      |
| Consistency       | Eventual consistency; search may lag briefly.                                                       | Results can appear immediately when both writes succeed.                                |
| Failure isolation | Elasticsearch outages do not block core writes; retry and replay are required.                      | An outage can fail or delay the user request unless degraded behaviour is added.        |
| Complexity        | Requires a replica set, listener lifecycle, resume tokens, idempotency, retry, DLQ, and reindexing. | Simpler initially, but dual-write consistency becomes the application's responsibility. |
| Recovery          | Replay and full reindex can rebuild a derived index from MongoDB.                                   | Must reconcile partial dual writes and missed updates manually.                         |

### Phase 3 — Auth0 Authentication and Cloudinary Avatars (Evening 3)

Implement and verify the complete backend locally before building the frontend or deploying anything. Auth0 and Cloudinary remain hosted development services, but the Spring Boot API, MongoDB, Elasticsearch, CDC listener, and all automated/manual backend tests run locally. Auth0 owns credentials, sessions, token issuance, and refresh-token rotation; Moodly validates Auth0 access tokens and owns only app profile data and avatar metadata. Cloudinary stores, transforms, and delivers avatar images.

#### Local-First Service Configuration

- [ ] Create an Auth0 tenant, an API with a stable Moodly API identifier as its audience, and a local-development SPA client. Configure `http://localhost:<frontend-port>` as the allowed callback, logout, and web-origin URL. Add the deployed Vercel URL only after the frontend has been deployed.
- [ ] Create a Cloudinary Free account and a signed upload preset intended for avatars. Configure it to accept images only, use the `moodly/avatars` folder, and set a conservative maximum upload size. Keep the Cloudinary API secret only in local backend environment variables; never expose it to a browser or commit it.
- [ ] Extend `.env.local.example` with placeholders for the Auth0 issuer, API audience, Cloudinary cloud name, API key, API secret, upload preset, avatar folder, and local CORS origin. Add the corresponding values only to ignored `.env.local`.
- [ ] Add `application-local.yaml` bindings for Auth0, Cloudinary, and CORS so `make local-run` uses local MongoDB/Elasticsearch/CDC plus Auth0 and Cloudinary development credentials. Keep production bindings for a later `application-production.yaml`; do not add Render or Vercel configuration yet.

**Commit checkpoint:** `chore(local): configure Auth0 and Cloudinary development services`

#### Auth0 Identity and Spring Security

- [ ] Configure Spring Security as an OAuth 2.0 resource server. Verify Bearer JWT signatures with Auth0's JWKS and validate issuer, expiry, and the Moodly API audience.
- [ ] Map the Auth0 JWT `sub` to the application `userId`. On the first authenticated request, create a `users` profile document with a unique `auth0Subject`, normalized email when available, and timestamps; never create or store password hashes or refresh tokens.
- [ ] Require authentication for all habit, entry, statistics, search, and avatar endpoints. Do not expose `/auth/register`, `/auth/login`, or `/auth/refresh`; the frontend uses Auth0 Universal Login and refreshes through Auth0's supported client flow.
- [ ] Extract `userId` exclusively from the authenticated principal or `SecurityContext`; remove the Phase 1 assumed/header-provided user ID from controllers, request DTOs, and service interfaces.
- [ ] Update every MongoDB query and Elasticsearch query to scope results and writes to the authenticated `userId`.
- [ ] Return consistent `401 Unauthorized` responses for missing, expired, malformed, invalid, wrong-issuer, or wrong-audience tokens, and `403 Forbidden` only for authenticated users lacking permission.

**Commit checkpoint:** `feat(auth): secure modules with Auth0 JWTs`

#### Cloudinary Avatar Storage

- [ ] Add avatar metadata to the application profile: nullable `avatarPublicId`, version, content type, size, and updated timestamp. The client never supplies an arbitrary final public ID.
- [ ] Implement `POST /me/avatar/upload-signature`. Validate allowed image MIME types and a small maximum size, generate a `moodly/avatars/{authenticated-userId}/...` public ID, and return a short-lived Cloudinary signed-upload payload.
- [ ] Upload directly from the client to Cloudinary using the signed payload. Persist the confirmed public ID and version only after a successful upload; build the delivery URL from these values and apply a fixed safe avatar transformation (for example square crop and automatic format/quality).
- [ ] When replacing an avatar, delete the prior Cloudinary asset from the backend after the new upload is confirmed. Document cleanup for abandoned assets from failed client uploads.
- [ ] Ensure an authenticated user cannot obtain a signed payload for, overwrite, or delete another user's avatar asset.

**Commit checkpoint:** `feat(avatars): add Cloudinary-backed avatar uploads`

#### Local Backend Verification Before Frontend Work

- [ ] Extend the `.http` file with an Auth0 access-token acquisition note and authenticated CRUD, search, cross-user isolation, expired/invalid/wrong-issuer/wrong-audience token, and avatar signed-upload scenarios.
- [ ] Use two local Auth0 demo users to verify that neither local MongoDB-backed endpoints nor local Elasticsearch search reveals the other user's data, and neither user can perform Cloudinary asset-management operations for the other's avatar.
- [ ] Run the full local stack with `make local-up` and `make local-run`; verify authenticated CRUD, CDC indexing, search, avatar upload, avatar replacement/deletion, and error handling before beginning any frontend work.
- [ ] Do not start frontend deployment until the local backend verification checklist passes. The frontend's first responsibility is to complete Auth0 Universal Login and call these already-tested API endpoints.

**Commit checkpoint:** `test(auth): verify local Auth0, CDC, and Cloudinary flows`

#### Deployment Preparation After Local Verification

- [ ] Add `application-production.yaml` with environment-variable placeholders only. Keep `.env.local.example` and `.env.test.example` as local/test templates; do not create or commit `.env.production`.
- [ ] Configure Render environment variables for the Auth0 issuer, API audience, and Cloudinary cloud name, API key, API secret, upload preset, avatar folder, and production CORS origin. Configure Vercel later with only public Auth0 client settings.
- [ ] Add a deployment checklist covering Vercel, Render, Atlas, Bonsai, Auth0, and Cloudinary; do not put provider secrets in Compose files or source control.
- [ ] Document free-tier limitations: Render may sleep, so CDC indexing can pause until the backend wakes and resumes; Bonsai sandbox and provider quotas are demo-grade; no component has an HA or backup guarantee in this plan.

**Commit checkpoint:** `docs(deploy): document hosted demo workflow and limits`

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

---

## 7. Hosted Demo Deployment Runbook

Follow this order exactly. It avoids circular configuration: the backend needs database, search, auth, and media credentials before it can deploy; Auth0 and backend CORS need the final frontend URL after Vercel deploys it.

### 7.1 Prepare the repository and configuration

- [ ] Finish Phase 1–3 locally and run the automated tests. Confirm a local full reindex succeeds and the API can resume a MongoDB Change Stream from a saved resume token.
- [ ] Keep the existing `.env.local.example` and `.env.test.example` as the templates for their respective local environments. Add `src/main/resources/application-production.yaml` with environment-variable placeholders only; do **not** create or commit a `.env.production` file. Render Dashboard is the source of production variable values. The production profile needs at least:

   ```dotenv
   SPRING_PROFILES_ACTIVE=production
   MONGODB_URI=
   ELASTICSEARCH_URL=
   ELASTICSEARCH_USERNAME=
   ELASTICSEARCH_PASSWORD=
   AUTH0_ISSUER_URI=
   AUTH0_AUDIENCE=
   CLOUDINARY_CLOUD_NAME=
   CLOUDINARY_API_KEY=
   CLOUDINARY_API_SECRET=
   CLOUDINARY_UPLOAD_PRESET=
   APP_CORS_ALLOWED_ORIGINS=
   ```

- [ ] Ensure `.env.local`, `.env.test`, any untracked local production export, provider exports, Auth0 client secrets, Cloudinary API secrets, Atlas connection strings, and Bonsai credentials are ignored by Git. Commit `application-production.yaml` with placeholders and deployment documentation before creating cloud resources.

### 7.2 Create persistent data services first

- [ ] **Step 4 —** In MongoDB Atlas, create one Free cluster in a region close to Render. Create a least-privilege database user for Moodly and copy its application connection string into a password manager. Add Render's outbound access to the Atlas IP access list. If Render Free has no stable outbound IP, temporarily use `0.0.0.0/0` only for the demo and keep the database user's password strong; remove this rule when a stable IP is available.
- [ ] **Step 5 —** In Bonsai, create one Free Sandbox cluster compatible with the Elasticsearch/OpenSearch client version used by the backend. Create a restricted credential, record the HTTPS endpoint and credentials, then configure the backend's `production` profile locally and run a one-time index mapping/reindex verification.
- [ ] **Step 6 —** In Cloudinary, create a Free account and a **signed** upload preset. Configure it to accept only images, set the `moodly/avatars` folder, and set a conservative maximum upload size. Record the cloud name and API key; keep the API secret private to Render.

### 7.3 Configure authentication before deploying the backend

- [ ] **Step 7 —** In Auth0, create an API with a stable identifier such as `https://api.moodly.demo`; use this exact value as `AUTH0_AUDIENCE`. Do not use a temporary Render URL as the audience.
- [ ] **Step 8 —** Create an Auth0 application for the frontend (SPA for a browser-only frontend, or Regular Web Application when the frontend owns a server-side session). Enable the database connection needed for demo users. Leave callback/logout/origin URLs to update after Vercel has supplied its final URL.
- [ ] **Step 9 —** Record Auth0's issuer URL in the form `https://<tenant>.auth0.com/`. Configure the backend to validate this issuer and the API audience, not merely that a JWT has a valid signature.

### 7.4 Deploy the backend and initialize search

- [ ] **Step 10 —** Create a Render Web Service from the backend repository. Use the Maven build command required by the project and the production start command; do not run Keycloak, Garage, or a local Elasticsearch container on Render.
- [ ] **Step 11 —** Add the production variables referenced by `application-production.yaml` in section 7.1 to Render, using the values from Atlas, Bonsai, Auth0, and Cloudinary. Set `APP_CORS_ALLOWED_ORIGINS` temporarily to an empty or placeholder value until the Vercel URL exists; do not use `*` for an authenticated API.
- [ ] **Step 12 —** Deploy Render and record the public backend URL, for example `https://moodly-api.onrender.com`. Verify its health endpoint and a protected endpoint returns `401` without a token. Verify the application connects to Atlas and Bonsai from Render logs without printing credentials.
- [ ] **Step 13 —** Invoke the protected reindex/admin workflow through a controlled local operation, or start the approved reindex command once, so Bonsai has the current MongoDB documents. Confirm the index mapping, one search result, and the saved CDC resume token. Do not expose a public unauthenticated reindex endpoint.

### 7.5 Deploy the frontend, then close the configuration loop

- [ ] **Step 14 —** If the demo has a frontend, create a Vercel project from its repository. Add only browser-safe configuration: Auth0 domain, Auth0 client ID, Auth0 audience, and the Render API base URL. Never put Cloudinary API secret, Auth0 client secret, Atlas URI, or Bonsai credentials in Vercel.
- [ ] **Step 15 —** Deploy Vercel and record its production URL. In Auth0, add the exact HTTPS Vercel URL to **Allowed Callback URLs**, **Allowed Logout URLs**, and **Allowed Web Origins**. Add `http://localhost:<frontend-port>` separately for local development only.
- [ ] **Step 16 —** Update Render's `APP_CORS_ALLOWED_ORIGINS` to the exact Vercel production URL (and optionally the local frontend URL), then redeploy Render. Redeploy Vercel if its environment variables changed. If there is no frontend, skip this subsection and obtain a demo access token through Auth0's supported test/login flow before using the `.http` file.

### 7.6 Verify the complete public flow

- [ ] **Step 17 —** Register two separate demo users through Auth0 Universal Login. For each user, call one protected API endpoint and confirm that a profile is created with its `auth0Subject`.
- [ ] **Step 18 —** As user A, create a habit and entry, wait for or trigger the CDC catch-up, and verify the Bonsai-backed search returns only user A's document. Repeat as user B and verify neither MongoDB-backed endpoints nor search reveal user A's data.
- [ ] **Step 19 —** As user A, request `/me/avatar/upload-signature`, upload a permitted image directly to Cloudinary, save the returned asset metadata, and retrieve the avatar delivery URL. Replace it once and confirm the old Cloudinary asset is deleted. Repeat as user B and confirm asset-management requests cannot target user A's public ID.
- [ ] **Step 20 —** Test missing token, expired token, invalid signature, wrong issuer, and wrong audience: each must return `401`. Confirm an authenticated but unauthorized operation returns `403` only where an authorization rule exists.
- [ ] **Step 21 —** Restart the Render service or wait for a free-tier sleep/wake cycle, then make a writing operation and confirm the CDC listener resumes and Bonsai catches up. If it does not, run the restricted reindex workflow and investigate the stored resume token and dead-letter collection before declaring the demo ready.

### 7.7 Final safety checklist

- [ ] No secrets are committed, printed in logs, or sent to Vercel browser code.
- [ ] Atlas access is restricted as far as the Render plan permits.
- [ ] Auth0 callback, logout, and web-origin URLs contain only the Vercel production URL and intentional local URLs.
- [ ] Cloudinary uploads are signed; the API secret remains server-side.
- [ ] CORS allows only intended frontend origins.
- [ ] CDC resume, retry, dead-letter, and reindex paths have been exercised.
- [ ] Provider dashboards have usage alerts/budgets enabled where their free plans support them.
- [ ] The README states that this is a free-tier demo, so service sleep, quota limits, and provider policy changes can affect availability.
