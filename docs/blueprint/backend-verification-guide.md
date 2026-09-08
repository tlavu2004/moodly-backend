# Moodly Backend Verification Guide (Bruno)

This guide verifies the local backend before additional automated testing and React SPA development. It covers Auth0, MongoDB, habits and daily entries, statistics, Elasticsearch CDC/search, Cloudinary avatars, and data isolation between two users.

## 1. Prerequisites

1. Docker Desktop is running.
2. `.env.local` contains MongoDB, Elasticsearch, CDC, Auth0, Cloudinary, and CORS configuration. Do not commit this file.
3. Auth0 has two separate test accounts: **user A** and **user B**.
4. Open the collection at `docs/testing/bruno/moodly-local-verification` and select the `local` environment.
5. The API is running at `http://localhost:8080`.

The collection's `local` Bruno environment is intentionally local and ignored by Git. Configure it with values for your Auth0 SPA application, Cloudinary setup, and CDC maintenance key. The avatar signature request populates its short-lived upload variables and `publicId` at runtime, while requests that create a habit or a dead letter populate their corresponding IDs.

Start and check the local stack:

```bash
make local-up
make local-status
make local-replica-status
make local-elasticsearch-status
make local-run
```

If the collection was updated, close and reopen it in Bruno to load the new requests.

## 2. Authentication conventions

| Request group | Configuration |
|---|---|
| Health | `No Auth` |
| User A profile bootstrap, habits, entries, statistics, search, and avatars | `Inherit` from the collection OAuth configuration |
| User B ownership checks | Request-level OAuth 2.0 with the `moodly-user-b-local` credential ID |
| Invalid token | Intentionally invalid Bearer token |
| CDC maintenance | `No Auth` plus `X-Maintenance-Key` |

Bruno stores the access token after Auth0 Universal Login. Do not copy access tokens into `.env`, do not use ID tokens for API calls, and do not expose an Auth0 Client Secret in the SPA or Bruno.

Auth0 is the only system that signs up users, logs them in, and issues tokens. Once Auth0 authentication succeeds, the client calls `PUT /auth/profile` to create or retrieve the application profile idempotently. Habit, entry, statistics, and search APIs only read the JWT `sub`; they must not create users as a hidden side effect.

Deleting MongoDB alone does not delete an Auth0 account or Auth0 browser session. `PUT /auth/profile` recreates an empty application profile after a MongoDB reset. To test as a completely new person, use or delete the corresponding Auth0 test account as well.

### Select the correct user before sending requests

- **User A**: use the collection OAuth token for sections 5, 6, 8, 9, 11, and 12, and when writing the daily entry in section 13.2.
- **User B**: use the request-level OAuth flow for the ownership checks in sections 7 and 10. The B requests use `prompt=login`; sign in with the B account when Auth0 asks. Do not overwrite the collection token for user A.
- **No sign-in required**: health (section 3), missing/invalid token (section 4), the direct Cloudinary upload (section 9.2), CDC reindex (section 13.1), and dead-letter replay (section 13.2). CDC maintenance requests still require `X-Maintenance-Key`.

After a user B check, continue user A requests with the collection OAuth token.

Successful responses use this envelope:

```json
{
  "success": true,
  "data": {},
  "timestamp": "..."
}
```

## 3. Health check

Run `01 - Health/Health`.

Required result:

- HTTP `200`.
- Overall health is `UP`.
- MongoDB and Elasticsearch report no errors.

If health is not ready, stop business-flow testing and fix the local infrastructure first.

## 4. Negative authentication checks

Run, in order:

1. `05 - Security and CDC/Missing token is unauthorized`.
2. `05 - Security and CDC/Invalid token is unauthorized`.

Both must return HTTP `401` with code `UNAUTHORIZED`. The invalid-token request intentionally contains `invalid-or-expired-token`; Bruno's hard-coded-token warning is expected.

Testing wrong issuer, wrong audience, and expired JWTs requires deliberately invalid or expired tokens. Do not replace the active user A/B token; cover those cases in automated security tests or with dedicated OAuth test credentials.

## 5. User A habits and daily entries

### 5.1 Bootstrap the application profile

Sign in as **user A**, then run `02 - Habits and Entries/Bootstrap authenticated profile`.

Expect HTTP `200`, `success: true`, `data.userId` equal to the Auth0 JWT `sub`, and a normalized email when the token contains an `email` claim. Running the request a second time must return the same profile without creating a duplicate `users` document.

### 5.2 Create a habit

Continue as user A and run `02 - Habits and Entries/Create habit`.

Expect HTTP `201 Created`, a habit named `Exercise`, and `active: true`. Bruno automatically stores `data.id` in `habitId` in the `local` environment; check that variable before running requests that use `{{habitId}}`.

### 5.3 Read and update today's data

Run in order:

1. `List active habits` — HTTP `200` and contains `Exercise`.
2. `Mark habit done today` — HTTP `200`, `done: true`, and note `30-minute run`.
3. `Set today's mood` — HTTP `200`, mood score `4`, tags `productive` and `tired`, and a note containing `tired`.
4. `List entries by date range` — HTTP `200` and contains the updated entry.
5. `Get habit streak` — HTTP `200`; a first recorded day normally has streak `1`.

Date-range requests must use a `to` date no later than today and include the date that was written. Update the Bruno request when testing on a different date.

## 6. Statistics and Elasticsearch search

Continue as **user A**.

Run:

1. `03 - Statistics and Search/Weekly mood trend`.
2. `Most missed habits`.
3. `Search entries`.

Verify that the mood trend contains the submitted data, most-missed returns a valid response, and search finds user A's `tired` note or tag. Most-missed may be empty when no habit has been missed.

Search uses CDC and is eventually consistent. If data is not visible, wait a few seconds and retry. If it is still absent, check health, run `Rebuild CDC index`, and search again. Reindexing must not create duplicate documents.

## 7. User A and user B isolation

Keep the data created by user A, then run these requests as **user B**:

1. `05 - Security and CDC/User B cannot see User A habits`.
2. `User B search is isolated`.

Both may return HTTP `200`, but their payloads must not contain the `Exercise` habit, the `tired` note/tag, or any other user A data. An empty list is correct when user B has no data of its own.

## 8. Negative avatar validation

Switch back to **user A**.

Run:

1. `04 - Avatar/Reject unsupported avatar type` — sends `image/gif`; expect HTTP `400`.
2. `Reject avatar over 5 MiB` — sends `5242881` bytes; expect HTTP `400`.

The backend accepts only `image/jpeg`, `image/png`, and `image/webp`, from 1 byte through `5242880` bytes.

## 9. Valid avatar upload

### 9.1 Request a signed upload payload

Run `04 - Avatar/Request avatar upload signature`. Bruno saves `uploadUrl`, `apiKey`, `timestamp`, `signature`, `publicId`, and `uploadPreset` as temporary environment variables; it also persists `publicId` for the confirm request.

### 9.2 Upload directly to Cloudinary

Run `04 - Avatar/Upload avatar to Cloudinary`. Before the first run, open **Body → Multipart Form**, add a `file` field of type **File**, and select a JPG, PNG, or WebP no larger than 5 MiB. The Cloudinary text fields already use variables from section 9.1. The request saves Cloudinary's returned `public_id` and `version` to `publicId` and `avatarVersion` in the `local` environment.

Never put `CLOUDINARY_API_SECRET` in Bruno. Cloudinary must return HTTP `200` with `public_id`, `version`, `format`, `bytes`, and `secure_url`.

### 9.3 Confirm and read the avatar

After a successful upload, `publicId` and `avatarVersion` are already updated. Run `Confirm uploaded avatar`, then `Get current avatar`. Metadata must match Cloudinary and `deliveryUrl` must open successfully.

## 10. Avatar ownership between users

Create a new signed upload and upload an image as user A, but do not confirm it yet. Keep its `publicId` in the environment, then run `05 - Security and CDC/User B cannot confirm User A avatar` as **user B**.

The request must return HTTP `400` with an ownership error. User B must not confirm or manage an asset under user A's namespace. Then switch back to user A and confirm the pending upload successfully.

## 11. Replace an avatar and delete the previous asset

Switch back to **user A**.

1. Record the current avatar `publicId`.
2. Request a new signature and upload a different image.
3. Allow Bruno to update `publicId` and `avatarVersion`.
4. Run `Confirm uploaded avatar`, then `Get current avatar`.
5. Check the Cloudinary Media Library.

The profile must return the new avatar, the backend must delete the previous asset, and the new asset must remain available.

## 12. Clean up an abandoned upload

1. Request a new signed upload payload.
2. Upload a file but do not call confirm.
3. The pending upload expires after one hour.
4. The cleanup scheduler runs every five minutes by default.
5. After expiry and a scheduler run, the abandoned asset must disappear from Cloudinary.
6. If Cloudinary is temporarily unavailable, the backend must retain the pending record, increment cleanup attempts, and retry later.

This is a long-running test and can run while other steps are performed.

## 13. CDC maintenance and recovery

### 13.1 Full reindex

Run `05 - Security and CDC/Rebuild CDC index`. The request uses `No Auth` and this header:

```text
X-Maintenance-Key: {{maintenanceKey}}
```

Expect HTTP `200`. Search again and verify complete data, correct ownership, and no duplicates.

### 13.2 Dead-letter replay

This test requires a real dead letter:

1. Stop only Elasticsearch; keep MongoDB and the backend running.
2. Use **user A** to create or update a daily entry with a distinctive note.
3. Wait for all CDC retries to fail and create a dead letter in MongoDB.
4. Copy the dead-letter `_id` into `deadLetterId` in the environment.
5. Start Elasticsearch again and wait for it to become healthy.
6. Run `Replay CDC dead letter`.

A valid ID returns HTTP `204`; a placeholder or missing ID returns `404`. After a successful replay, the dead-letter document must disappear and search must find the updated entry.

## 14. Completion checklist

- [x] Local health infrastructure is `UP`.
- [x] Missing and invalid tokens return `401`.
- [x] `PUT /auth/profile` bootstraps idempotently and ordinary business APIs do not create profiles.
- [x] User A creates and reads habits and entries successfully.
- [x] Streak and statistics return sensible data.
- [x] CDC indexes entries into Elasticsearch and search finds them.
- [x] User B cannot see user A's MongoDB or Elasticsearch data.
- [x] Avatar rejects an unsupported MIME type and a size above 5 MiB.
- [x] Signed upload, Cloudinary upload, confirmation, and delivery URL work.
- [x] User B cannot confirm user A's avatar public ID.
- [x] Replacing an avatar deletes the previous asset.
- [x] An abandoned upload is cleaned up after expiry.
- [ ] Cleanup retry behavior is verified when Cloudinary is unavailable.
- [x] Full reindex neither loses nor duplicates data.
- [x] Dead-letter replay works with a real dead letter.

## 15. Remaining Phase 3 verification

The completed items above prove the core local flow, but Phase 3 is not complete until every item below passes. Keep using the ignored `local` Bruno environment; never commit tokens, Cloudinary secrets, uploaded test-media URLs with sensitive query data, or test-account passwords.

- [ ] **Finish two-way data isolation.** As user B, bootstrap a profile and create a habit and entry with unique data. Verify user A cannot read B's habits, entries, statistics, or search result; retain the existing B-cannot-read-A check. Inspect local MongoDB only for the two distinct `auth0Subject` values and absence of credentials, sessions, refresh tokens, and password hashes. Inspect Elasticsearch documents to confirm their `userId` matches the owner, and confirm no search request accepts a caller-supplied user-ID override.
- [ ] **Finish the hosted token matrix.** Keep the existing missing and malformed-token checks. Using dedicated disposable Auth0 test credentials or an approved tenant test mechanism, send expired, invalid-signature, wrong-issuer, and wrong-audience access tokens to one protected endpoint. Every case must return `401 UNAUTHORIZED`; only a valid identity denied by a real authorization rule may return `403 FORBIDDEN`.
- [ ] **Finish avatar confirmation validation.** Confirm that fabricated public IDs, altered versions, expired pending uploads, an asset outside the owner's namespace, a disallowed Cloudinary format, and confirmed size over 5 MiB all fail without replacing current avatar metadata.
- [ ] **Finish cross-user avatar protection.** With user B, try to reuse a signature issued to user A and try every available confirm/overwrite/delete path using a user-A public ID. Each must fail, with user A's profile metadata and Cloudinary asset unchanged.
- [ ] **Verify cleanup retry.** Make Cloudinary deletion fail temporarily for an expired pending upload. Verify the local pending record remains and its cleanup attempt count increments; restore Cloudinary access and verify a later scheduler run removes the remote asset and record.

## 16. Completion and next order

Only after section 15 passes:

1. Tick the matching `[~]` items in `moodly-blueprint.md` as complete and mark the local Phase 3 checkpoint complete.
2. Run `mvn test` with Docker available; the full suite must pass before committing.
3. Inspect `git diff` and Git status. Remove any token, secret, test media, or local environment file from the change set.
4. Commit the Phase 3 verification with `test(auth): verify local Auth0, CDC, and Cloudinary flows`.
5. Start the next gate: OpenAPI/Swagger documentation and GitHub Actions CI. Do not begin frontend deployment until that gate is complete.
