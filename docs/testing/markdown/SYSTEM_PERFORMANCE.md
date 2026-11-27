# System Performance

Use this runbook to compare a real browser journey with server timings from the same run. The [frontend performance guide](https://github.com/tlavu2004/moodly-frontend/blob/main/docs/markdowns/FRONTEND_INTEGRATION_TESTING.md#habits-page-performance) owns the Playwright command and browser timing definitions. [Backend Performance](BACKEND_PERFORMANCE.md) owns server timer collection.

## Habits Journey

1. Record the FE and BE commit SHAs, browser and viewport, backend topology, dataset, concurrency, rate limits, and start and end times. Use a dedicated test stack and a synthetic account. The current Playwright harness changes that account's data and is intended for loopback test runs.
2. Run the documented authenticated browser benchmark. Collect the two-habit and ten-habit scenarios separately, with one discarded warm-up and 100 measured navigations per scenario. The bundled seed has short history. For a decision about longer histories, repeat on a representative dedicated dataset and identify the dataset in the report.
3. Measure the page journey from immediately before Playwright navigates to `/habits` until it observes the expected habit cards in the DOM. Record missing or failed streak reads. The harness checks that the expected streak requests completed successfully. A measurement explicitly requiring every streak value to be visible would need a separate DOM assertion.
4. Save the Playwright timing attachment and collect server data during the corresponding measurement windows. Report browser page timing, browser-observed API timing, and server latency as distinct rows. Record how many runs were attempted and completed, along with failures, timeouts, and rate limiting. Report a failed or incomplete run explicitly.

## Report and Review

| Metric | Sample |
| --- | --- |
| End-to-end Habits page | One elapsed duration per completed navigation |
| Browser habit list and streak group | One duration per completed navigation for each metric |
| Browser individual streak request | One duration per streak request |
| Server habit list and streak | One observation per request or a per-route histogram over the same run window |

For each measured row, report scenario, sample count, median, p95, p99 in milliseconds, and the applicable error and 429 counts. Calculate end-to-end percentiles directly from navigation samples. Percentiles from different rows may describe different observations, so neither adding nor subtracting them measures another phase. For 100 samples, nearest-rank p95 and p99 are the 95th and 99th sorted observations.

Review the N+1 streak reads if the ten-active-habit page-load p95 exceeds one second on a representative test run, typical active counts grow beyond ten, or rate limiting affects the journey. A fixture with mocked API delays helps compare FE changes. The system measurement requires the real backend.
