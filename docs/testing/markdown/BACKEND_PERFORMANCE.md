# Backend Performance

Measure server-side request latency with the Spring Boot Actuator and Micrometer timer `http.server.requests`. The repository enables its percentile histogram in `src/main/resources/application.yaml`. Confirm that the test environment has a histogram-capable registry and a private collection path before running. The histogram setting alone does not export buckets or provide a percentile dashboard. Keep management endpoints private.

## Measurement Procedure

1. Run a dedicated BE test instance using [Backend Execution Modes](BACKEND_EXECUTION_MODES.md). Record the BE commit, infrastructure, concurrency, active habit count, history depth, and effective rate-limit configuration. Use synthetic accounts and keep credentials out of reports.
2. Warm the API with representative requests. Capture a measurement window that excludes warm-up, authentication setup, seed writes, and unrelated traffic. Record whether the scenario uses a single browser or a separate concurrent workload.
3. Collect successful `GET /habits` and `GET /habits/{habitId}/streak` timings separately using method, URI template, and status, where available. Use the route template for streaks so different habit IDs share one series. Report failures, timeouts, and HTTP 429 separately, with counts and denominators.
4. Calculate p95 and p99 over the run window. With raw elapsed times, sort samples and select one-based rank `ceil(p × N)`. With exported histogram buckets, compute quantiles from bucket count changes for the selected window and label the values as estimates. If compatible instances serve the same workload, combine bucket counts before estimating quantiles. An average, maximum, or average of per-instance percentiles cannot reconstruct the distribution.
5. Report the route, workload, measurement window, successful sample count, p50, p95, p99, and error and 429 counts. Use milliseconds and label low sample counts, especially when interpreting p99. If the environment cannot supply raw timings or buckets, report server percentiles as unavailable.

The server timer covers the interval instrumented inside the application. Use [System Performance](SYSTEM_PERFORMANCE.md) to compare it with browser-observed request and page timings from the same run.
