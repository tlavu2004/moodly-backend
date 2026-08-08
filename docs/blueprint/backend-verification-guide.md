# Backend Verification Guide

This guide verifies the complete local Moodly backend before frontend work. It covers Auth0 JWT authentication, MongoDB ownership, CDC-backed search, and Cloudinary avatars.

## 1. Prerequisites

1. Docker Desktop is running.
2. `.env.local` contains MongoDB, Elasticsearch, CDC, Auth0, Cloudinary, and CORS values. Do not commit this file.
3. Create two Auth0 demo users: **user A** and **user B**.
4. Obtain one **access token** per user for `AUTH0_AUDIENCE`. Do not use an ID token.
5. Start the local services:

   ```bash
   make local-up
   make local-run
   ```

6. Confirm infrastructure is healthy:

   ```bash
   make local-status
   make local-replica-status
   make local-elasticsearch-status
   ```

The API is expected at `http://localhost:8080`.

## 2. Expected security behavior

| Scenario                                                  | Expected result                                    |
|-----------------------------------------------------------|----------------------------------------------------|
| No Bearer token                                           | `401`, error code `UNAUTHORIZED`                   |
| Malformed, expired, wrong issuer, or wrong audience token | `401`, error code `UNAUTHORIZED`                   |
| Valid access token                                        | Request succeeds and uses JWT `sub` as the user ID |
| User B reads user A data                                  | No user A data is returned                         |
| User B confirms user A avatar ID                          | Rejected as invalid/unauthorized ownership         |

Every successful response uses `{ "success": true, "data": ..., "timestamp": ... }`.

## 3. Verify with the `.http` file

Open [`docs/testing/moodly.http`](../testing/moodly.http) in VS Code with the REST Client extension.

At the top, set:

```http
@accessToken = <user-A-access-token>
@secondAccessToken = <user-B-access-token>
```

Run requests in this order.

1. **Authentication failure:** send `GET /habits` with no token, then the invalid-token request. Both return `401`.
2. **User A CRUD:** create a habit, update today's habit entry and mood, then list habits and date-range entries. Copy the created habit ID into `@habitId`.
3. **Statistics:** call mood trend, most-missed habits, and streak.
4. **CDC and search:** search for text from the mood/habit note. Search can lag briefly; retry after a few seconds. Inspect `/actuator/health` and the Elasticsearch document when needed.
5. **User B isolation:** call `GET /habits` and search with `@secondAccessToken`. User A data must not be visible.
6. **Avatar signature:** call `POST /me/avatar/upload-signature` as user A with a small JPG, PNG, or WebP size. Record `uploadUrl`, `apiKey`, `timestamp`, `signature`, `publicId`, and `uploadPreset` from the response.
7. **Direct Cloudinary upload:** send a `multipart/form-data` POST to `uploadUrl` with `file`, `api_key`, `timestamp`, `signature`, `public_id`, and `upload_preset`. Use the returned `public_id` and `version` exactly.
8. **Avatar confirmation:** call `POST /me/avatar/confirm`, then `GET /me/avatar`. Verify the returned delivery URL loads a square transformed avatar.
9. **Ownership:** attempt to confirm user A's public ID with user B's token. It must fail.
10. **Replacement:** issue a new signature, upload a second file, and confirm it. Verify the profile returns the new public ID and the old asset is deleted in the Cloudinary Media Library.
11. **Abandoned upload cleanup:** request a signature but do not upload. After its expiry, allow the scheduled cleanup to run and inspect backend logs/Cloudinary. This validates the retry cleanup path.

## 4. Verify with Bruno

### Create the collection

1. Open Bruno and select **Create Collection**.
2. Name it `Moodly Local Verification` and store it outside ignored/secrets directories, or ensure any committed collection contains no token values.
3. Create an environment named `local` with these variables:

   ```text
   baseUrl=http://localhost:8080
   accessToken=<user-A-access-token>
   secondAccessToken=<user-B-access-token>
   habitId=
   publicId=
   avatarVersion=
   ```

Keep token values local to Bruno; do not commit the environment file if it contains tokens.

### Request setup

For every protected request, add this header:

```text
Authorization: Bearer {{accessToken}}
```

Use `Bearer {{secondAccessToken}}` for user B isolation requests.

Create requests matching the `.http` file:

| Bruno request        | Method and URL                                | Notes                                                                              |
|----------------------|-----------------------------------------------|------------------------------------------------------------------------------------|
| Create habit         | `POST {{baseUrl}}/habits`                     | Save returned habit ID to `habitId` manually or with a Bruno post-response script. |
| List habits          | `GET {{baseUrl}}/habits`                      | Run as both users.                                                                 |
| Update today habit   | `PATCH {{baseUrl}}/entries/today`             | JSON body from `.http`.                                                            |
| Set mood             | `PUT {{baseUrl}}/entries/today/mood`          | Include a searchable note.                                                         |
| Entries/stats/streak | `GET` endpoints                               | Use the same parameters as `.http`.                                                |
| Search               | `GET {{baseUrl}}/entries/search?q=tired`      | Wait for CDC indexing.                                                             |
| Avatar signature     | `POST {{baseUrl}}/me/avatar/upload-signature` | JSON: `contentType`, `sizeBytes`.                                                  |
| Avatar confirm       | `POST {{baseUrl}}/me/avatar/confirm`          | Use Cloudinary response `public_id` and `version`.                                 |
| Get avatar           | `GET {{baseUrl}}/me/avatar`                   | Check `deliveryUrl`.                                                               |

### Upload to Cloudinary from Bruno

After the signature response, create a request with:

- Method: `POST`
- URL: copy `uploadUrl` from the signature response
- Body type: `Multipart Form`

Add these form fields:

| Field           | Value                                 |
|-----------------|---------------------------------------|
| `file`          | Select a local JPG, PNG, or WebP file |
| `api_key`       | Signature response `apiKey`           |
| `timestamp`     | Signature response `timestamp`        |
| `signature`     | Signature response `signature`        |
| `public_id`     | Signature response `publicId`         |
| `upload_preset` | Signature response `uploadPreset`     |

Copy Cloudinary's `public_id` and `version` into the Avatar confirm request. Never enter `CLOUDINARY_API_SECRET` into Bruno.

## 5. Completion checklist

- [ ] User A CRUD, statistics, CDC indexing, and search work.
- [ ] Missing/invalid/expired/wrong-issuer/wrong-audience tokens return `401`.
- [ ] User B cannot read User A MongoDB or Elasticsearch data.
- [ ] Avatar accepts only allowed image types and rejects a declared size above 5 MiB.
- [ ] Avatar confirmation persists Cloudinary-verified metadata and delivery URL.
- [ ] User B cannot confirm User A's avatar public ID.
- [ ] Avatar replacement deletes the previous Cloudinary asset.
- [ ] Abandoned signed uploads are cleaned up after expiry.

Only after every item passes should frontend implementation begin. Before deployment, complete the OpenAPI and CI/CD gate in the main blueprint.
