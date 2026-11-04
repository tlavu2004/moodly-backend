# HTTP rate limiting

The optional limiter gives each authenticated Auth0 subject a shared quota across the client API on each backend instance. It uses the verified JWT `sub` (the same identifier used by `CurrentUser`) under the configured single issuer. Renewing a token keeps the same quota. Spring Security authenticates the token and makes its URL authorization decision before the limiter runs. Requests that reach the limiter count even if MVC later returns a validation error or a missing resource.

| Render environment variable      | Default | Purpose                              |
|----------------------------------|---------|--------------------------------------|
| `MOODLY_RATE_LIMIT_ENABLED`      | `false` | Enable after staging validation      |
| `MOODLY_RATE_LIMIT_REQUESTS`     | `120`   | Requests per subject in one window   |
| `MOODLY_RATE_LIMIT_WINDOW`       | `60s`   | Fixed window duration                |
| `MOODLY_RATE_LIMIT_MAX_SUBJECTS` | `10000` | Maximum subject counters per process |

The first request after expiry starts the next instance-wide window and clears all counters. Bursts can span two adjacent windows. At capacity, new subjects receive 429 until the window resets. Tracked subjects can still use their remaining quota. A restart, redeploy or replacement instance starts with empty counters. With multiple instances, each counts independently and a caller can consume quota on each. Review a shared store if a service-wide quota becomes a requirement. The current implementation uses process memory and needs no additional service.

429 uses the standard JSON error envelope with code `RATE_LIMIT_EXCEEDED` and a `Retry-After` header in whole seconds rounded up. Browser clients on allowed origins can read the header. Pause retries for at least that interval.

## Exempt traffic

- `/actuator/health`, Swagger and OpenAPI routes, and OPTIONS are exempt.
- `/internal/cdc/**` retains its maintenance key and production Auth0 admin authority requirements. `/internal/e2e/**` retains its existing profile and controller protections. All `/internal/**` routes are exempt, including for operators who have used their client API quota.
- No anonymous or IP-based quota is applied. Requests rejected by JWT authentication or URL authorization retain their existing responses.

## Render rollout review

1. Confirm the intended instance count, acceptable restart resets, window-boundary bursts, subject capacity behavior, and the exemptions above. Validate 120/min against dashboard, search and other concurrent client traffic.
2. On staging, confirm the configured health check path is `/actuator/health` and returns a healthy response after an API user reaches quota. Verify 429, Retry-After, CORS, a second subject, and the existing 401/403 and maintenance behavior. Confirm quota resets after a restart and is independent if staging has multiple instances.
3. Render documents that public traffic passes through Cloudflare and Render proxies and that client IP information is forwarded. The actual service ingress, direct/private reachability, and header overwrite behavior have not been verified. Before introducing any IP quota, verify the trusted proxy boundary and test spoofed and duplicate forwarding headers across the default hostname, any custom domain and private ingress. [Render edge and forwarding documentation](https://render.com/articles/how-render-handles-ddos-attacks) · [Render proxy header discussion](https://render.com/articles/host-pocketbase-on-render)
4. Set the environment variables after review and confirm normal traffic and 429 rates after rollout. Set `MOODLY_RATE_LIMIT_ENABLED=false` to disable the limiter.
