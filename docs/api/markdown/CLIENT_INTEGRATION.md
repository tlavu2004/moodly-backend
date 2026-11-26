# Client Integration

## Habits and Journal

`PUT /entries/today/mood` requires a `score` from 1 to 5. A missing score returns HTTP 400 `INVALID_REQUEST`.

Use `GET /habits?status=active` for today's habit selection and `GET /habits?status=all` to join journal logs or statistics to the user's current habit names and icons by ID. The `archived` status lists only archived habits. Names reflect the current habit record, including renames, so clients should provide a fallback for unresolved IDs.

`PATCH /habits/{habitId}` and `POST /habits/{habitId}/archive` or `/restore` require the latest `version`. Use the returned habit after a successful mutation. On HTTP 409 `HABIT_CONFLICT`, reload the habit list and let the user review the latest state before resubmitting. Unknown and other users' habit IDs both return HTTP 404 `HABIT_NOT_FOUND`.

`PATCH /entries/today` requires `habitId` and an explicit Boolean `done`. A missing or null `done` returns HTTP 400 `VALIDATION_FAILED`. The optional `note` replaces the log's note. Omission or null clears it, so send the current note when toggling completion if it should be retained. An existing log for today can be corrected after its habit is archived. Creating a new log for an archived habit returns HTTP 409 `HABIT_ARCHIVED`.

`GET /entries?from=YYYY-MM-DD&to=YYYY-MM-DD` returns paginated journal entries with each log's `habitId`, `done`, and optional `note`. Join these IDs with the habit list for labels. Date boundaries are inclusive calendar dates. The backend resolves today in `Asia/Ho_Chi_Minh`. Audit fields such as `createdAt` and `updatedAt` are timestamps.

## Avatar

`POST /me/avatar/upload-signature` requires `contentType` and `sizeBytes`. `POST /me/avatar/confirm` requires `publicId` and `version` from the upload flow. Omitting `sizeBytes` or `version` from its respective request returns HTTP 400 `INVALID_REQUEST`.

`DELETE /me/avatar` returns the cleared avatar metadata in the normal success envelope. Use that response to update local state and render the existing default avatar. Cloudinary cleanup can be retried asynchronously. The Auth0 picture and profile are separate from Moodly's custom avatar.

## Search

Display the highlight keys using localized labels for `mood.note` (mood note), `habits.note` (habit log note), and `mood.tags` (mood tag). A habit note fragment does not identify an individual habit. Each range refers to its own fragment's returned `text`, which may be shorter than the original field. Offsets are zero-based UTF-16 code units, with an inclusive start and exclusive end. For example, in `😴 calm`, the range `[3, 7)` selects `calm` using JavaScript `text.slice(3, 7)`. Render the sliced values as text.
