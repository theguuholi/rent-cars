# Study: pool and capacity

Complete each checkpoint before changing configuration. Start with the [ACID lab](rental-acid-rollback.md) if transaction boundaries are unfamiliar. This lab changes no application settings and runs against a disposable PostgreSQL container, never your development database.

## 1. Trace the request and predict demand

Read `apps/api/src/main/resources/application.yaml`, `CarController`, and `CarRepository.search`. Follow the React Cars page through its HTTP client to `GET /cars`.

- [ ] Find `spring.datasource.hikari`. Currently it configures `logServerErrorDetail`, not a pool size.
- [ ] Explain why 12 browser requests do not necessarily require 12 connections for their entire lifetime.
- [ ] Distinguish browser latency, server processing, connection acquisition, SQL execution, and JSON serialization.
- [ ] Predict which measurements would justify investigating the pool rather than a slow SQL query.

Hikari's `maximumPoolSize` bounds idle plus borrowed connections; `minimumIdle` is the idle target. `connectionTimeout` bounds waiting to borrow, not SQL execution. Read the [Hikari configuration reference](https://github.com/brettwooldridge/HikariCP#configuration-knobs-baby).

## 2. Run the baseline before tuning

Prerequisites: the repository's Java 25 and a running Docker daemon. From the repository root:

```sh
cd apps/api
./mvnw -B -Dtest=CatalogCapacityStudyTest test
cat target/study-reports/catalog-capacity/environment.txt
cat target/study-reports/catalog-capacity/summary.csv
```

The Given/When/Then test starts the actual API on a random loopback port, applies migrations to Testcontainers PostgreSQL, inserts 20,000 synthetic cars and runs `ANALYZE`. After 10 warm-up requests, it performs 120 requests at each concurrency level: 1, 4 and 12. Each response must be HTTP 200 with 20 cars and 20,000 matching rows. Connections must be returned afterward. Fixtures are deleted and Testcontainers removes the database when the test JVM exits.

Reports under `target/study-reports/catalog-capacity/`:

| File | Meaning |
| --- | --- |
| `environment.txt` | Actual Java/PostgreSQL versions, pool bounds, borrow timeout, server connection budget and initial client connections |
| `summary.csv` | Nearest-rank p50/p95/max latency, throughput and sampled active/waiting/total pool peaks |
| `latency-N.csv` | One request duration in milliseconds per row; completion retrieval follows submission order |
| `pool-N.csv` | Samples about every 10 ms: borrowed connections, waiting threads, total connections |

Latency covers the local HTTP round trip including body transfer, excluding assertions. Throughput includes test assertions and executor teardown. Sampling can miss short peaks; the three pool getters are not an atomic snapshot. Hikari borrowed connections are not identical to PostgreSQL `state='active'` sessions. Idle pool connections still occupy server slots.

- [ ] Save the environment and reports before any experiment.
- [ ] Compare p95 and throughput across stages, alongside occupied connections and waiting threads.
- [ ] Record the bottleneck you can demonstrate, or explicitly write **not yet established**.
- [ ] Repeat the baseline to detect warm-up effects and local machine noise.

There are no speed assertions: hardware-dependent timing is study evidence, not a CI performance threshold. The test has bounded HTTP and batch timeouts to detect a broken experiment. This colocated client/server, small warm dataset and short run do **not** establish production capacity. Use a separate load generator, realistic data and sustained mixed reads/writes before a capacity decision.

## 3. Calculate a connection budget

Use the observed values, not assumed defaults:

```text
API replicas × maximumPoolSize
+ connections from jobs, migrations and other applications
+ operational headroom
<= max_connections - superuser_reserved_connections - reserved_connections
```

Plan for rollout overlap and maximum autoscaling, not just the current replica count. Reserved connection availability depends on the database role; leave administrative capacity untouched. See [PostgreSQL connection settings](https://www.postgresql.org/docs/17/runtime-config-connection.html).

- [ ] Calculate the budget for one API replica and then for a hypothetical rolling deployment with four replicas.
- [ ] Explain why independently increasing every pod's pool could exhaust PostgreSQL.
- [ ] Account for separate pools if read replicas are introduced later.

## 4. Diagnose before changing the pool

| Evidence | Next investigation |
| --- | --- |
| Waiting grows and borrowed connections reach the cap | Check SQL duration, transaction lifetime, locks and database CPU/I/O before testing another size |
| p95 grows but there is no sampled queue | Investigate query plans, CPU, serialization, HTTP threads and the load generator; absence of a sample is not proof of no waiting |
| Connections approach the PostgreSQL budget | Reduce total demand or replica/pool allocation; do not simply increase the server limit |
| Larger pool yields no repeatable gain | Keep the baseline; extra connections are not free throughput |

For a justified comparison only, pass a temporary override to the same disposable experiment:

```sh
# Run from apps/api, after preserving baseline reports (this overwrites them).
./mvnw -B -Dtest=CatalogCapacityStudyTest \
  -Dspring.datasource.hikari.maximum-pool-size=6 test
```

Confirm `environment.txt` reflects the override. Repeat both configurations with the same fixture, concurrency and machine conditions. Record uncertainty and rollback criteria. Do not copy the experimental size to `application.yaml` without evidence.

## 5. Connect the measurements to React

Open the Cars page with the normal local stack and inspect Network timings while changing a filter and page. Compare user-observed latency with the isolated API experiment. Check how many requests a user action generates, which are canceled, and which are served from TanStack Query's cache. Browser timings include proxy/network/rendering effects absent from the pool sample. Do not run the load against a shared environment.

Write a conclusion with: workload, versions, p95, throughput, maximum borrowed/waiting connections, PostgreSQL budget, demonstrated bottleneck, missing evidence and next experiment. Continue with [replication, partitioning and cache](replication-cache-partitioning.md).

An initial [recorded baseline](results/catalog-capacity/README.md) includes raw measurements and an explicit “bottleneck not established” conclusion. Reproduce it on your machine rather than expecting identical timings.
