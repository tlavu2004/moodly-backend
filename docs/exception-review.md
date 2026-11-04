# Exception review before production

This is a historical audit. The current contract and implementation rules are in [Moodly Exception Standard](blueprint/exception-standardization-blueprint.md).

## HTTP contract

`ApplicationException` carries the public `ErrorCode`, safe message and HTTP status. `GlobalExceptionHandler` maps it into `ApiError`. Framework validation, malformed input, security and optimistic locking have explicit mappings. Unclassified duplicate keys and unexpected exceptions, including plain `IllegalArgumentException`, return the fixed `INTERNAL_SERVER_ERROR` response; their messages are not exposed.

The initial review preserved existing status, code and message values. Avatar cleanup now returns the persisted profile result even if deletion of the obsolete asset fails unexpectedly; cleanup is a secondary operation after the profile write. A missing or stale E2E search document returns the existing 503 response. The E2E reset endpoint is disabled in the documentation profile and is not in the OpenAPI snapshot. No generated OpenAPI path or schema changed.

## Initial findings and changes

This table records the first review; the follow-up fixes and the official standard supersede its original shared-code decisions.

| Area                                     | Finding                                                                                                                                                                             | Resolution                                                                                                                                                                           |
|------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Auth/avatar                              | Avatar confirmation used parameterless `orElseThrow()` if a synchronized profile disappeared. That produced an anonymous internal failure.                                          | `AvatarProfileMissingException` names the invariant failure. It still maps to 500.                                                                                                   |
| Auth/Cloudinary                          | Obsolete asset deletion used plain `IllegalStateException`; its caller queues cleanup after profile persistence.                                                                    | `AvatarAssetDeletionException` identifies the retryable cleanup failure and preserves its messages and cause.                                                                        |
| Search/CDC replay                        | Interruption, non-availability Elasticsearch failures and malformed replay payloads used plain `IllegalStateException`.                                                             | `CdcReplayException` identifies these internal replay failures; unavailable infrastructure still uses `SEARCH_UNAVAILABLE` and missing dead letters use `CDC_DEAD_LETTER_NOT_FOUND`. |
| Auth configuration                       | `Auth0HttpConfigurationException` extends `IllegalArgumentException`.                                                                                                               | Kept: this is a named startup configuration error, not a request validation exception.                                                                                               |
| Entries, habits, stats, search query     | Explicit input checks use `InvalidRequestException`; missing habits/dead letters use `ResourceNotFoundException`; stale writes use `ConflictException` or optimistic-lock mapping.  | Kept to preserve current 400/404/409 responses and messages.                                                                                                                         |
| Search listener, CDC delivery, E2E reset | Plain `IllegalStateException` or `Objects.requireNonNull` represents internal event invariants, serialization failures or a diagnostic cause inside an already typed 503 exception. | Kept as internal diagnostics. Do not map all `IllegalStateException` or `IllegalArgumentException` to 400: that would expose programming or provider failures as client errors.      |
| Background cleanup and CDC listener      | Broad catches keep retry/dead-letter processing alive or record listener failure.                                                                                                   | Kept; these paths do not directly create API error responses.                                                                                                                        |

## Review before merge

- Confirm that a profile disappearing between upload and confirmation is an internal consistency failure (500), rather than a domain-level 404. The current HTTP behavior is preserved.
- Confirm that a non-availability CDC replay failure remains 500 and that only search infrastructure unavailability is 503. The current HTTP behavior is preserved.
- Review whether returning the persisted avatar state while an obsolete Cloudinary asset remains queued matches the product's intended completion semantics.
- Review the operational alert for a CDC programming failure: the listener stays unhealthy and requires a corrected deployment or explicit restart. Transient MongoDB connection failures restart from the persisted resume token.

## Follow-up fixes

- Cloudinary catches only transport and JSON parsing failures for avatar verification, and transport failures for deletion. Unexpected runtime failures reach the internal 500 fallback instead of being reported as provider unavailability. SHA-1 catches only `NoSuchAlgorithmException`.
- Avatar cleanup queues deletion failures after profile persistence. Unexpected client failures are logged at error level, and scheduled cleanup reschedules them while continuing to process other assets.
- CDC delivery retries and dead-letters `IOException` and `ElasticsearchException`. An unexpected writer runtime failure reaches the listener without advancing its resume token. A failed dead-letter write retains the original delivery failure as a suppressed cause. A failed replay-state write no longer hides the replay failure or its HTTP classification.
- E2E reset compares each seeded search document with the expected projection. A missing source or stale content is failed verification and returns the existing `SEARCH_UNAVAILABLE` 503 response.
- The CDC listener schedules a restart after transient MongoDB failures and resumes from its persisted token. It keeps a programming failure unhealthy without automatic replay of the same poison event.

## Recovery follow-up

- A history-lost CDC stream now records a MongoDB operation-time checkpoint captured before rebuilding the search index. The previous token remains persisted if the rebuild fails. A successful rebuild saves the operation-time checkpoint before registering the stream, so a process restart can replay changes made during the rebuild. MongoDB error code 286 is identified from the exception cause chain rather than message text.
- Expired avatar uploads are cleaned independently. Unexpected client failures retain their pending record and do not stop cleanup of later uploads. A confirmed avatar remains protected if deletion of its pending-upload record failed.
- Avatar replacement and deletion persist the obsolete asset's cleanup record before changing the profile. If that write fails, the profile is left unchanged. Cleanup checks for a still-referenced avatar before deleting its Cloudinary asset.
- A search hit without its source fails the request with `INTERNAL_SERVER_ERROR` 500 instead of silently shrinking the result page. The adapter logs its hit ID for index inspection or rebuild.

## Client compatibility check

The adjacent `moodly-frontend` currently stores `error.code` in `ApiRequestError`, but its feature code does not branch on the former shared 400/404/409 codes or `SEARCH_UNAVAILABLE`. `getUserFacingError` maps 5xx responses to a generic safe message. No frontend code migration is required for the module-code changes in this branch. A missing-source 500 has the same generic client message as other internal failures; operators distinguish it by the backend hit-ID log and request ID, not by a new public error code.
