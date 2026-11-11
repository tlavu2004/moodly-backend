# Release and production deployment

## Current state

`release.yml` verifies code/OpenAPI, builds and loads one image, smoke-tests that
image, then pushes its tags without rebuilding. The full commit SHA tag, OCI
revision label, workflow summary, and `release-image.json` artifact identify it.
PR container checks also run smoke tests. No workflow in this repository currently
deploys Render. The existing `render.yaml` describes a Git-backed source build;
its presence does not prove the live service uses that configuration.

There are only local, test, and production environments. The smoke stack is a
short-lived test fixture, not another deployment environment.

## Recommended primary flow

Use GHCR image-by-digest as the production artifact:

1. Merge a reviewed change; the release workflow must pass verification and smoke.
2. Read `images` and `commit` from that run's `release-image.json`. Use the
   `ghcr.io/...@sha256:...` reference, never `latest`, as the deployment identity.
3. Deploy that reference to an image-backed Render service.
4. Wait for Render to report success and `/actuator/health` to return HTTP 200
   with `status: UP`. Record the Render deployment ID alongside commit and digest.
5. If verification fails, deploy the previous known-good digest and verify health.

Image rollback does not roll back MongoDB data, Elasticsearch mappings, or hosted
configuration. Release changes must remain compatible with the preceding image,
or include a separately reviewed data migration and rollback plan.

## One-time migration (not performed by this change)

Before changing any hosting settings, inspect the live Render service's source,
auto-deploy mode, current commit/deploy ID, secret environment group, hostname,
and registry access. Do not copy credentials into the repository or workflow logs.

Prepare an image-backed Render web service with the same production environment
group and non-secret settings from `render.yaml`, including the production profile,
port 8080, CDC/search, rate limit, and `/actuator/health` health check. For a private
GHCR package, configure a Render registry credential with read-only package access.
Use a verified release digest for the initial image. Review the service migration,
URL/custom-domain cutover, and removal of the old source auto-deploy path before
executing them. Keep the old production service available until cutover is verified;
this is a service migration within production, not a staging environment.

After migration, replace the source-build Blueprint with the image-backed
configuration actually in use. Do not blindly sync the current `render.yaml`
against an image-backed service. Keep one production deployment owner.

Initially, deploy the reviewed digest manually in Render. Automation can later
trigger a deploy with an explicit image reference and await its final status;
provisioning service IDs, API credentials, and any approval environment is a
separate, reviewed hosting change. Publishing an image alone is not a deployment.

## Base image updates

Both Dockerfile stages pin the registry's multi-platform index digest while keeping
the tag readable. Existing weekly Dependabot Docker updates should be reviewed for
both tag and digest changes; verify the first real update PR rather than assuming
the bot has run successfully. Require application verification, image build, and
smoke checks before merge. A digest pin freezes security fixes until updated.
The Maven Wrapper separately pins the Maven distribution version. MongoDB versions
in env templates require explicit review; do not assume Dockerfile dependency
updates cover variable-expanded Compose images.

## Smoke scope and limitations

Run `docker build -t moodly:review .`, then
`python3 scripts/docker/smoke-test.py moodly:review` (use `python` on Windows).
The smoke test never reads env files. It uses unique container/network names,
MongoDB tmpfs storage, fresh Elasticsearch storage, and a local OIDC discovery
fixture. JWT security, CDC, search initialization, and rate limiting stay enabled.
It checks startup, Docker health, health privacy, protected API rejection,
reset/docs unavailability, search mapping creation, and non-root execution.

This does not exercise valid hosted Auth0 tokens, browser login, Cloudinary uploads,
429 quotas, or CDC event delivery. Those remain integration/security/E2E concerns.
The E2E reset helper must remain disabled in production. A dead-letter CDC health
status can be DEGRADED; smoke deliberately requires UP on its fresh fixture.

## References

- [Render prebuilt images and registry access](https://render.com/docs/deploying-an-image)
- [Render rollback semantics](https://render.com/docs/rollbacks)
- [Docker image pinning](https://docs.docker.com/build/building/best-practices/)
