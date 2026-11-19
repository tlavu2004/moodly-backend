# Release and Production Deployment

## Primary Deployment Flow

Moodly builds and smoke-tests a single backend image in GitHub Actions, publishes that same image to GHCR, and deploys an immutable digest to an image-backed Render service. Render does not rebuild the application from Git source. No workflow in this repository calls Render, creates a service, changes hosted secrets, or deploys automatically.

The checked-in `render.yaml` is the reviewed deployment configuration. Its bootstrap digest identifies the already published application commit recorded in the file. It does not claim to contain unmerged branch changes. For the first deployment after this branch merges, use the new successful release's candidate and promote its digest before provisioning Render.

There are only local, test, and production environments. Workstation Compose and CI fixture networks are infrastructure, not additional deployment environments.

## Build and Release Identity

PR CI verifies application/OpenAPI, builds an image, and runs production container smoke. Release runs on application changes to `main` and version tags. It targets `linux/amd64`, loads the image, smoke-tests it, and only then pushes its tags. It checks the loaded image's OCI revision against the workflow source SHA and records exactly one digest for the expected GHCR repository.

The release artifact `release-image-<commit>` contains:

- `release-image.json`: source commit, expected repository, tags, and immutable image reference.
- `render-release.yaml`: an offline deployment candidate using the same service configuration as `render.yaml`, with only the image URL and source-commit comment replaced.

Tags such as `latest` are discovery conveniences, not deployment identities. The Blueprint always uses `ghcr.io/tlavu2004/moodly-backend@sha256:...`. Retain the release artifact and the preceding known-good digest for rollback. A successful push is not evidence of a successful Render deployment.

## Offline Candidate Review

Download the artifact from a successful Release run for the intended application commit. To regenerate its candidate locally:

```bash
python3 scripts/deployment/render_blueprint.py release-image.json render.yaml target/render-release.yaml
```

Use `python` on Windows if needed. The helper reads only the provided record and public Blueprint, validates the complete commit and repository-specific digest, and writes a candidate. It never contacts Render or GHCR, edits its inputs, or deploys. Do not treat an arbitrary JSON file as verified release provenance; obtain the record from the successful repository workflow.

Review the diff between `render.yaml` and the candidate. For a normal promotion, only the source-commit comment and image URL change. Copy the reviewed candidate to `render.yaml` in a small promotion PR. A push that changes only `render.yaml` skips the Release image build; PR verification still validates configuration. A version tag can explicitly trigger a new image release.

## First Render Setup

These are operator steps, not actions performed by the branch:

1. Confirm the intended release completed verification, image build, smoke, push, and candidate generation. Promote that candidate's digest; do not deploy the older bootstrap image by accident.
2. Use the Blueprint's image-backed web service configuration: `moodly-backend`, Singapore, free plan, HTTP port 8080, `/actuator/health`, production profile, and 60-second shutdown allowance. Confirm the chosen Render plan and its resource limits before provisioning; local smoke is not a production load test.
3. Create or review the `moodly-secrets` environment group using `.env.production.example` as a key checklist. Configure Atlas, remote Elasticsearch, Auth0, Cloudinary, exact FE CORS origins, and the CDC maintenance key. Fill hosted secrets only in the provider's secret store; never commit their values or print resolved environment configuration.
4. Confirm Atlas Free's server major and driver compatibility, database/network permissions, replica-set change-stream access, and remote Elasticsearch compatibility. No Atlas or search server is created by this Blueprint.
5. Confirm GHCR pull access. The bootstrap image was verified anonymously accessible. If package visibility changes, provision a Render registry credential with read-only package access and reference it through `image.creds.fromRegistryCreds.name`; do not paste a token into YAML.
6. Before linking/syncing a Blueprint, review its branch/path and disable Blueprint Auto Sync for manual promotion. The Git service `autoDeployTrigger` field does not control image-backed services or replace Blueprint sync policy. Do not manage this service from another Blueprint or an independent source-build service.
7. Review the Render change preview, then obtain approval for the actual service creation/sync and deploy. Applying an image URL change through Blueprint sync can deploy the service.

Render's initial service creation and environment-group setup require access to the user's workspace. YAML/schema validation does not verify that those resources exist or that hosted credentials work.

## Post-Deploy Verification

Wait for the Render deployment to finish and check the public health response contains `status: UP`. Production health must omit components/details. Confirm unauthenticated protected API requests return 401 and reset/docs endpoints remain unavailable. Review CDC/search health, Auth0 token validation, CORS and uploaded asset behavior with authorized synthetic accounts before FE cutover.

Record the deployment ID, application commit, image digest, and preceding known-good image. Keep hosted API tokens, deploy-hook URLs, and user tokens out of logs and PR descriptions. Review the FE Auth0 callback/logout configuration separately; changing backend CORS does not change Auth0 settings.

## Promotion and Rollback Ownership

Blueprint manual sync is the production configuration owner. After a reviewed promotion PR merges, inspect the Blueprint diff and manually sync it with approval. Do not independently edit the service's image URL in the dashboard and then let a conflicting Blueprint overwrite it. No deploy hook or Render API credential is configured in the release workflow.

For rollback, prepare a small configuration change restoring the previous known-good digest and source-commit marker, review it, then manually sync and verify health. An emergency provider rollback must be reconciled with the checked-in Blueprint before the next sync. Image rollback does not reverse MongoDB data, Elasticsearch mappings, uploads, or hosted configuration; review compatibility and migrations separately.

## Image and Runtime Maintenance

Dockerfile base images use readable tags plus digests. Review dependency updates with image build and smoke verification. MongoDB fixture pins live in `.env.local.example` and are shared with smoke; the existing Dockerfile updater is not proof those dotenv pins update automatically. Remove the temporary rseq workaround only after validating a published patched image against the actual runner kernel.

Backend business dates, JVM defaults, and application log timestamps use `Asia/Ho_Chi_Minh`; persisted `Instant` values remain UTC. Native MongoDB/Elasticsearch logs can use different timestamp conventions. See [Backend Execution Modes](../testing/backend-execution-modes.md) for local commands and data ownership.

## References

- [Render Blueprint Reference](https://render.com/docs/blueprint-spec)
- [Deploy a Prebuilt Docker Image](https://render.com/docs/deploying-an-image)
- [Render Blueprint Sync](https://render.com/docs/infrastructure-as-code)
- [Render Rollbacks](https://render.com/docs/rollbacks)
