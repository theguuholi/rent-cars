# Spark recommendations implementation plan

> Execute task-by-task with the executing-plans skill. The scope was approved in the project conversation.

**Goal:** Compute a reproducible popularity ranking from archived rental events and expose the last successful result through the recommendations service.

**Architecture:** Kafka ingestion remains independent. A Java 21 Spark batch reads HDFS, writes immutable Parquet and publishes a PostgreSQL snapshot atomically. The Java 25 Spring service owns migrations and an authenticated read API. Main API and React integration belong to the next PR.

**Tech stack:** Spark 4.1.0, Java 21/25, PostgreSQL 17, Spring Boot, Flyway, JUnit, Testcontainers, JaCoCo, PIT.

**Spec:** Approved design: thirty-day RentalOpened popularity, deduplicated event and rental IDs, deterministic ties, isolated database, local/Kubernetes execution.

## Constraints and review focus

- Preserve quality gates and independent builds; Given/When/Then tests.
- Do not count returns or duplicate opens twice.
- A failed or older batch must not replace a newer successful snapshot.
- Empty history is valid; malformed history fails visibly.
- Persist window cutoff and algorithm with results; never query Spark during HTTP requests.
- Protect the internal endpoint; no frontend or main API changes.

## Tasks

- [x] Add migration and authenticated GET /recommendations/cars with HTTP/PostgreSQL tests, including empty and populated snapshots, limit validation and unauthorized access.
- [x] Add independent Spark job and tests for the half-open UTC window, duplicates, ties, invalid schema and empty input.
- [x] Add transactional snapshot publication tests: rollback, idempotent rerun, older result rejection, and result visibility.
- [x] Add Docker, optional Compose job, Helm CronJob/manual execution, Make and image publishing support.
- [x] Add independent CI, Swagger, REST Client and a didactic local/Kubernetes study guide.
- [ ] Run formatting, complete tests, inspect coverage/mutation results and review the diff before opening the PR.

## Decisions and evidence

- Use one immutable bounded JSONB snapshot per cutoff instead of three tables: one insert publishes atomically; `ORDER BY as_of DESC` prevents an older late job becoming active. PostgreSQL primary-key conflict handling makes concurrent reruns first-writer-wins.
- Keep the Spark Maven build independent of Spring Boot dependency management. A Boot BOM upgraded Servlet transitives incompatibly with Spark during initial tests; independent Spark dependencies resolved it.
- The archive contains one complete JSON document per file, including pretty-printed JSON. Regression tests reproduced the JSON-lines mismatch before enabling multiline parsing. Canonicalize UUID case before validation and aggregation.
- The Docker Hadoop client needs an OS username for UID 1001; create the Spark user explicitly. Use writable `/tmp` with executable mapping for native compression libraries; Helm supplies an `emptyDir` there.
- Local Docker smoke: real HDFS raw JSON → Parquet `_SUCCESS` → PostgreSQL snapshot → authenticated HTTP result. An invalid event in a subsequent batch failed while HTTP kept the previous result.
- Helm templates and Compose configuration validated. No deployment was applied to the user's active Kubernetes cluster.
