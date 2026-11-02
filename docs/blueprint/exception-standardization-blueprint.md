# Moodly Exception Standard

**Status:** Official guidance for Moodly backend. This is the only exception blueprint. The module-code migration changes public `error.code` values; review client handling before merge. Existing HTTP statuses and safe messages remain stable unless a specific change is documented.

## 1. Boundaries and response contract

Normal API failures use `ApiResponse.error(ApiError)` with a stable `status`, `code`, safe `message`, `path`, validation `errors`, and `requestId`; the envelope also includes `timestamp`. Spring Security writes the same shape before MVC. Actuator health has a separate operational contract.

`ErrorCode` owns the public code, default message, and HTTP status. `ApplicationException` stores an `ErrorCode`, an optional safe message, and an optional cause. Its status derives from the code. Client code should branch on `error.code`, never on message text. Codes are unique across all enums and must never be reused for a different condition.

| Failure | Decision point | Public outcome |
| --- | --- | --- |
| Malformed JSON, missing or mistyped parameters, Bean Validation, security filter denial | Spring MVC, Security, or `GlobalExceptionHandler` | Shared `GlobalErrorCode` |
| Expected business or module-specific request failure | Controller, service, or adapter that understands the condition | Module `ErrorCode` through `ApplicationException` |
| Unknown persistence collision, broken invariant, programming or library defect | Nearest system boundary | Generic 500 `INTERNAL_SERVER_ERROR`; log a compact cause summary and application location |
| Scheduled job or change-stream callback failure | Job/listener retry, dead-letter, and health policy | No HTTP response unless an API explicitly invokes the operation |

Controllers may validate transport input. Services decide ownership, state, and persistence outcomes. Repositories return data or native persistence exceptions; they do not construct HTTP errors. `GlobalExceptionHandler` handles known MVC failures, all `ApplicationException` instances, and a generic fallback. Do not add a blanket 4xx mapping for `IllegalArgumentException`, `IllegalStateException`, `NoSuchElementException`, Mongo exceptions, or third-party exceptions.

## 2. Public code ownership

Each module owns codes for its **expected application failures**. Shared framework and authentication-filter responses keep global codes. A code belongs to the domain that rejects the operation, even when another module exposes the endpoint; an entry log for an unknown habit therefore returns `HABIT_NOT_FOUND`. A module without an expected failure needs no empty enum or exception class.

| Owner | Public codes and conditions |
| --- | --- |
| Global | `VALIDATION_FAILED`, `INVALID_REQUEST`, `MISSING_REQUIRED_HEADER`, `METHOD_NOT_ALLOWED`, `UNSUPPORTED_MEDIA_TYPE`, `NOT_ACCEPTABLE`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`, `INTERNAL_SERVER_ERROR`; `DUPLICATE_RESOURCE` is reserved for legacy clients and is not emitted by a generic Mongo handler. |
| Auth/avatar | `AVATAR_CONTENT_TYPE_UNSUPPORTED`, `AVATAR_FILE_TOO_LARGE`, `AVATAR_UPLOAD_NOT_FOUND`, `AVATAR_ASSET_INVALID`, `AVATAR_CONFLICT`, `AVATAR_SERVICE_UNAVAILABLE`. `AVATAR_VERSION_INVALID` guards direct service calls; HTTP `version <= 0` is rejected first by Bean Validation as global `VALIDATION_FAILED`. Security-filter 401/403 remains global. |
| Habits | `HABIT_NOT_FOUND` for missing or unowned habit, `HABIT_CONFLICT` for stale version, `HABIT_INVALID_STATUS` for an unsupported filter, `HABIT_ARCHIVED` for a new log on an archived habit. |
| Entries | `ENTRY_INVALID_RANGE` for application-level date and pagination rules. Bean Validation and malformed request syntax remain global. |
| Stats | `STATS_INVALID_PERIOD` for an unsupported mood trend period. Streak ownership failures use `HABIT_NOT_FOUND`. |
| Search/CDC | `SEARCH_INVALID_REQUEST`, `SEARCH_UNAVAILABLE`, `CDC_MAINTENANCE_FORBIDDEN`, `CDC_DEAD_LETTER_NOT_FOUND`. An E2E operation that depends on search may return `SEARCH_UNAVAILABLE`. |
| E2E | `E2E_RESET_FORBIDDEN` for an invalid reset token. Production does not expose this endpoint. |
| Dashboard | No expected module-specific failure at present; unexpected aggregation or persistence failure is 500. |

This code migration is intentional and requires consumer review before merge. Preserve each code's meaning, HTTP status, and safe message after publication. A future code replacement requires an explicit compatibility decision, HTTP tests, and an OpenAPI update.

## 3. Throwing and translating

1. Use Bean Validation for DTO constraints. Use a module exception factory for known business input, ownership, and state failures. The factory selects the module code and a bounded safe message. Do not copy raw user input, provider text, credentials, index names, or stack traces into public messages.
2. Return the same 404 and message for a missing resource and a resource owned by another user. Ownership checks should be shared by callers within the domain; do not reveal another user's identifier or existence.
3. Translate persistence errors only at an operation that can prove the business condition. A create-if-absent duplicate-key race can recover by reading the intended winner. An unmatched collision retains its cause and becomes 500. `DUPLICATE_RESOURCE` is not a catch-all 409.
4. Classify dependency failures narrowly. Transport or temporary search unavailability is 503 `SEARCH_UNAVAILABLE`. A search hit without `_source`, an unexpected client response, or a mapping defect is an internal 500. Keep causes for server diagnostics.

Operational error logs should occupy one line. Include a bounded exception class chain and application stack location, together with the request ID where available. Do not log raw exception messages or attach `Throwable` to routine request, cleanup, or CDC failure logs: provider messages can contain secrets and full stack traces obscure the event. Preserve the original cause in the exception object for programmatic recovery and debugging.
5. Catch only to recover, retry, compensate, or add useful context. Restore the interrupt flag after `InterruptedException`. A primary operation may succeed after secondary cleanup fails only if a durable retry record exists.
6. Validate configuration at startup. For example, CDC requires `max-attempts >= 1` and `initial-backoff-ms >= 0`; these violations are internal `IllegalArgumentException`s outside the HTTP contract.

## 4. Checked and unchecked exceptions

Moodly application and domain exceptions are **unchecked**. `ApplicationException extends RuntimeException` is the sole base for expected API failures. Do not add a parallel checked application hierarchy. This matches service signatures and Spring's default transaction rollback behavior for unchecked exceptions.

JDK and library checked exceptions, including `IOException` and `InterruptedException`, remain checked at low-level operations. Keep `throws` only when the immediate caller decides recovery or retry; otherwise translate at the infrastructure boundary, preserving the cause. A checked exception crossing a transaction boundary needs an explicit rollback review. Never catch `Error` as an application failure.

All concrete exception classes are `final` leaves. Add a non-final intermediate class only for real shared behavior needed by multiple subclasses. `final` is an implementation rule, not part of the HTTP contract.

```text
RuntimeException
├── ApplicationException (abstract; expected public API failure)
│   ├── auth: AvatarException, AvatarServiceUnavailableException
│   ├── habits: HabitException (including archived())
│   ├── entries: EntryException
│   ├── stats: StatsException
│   ├── search: SearchException; SearchInfrastructureUnavailableException
│   ├── e2e: E2eException
│   └── shared legacy/common: ConflictException, ForbiddenException,
│       InvalidRequestException, ResourceNotFoundException
├── IllegalStateException (internal): AvatarAssetDeletionException,
│   AvatarProfileMissingException, CdcReplayException
└── IllegalArgumentException (configuration): Auth0HttpConfigurationException;
    CDC retry property validation
```

## 5. API and operational decisions

- A new habit log after archive returns 409 `HABIT_ARCHIVED`; correction of an existing log remains allowed. A request overlapping archive observes the state at validation time. Requests begun after archive finishes see the archived state.
- Known stale habit versions and optimistic-lock failures at habit writes use `HABIT_CONFLICT`. The service retains the previous 409 message for a repository race. Spring's shared optimistic-lock handler remains a fallback for failures outside a service-specific translation.
- Unexpected Mongo uniqueness failures are 500, including a create race that cannot find its intended winner. Only a proven client-actionable conflict deserves a module 409.
- Search hit missing `_source` is a 500 defect or index inconsistency. The adapter logs the hit ID for an operator to inspect or rebuild the index; it never logs the user's query or returns the ID to the client. Classified temporary search transport failures remain 503. An unreadable local index mapping is an internal unchecked failure (500 at an API boundary); the mapping is read before an existing index is deleted. CDC invalid retry settings fail startup before any delivery attempt, and an impossible retry fallthrough is an internal invariant failure rather than an I/O failure.
- CDC listeners, cleanup jobs, and security filters are outside MVC advice. Their local retry, durable record, health, and envelope rules remain explicit.

## 6. Verification and change procedure

Inventory every throw and catch in the affected module before changing a handler. Test status, code, safe message, and envelope for representative framework and module failures; test that unexpected `IllegalArgumentException` and unclassified persistence errors remain 500. Include ownership, concurrency, retry/dead-letter, and success paths where relevant. Regenerate `docs/api/moodly-openapi.json` whenever a public response changes and review its diff. Run the full Maven verification with Docker before push.
