# Follow-up task for technical verification of the OpenAPI contract

## What does CI currently guarantee?

`src/main/resources/scripts/openapi/generate-openapi.sh` runs the application with the `docs` profile, fetches `/v3/api-docs`, and writes it to `docs/api/json/moodly-openapi.json`. In `.github/workflows/ci.yml`, CI checks that the file parses as JSON, declares an OpenAPI 3 version, and contains `paths`. It then uses `git diff --exit-code` to compare the generated document with the committed version. This step catches **documentation that is out of sync with the code**. Verifying that every schema accurately describes the actual response requires a separate check.

`ApiIntegrationTest` already checks some details of `/v3/api-docs` and HTTP responses. A general comparison between the two is still missing.

## Additions for the next refactor

1. **Validate the OpenAPI document.** Use an OpenAPI 3.1-aware tool to validate its structure and resolve all internal `$ref` references. On failure, CI should identify the reference location and the missing target. Successful JSON parsing and the presence of `paths` are only preliminary checks.
2. **Check against actual HTTP responses.** For representative endpoints and scenarios, call the API through integration tests, then validate the status, content type, and JSON body against the corresponding OpenAPI response schema. Cover successful responses, validation and authentication errors, nullable fields, and shared envelopes. Document which endpoints and scenarios are covered. The results apply to the sampled responses.
3. **Check generation stability.** Generate the document twice in the same CI environment from the same revision and compare the results after normalization. If they differ, fix the source of unstable ordering or values in the generator. Updating the snapshot would mask the difference. Keep comparing the generated document with the committed file to check that it is up to date.

## Completion criteria

- All three checks run in CI and report failures clearly enough to locate the schema, endpoint, or generation step that caused the mismatch.
- Repeated runs from the same revision produce identical results, and the HTTP conformance tests confirm that the selected cases match their schemas.
- Keep the existing workflow. When a code change affects OpenAPI, run the generation script, review the diff, and commit `docs/api/json/moodly-openapi.json` alongside the code change.

The scope is **technical correctness**. Evaluating business rules and whether API decisions meet product needs is a separate review.
