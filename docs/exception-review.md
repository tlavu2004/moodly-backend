# Exception review before production

## HTTP contract

`ApplicationException` carries the public `ErrorCode`, safe message and HTTP status. `GlobalExceptionHandler` maps it into `ApiError`. Framework validation, malformed input, security, duplicate keys and optimistic locking have explicit mappings. Unexpected exceptions, including plain `IllegalArgumentException`, return the fixed `INTERNAL_SERVER_ERROR` response; their messages are not exposed.

The initial review preserved existing status, code and message values. Follow-up fixes below route unexpected avatar programming failures to the existing 500 response and a missing E2E search source to the existing 503 response. Avatar 500 and 503 responses are already in the OpenAPI snapshot. The E2E reset endpoint is disabled in the documentation profile and is not in that snapshot; its existing 503 response now also covers a missing source. No generated OpenAPI path or schema changed.

## Initial findings and changes

This table records the first review; the follow-up fixes below supersede the rows about broad catches and E2E verification.

| Area                                     | Finding                                                                                                                                                                             | Resolution                                                                                                                                                                      |
|------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Auth/avatar                              | Avatar confirmation used parameterless `orElseThrow()` if a synchronized profile disappeared. That produced an anonymous internal failure.                                          | `AvatarProfileMissingException` names the invariant failure. It still maps to 500.                                                                                              |
| Auth/Cloudinary                          | Obsolete asset deletion used plain `IllegalStateException`; its caller queues cleanup after profile persistence.                                                                    | `AvatarAssetDeletionException` identifies the retryable cleanup failure and preserves its messages and cause.                                                                   |
| Search/CDC replay                        | Interruption, non-availability Elasticsearch failures and malformed replay payloads used plain `IllegalStateException`.                                                             | `CdcReplayException` identifies these internal replay failures; unavailable infrastructure still uses `SEARCH_UNAVAILABLE` and missing dead letters still use `NOT_FOUND`.      |
| Auth configuration                       | `Auth0HttpConfigurationException` extends `IllegalArgumentException`.                                                                                                               | Kept: this is a named startup configuration error, not a request validation exception.                                                                                          |
| Entries, habits, stats, search query     | Explicit input checks use `InvalidRequestException`; missing habits/dead letters use `ResourceNotFoundException`; stale writes use `ConflictException` or optimistic-lock mapping.  | Kept to preserve current 400/404/409 responses and messages.                                                                                                                    |
| Search listener, CDC delivery, E2E reset | Plain `IllegalStateException` or `Objects.requireNonNull` represents internal event invariants, serialization failures or a diagnostic cause inside an already typed 503 exception. | Kept as internal diagnostics. Do not map all `IllegalStateException` or `IllegalArgumentException` to 400: that would expose programming or provider failures as client errors. |
| Background cleanup and CDC listener      | Broad catches keep retry/dead-letter processing alive or record listener failure.                                                                                                   | Kept; these paths do not directly create API error responses.                                                                                                                   |

## Review before merge

- Confirm that a profile disappearing between upload and confirmation is an internal consistency failure (500), rather than a domain-level 404. The current HTTP behavior is preserved.
- Confirm that a non-availability CDC replay failure remains 500 and that only search infrastructure unavailability is 503. The current HTTP behavior is preserved.
- The full `mvn verify` suite passed with Docker/Testcontainers: 177 tests, no failures or errors, and JaCoCo/Spotless gates passed.

## Follow-up fixes

- Cloudinary catches only transport and JSON parsing failures for avatar verification, and transport failures for deletion. Unexpected runtime failures reach the internal 500 fallback instead of being reported as provider unavailability. SHA-1 catches only `NoSuchAlgorithmException`.
- Avatar cleanup queues provider deletion failures and handles data-access failures in scheduled cleanup; unexpected client programming failures are no longer silently queued for retry.
- CDC delivery retries and dead-letters `IOException` and `ElasticsearchException`. An unexpected writer runtime failure reaches the listener without advancing its resume token. A failed dead-letter write retains the original delivery failure as a suppressed cause. A failed replay-state write no longer hides the replay failure or its HTTP classification.
- E2E reset treats a found Elasticsearch document with no source as failed verification and returns the existing `SEARCH_UNAVAILABLE` 503 response.
