# Moodly Exception Standard

**Status:** Official guidance for new Moodly backend work. Preserve published HTTP status, error code, and safe message until a separately reviewed API migration changes them.

This is the single Moodly exception standard. NovaCart was a design reference; its catalog, class hierarchy, and examples are not Moodly's API contract.

## 1. Failure categories and boundaries

| Category | Examples | Where to decide | Result |
| --- | --- | --- | --- |
| Request/framework failure | Validation, malformed JSON or parameters, unsupported method/media type | Spring MVC and `GlobalExceptionHandler` | Existing safe 4xx response |
| Expected application failure | Missing owned habit, stale version, invalid avatar, unavailable search dependency | Service or infrastructure adapter throws `ApplicationException` | Status, code, and safe message selected by `ErrorCode` |
| Unexpected failure | Defect, broken invariant, unknown unique constraint, unclassified library failure | Nearest system boundary | Generic 500; preserve diagnostic cause |

Controllers validate transport input and delegate business decisions. Services interpret domain and persistence outcomes. Repositories return data or native persistence exceptions; they do not build HTTP responses. MVC advice cannot catch failures in security filters, scheduled jobs, or change-stream callbacks. Those boundaries need their own response, retry, dead-letter, health, and logging policies.

## 2. Public HTTP contract

Normal API errors use `ApiResponse.error(ApiError)` with `status`, stable `code`, safe `message`, `path`, validation `errors`, and `requestId`; the envelope includes `timestamp`. Spring Security writes the same shape for failures before MVC. Actuator health has its own operational contract.

`ErrorCode` is the single source for machine code, default message, and HTTP status. `ApplicationException` stores the code and optional safe message and cause; its status derives from the code. Do not repeat status in constructors or call sites. Clients branch on code, not message text.

| HTTP | Current codes |
| --- | --- |
| 400 | `VALIDATION_FAILED`, `INVALID_REQUEST`, `MISSING_REQUIRED_HEADER`, `AVATAR_CONTENT_TYPE_UNSUPPORTED`, `AVATAR_FILE_TOO_LARGE`, `AVATAR_UPLOAD_NOT_FOUND`, `AVATAR_ASSET_INVALID` |
| 401 / 403 / 404 | `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND` |
| 405 / 406 / 415 | `METHOD_NOT_ALLOWED`, `NOT_ACCEPTABLE`, `UNSUPPORTED_MEDIA_TYPE` |
| 409 | `CONFLICT`, `HABIT_ARCHIVED`; `DUPLICATE_RESOURCE` remains reserved and the generic handler does not emit it |
| 503 | `SEARCH_UNAVAILABLE`, `AVATAR_SERVICE_UNAVAILABLE` |
| 500 | `INTERNAL_SERVER_ERROR` |

Codes must be unique across enums, stable after publication, and never reused for another meaning. Add a module code only when clients need to distinguish its recovery action from other failures with the same status. Use a named exception or factory when it fixes code, safe message, and cause together. Do not create one exception class per endpoint or module merely for symmetry. Keep shared meanings in `GlobalErrorCode`.

## 3. Throwing and translating

1. Reject known input failures with Bean Validation or `InvalidRequestException`. Never map every `IllegalArgumentException` to 400; libraries and programming errors throw it too.
2. Use `ResourceNotFoundException` for a missing resource and for another user's resource when the API must not reveal its existence. Use `ConflictException` or the existing optimistic-lock mapping for a real state/version conflict.
3. Translate persistence failures only when the exact operation and business condition are known. After a duplicate-key race in create-if-absent, read the intended winner. If there is no matching winner, retain the original cause and treat the collision as internal. Unclassified `DuplicateKeyException` reaches generic 500; it does not justify a blanket 409 handler.
4. At external adapters, classify expected dependency failures narrowly. Search transport/unavailability becomes `SEARCH_UNAVAILABLE` 503; unexpected client or mapping defects remain 500. Preserve the cause, but never copy a provider message, query, credential, or raw user input into the public message.
5. Domain code may raise a plain exception for a domain invariant without importing Spring HTTP or API DTOs. Application code translates an expected failure to `ApplicationException` before an API boundary. If both layers have exception classes, give them distinct names.
6. Catch only to recover, retry, compensate, add useful context, or translate a known condition. Do not swallow a failure and report false success. A primary operation may succeed after a secondary cleanup failure only when the primary state is persisted and cleanup has a durable retry record. Restore the interrupt flag after `InterruptedException`.

Unexpected `IllegalStateException`, `NoSuchElementException`, and other runtime failures need no blanket 4xx handler. `GlobalExceptionHandler` handles known MVC failures, `ApplicationException`, and a final `Exception` fallback. Security filters and background processing use their own boundaries.

## 4. Checked versus unchecked exception policy

**Moodly application and domain exceptions are unchecked (`RuntimeException`).** `ApplicationException` is the only base for expected application failures exposed through APIs. Do not create a parallel checked application hierarchy. This matches existing service signatures and Spring's default rollback on unchecked failures.

JDK and library checked exceptions, such as `IOException` and `InterruptedException`, remain checked at low-level operations. Keep `throws` when the immediate caller genuinely chooses retry or recovery; otherwise translate at the infrastructure boundary to an appropriate unchecked application or internal exception, retaining the cause. Do not turn every checked exception into 4xx. If a checked exception escapes a transaction, review rollback configuration explicitly. Do not catch `Error` as an application failure.

Use `final` for a concrete exception only when extension is intentionally prohibited. Uniform `final` modifiers are not part of the error contract.

## 5. Module rules

| Area | Current behavior and rule |
| --- | --- |
| `auth` / avatar | Security owns the 401/403 envelope. Avatar-specific codes live in `AuthErrorCode`. Cleanup exceptions are internal to durable jobs; they do not need public codes. |
| `habits` | Missing/unowned habit is 404 `NOT_FOUND`; stale version is 409 `CONFLICT`; bad status filter is 400 `INVALID_REQUEST`. |
| `entries` | Bad date/pagination is 400 `INVALID_REQUEST`; DTO violations are 400 `VALIDATION_FAILED`. A new log for an archived habit is 409 `HABIT_ARCHIVED`; an existing log remains editable. |
| `stats` / `dashboard` | Streak for missing/unowned habit is 404 `NOT_FOUND`; a valid habit with no log has streak zero. Propagate genuine dependency failures. |
| `search` / CDC | Bad search request is 400. Classified search infrastructure failures are 503 `SEARCH_UNAVAILABLE`. CDC retries transient delivery, records dead letters, and does not advance a checkpoint after an unrecorded failure. |
| `e2e` | Reset is disabled in production; bad credentials are 403; classified indexing/verification failure is 503; unexpected persistence failure is 500. |

## 6. Safe diagnostics and verification

- Public messages are safe and bounded. Never expose stack traces, tokens, secrets, database index names, or unreviewed input. Keep the original cause for controlled server diagnostics. Log 5xx with useful types and stack locations plus the request ID; avoid noisy full stacks for normal 4xx.
- Test status, code, message, envelope, and request ID for representative framework and application failures. Test that unexpected `IllegalArgumentException` and unclassified persistence failures remain 500. Cover ownership without revealing another user's resource, concurrency recovery, retry/dead-letter behavior, and successful paths.
- When status, code, message, or documented responses change, regenerate `docs/api/moodly-openapi.json` and verify its diff. Keep code catalog, HTTP tests, and OpenAPI aligned.

## 7. Reviewed direction for remaining decisions

1. **Duplicate key:** Keep unclassified Mongo uniqueness failures at 500. Translate only a proven user-actionable duplicate at its service to an explicit 409 exception/code. Retain `DUPLICATE_RESOURCE` as a legacy catalog value until client usage and deprecation are reviewed; do not restore the generic Mongo handler.
2. **Missing or unowned habit:** Use the shared owned-habit lookup and the same 404 and public message for streak, habit management, and today's habit log. Keep `NOT_FOUND` until a client needs a distinct module code.
3. **Archived habit log:** Permit correction of an existing log; reject creating a new log after archive with 409 `HABIT_ARCHIVED`. A log request that observes an active habit may complete while archive overlaps it; the state read at validation decides the outcome. Requests started after archive completes observe the archived state. Keep the HTTP test and OpenAPI response synchronized.

## 8. Change procedure

For each refactor, inventory throw/catch sites and current HTTP contract; identify the layer that understands the failure; change that layer and relevant tests; regenerate OpenAPI if the contract changes; run full verification; review status, code, message, and operational effects before merging. Do not add module-specific codes merely to make modules look symmetrical.
