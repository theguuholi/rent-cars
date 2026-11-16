# Recorded baseline

Captured on 2026-11-16 from `origin/master` commit `1746897` plus the new `CatalogCapacityStudyTest`, with Java 25, PostgreSQL 17.11 in Docker Desktop and the colocated HTTP load generator. Command, from `apps/api`:

```sh
./mvnw -B -Dtest=CatalogCapacityStudyTest test
```

Result: 1 test, 0 failures, 0 errors, 0 skipped. Ten warm-up requests and all 360 measured requests returned correct catalog pages. No pool size override was applied. See `environment.txt` and raw CSVs alongside this file.

| Concurrent requests | p95 (ms) | Requests/s | Peak borrowed | Peak waiting |
| --- | --- | --- | --- | --- |
| 1 | 66.20 | 16.99 | 1 | 0 |
| 4 | 93.15 | 48.67 | 4 | 0 |
| 12 | 267.62 | 64.51 | 10 | 2 |

The runtime pool maximum/minimum idle were both 10; PostgreSQL allowed 100 connections with 3 superuser-reserved slots. One API pool therefore accounts for at most 10 of the 97 ordinary slots. Four such pools would account for 40, before other clients and operational headroom.

Observed: the 12-worker stage reached the pool cap and sampled a queue, while p95 increased and throughput continued to rise. **The underlying capacity bottleneck is not established.** A queue demonstrates connection contention during this run, not whether a larger pool improves throughput or overloads the database. Next: inspect query plans, connection hold time, PostgreSQL waits and CPU/I/O, then repeat controlled comparisons if justified.

Limitations: one short run, ascending concurrency, warm data, test instrumentation, colocated client/server, no production network and no concurrent rentals. Peaks are sampled, not exact maxima. The workload contains broad brand matches and pagination/count queries; it is not a representative production traffic mix. Do not adopt these timings as an SLA or treat a higher pool size as the conclusion.
