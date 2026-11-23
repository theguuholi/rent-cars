# Recommendations Spark job

A bounded popularity baseline: count distinct `RentalOpened` rentals per car in the UTC interval `[asOf - 30 days, asOf)`. Return events do not increase scores. Ties are resolved by car UUID ascending. The top 1,000 cars are stored; the API can return up to 100.

This is **Apache Spark**, the data processing engine, not the unrelated Spark Java web framework. It is a Java 21 application with its own Maven Wrapper, dependencies, Docker image and CI. The recommendations HTTP service remains Spring Boot / Java 25.

## Run and study

Follow the [step-by-step lab](../../docs/recommendations-spark.md). Run `make recommendations-build` from the repository root after the recommendations service has applied its Flyway migration and archived events.

For a reproducible cutoff:

```sh
RECOMMENDATIONS_AS_OF=2026-11-24T00:00:00Z make recommendations-build
```

Command arguments are raw HDFS URI, curated HDFS URI, and ISO-8601 cutoff. Database settings are environment variables `RECOMMENDATIONS_DATABASE_URL`, `RECOMMENDATIONS_DATABASE_USER`, `RECOMMENDATIONS_DATABASE_PASSWORD`; credentials never belong in command arguments.

## Navigate the code

1. `PopularityRanking`: explicit JSON schema, validation, duplicate/conflict detection, window, aggregate, deterministic rank.
2. `RecommendationJob`: creates Spark, saves an immutable Parquet directory, publishes the complete result, releases resources.
3. `SnapshotPublisher`: one PostgreSQL insert publishes one snapshot. No JPA or Spring service imports in this standalone batch.
4. Flyway migration in the recommendations service owns the versioned database contract. The job does not create or alter schema.

## Verify

Use JDK 21 (also used by the Docker build and CI), with Docker running:

```sh
./mvnw -B verify
./mvnw -B -Pmutation test-compile org.pitest:pitest-maven:mutationCoverage
```

Reports: `target/site/jacoco/index.html` and `target/pit-reports/index.html`. Both thresholds are 100%. Tests run real Spark in `local[2]` and isolated PostgreSQL containers.

## Deliberate limits

- Popularity is not personalization; no ALS model yet.
- One local-mode Spark driver with two execution threads, even in Kubernetes. This needs no Spark operator or cluster RBAC. Scale to distributed executors after measurements justify it.
- Reads the archive each run; no streaming checkpoint or incremental ingestion yet. JSON is raw, Parquet is curated output.
- The complete bounded ranking is one JSONB snapshot. Readers cannot see a partial batch. Ordering by cutoff prevents late older jobs replacing newer results; repeated cutoffs keep the first committed result. To include late events, use a later cutoff.
- Parquet and PostgreSQL are not a distributed transaction. A database failure can leave an unreferenced Parquet directory, never a partially published result. Keep it for diagnosis; retention/cleanup is future operational work.
- A missing/unavailable archive fails the job. An existing empty directory produces an empty snapshot. Invalid events fail visibly rather than silently biasing the ranking.
- Availability filtering, main API orchestration and React display belong to the next PR.

Use the Make target or explicitly set `RECOMMENDATIONS_AS_OF` when invoking Compose directly; an omitted cutoff fails rather than silently computing an obsolete window. Local Docker/Helm set HDFS replication to one for the single-DataNode lab. Adjust replication with a production HDFS topology.

## Independent Sonar project

The CI always runs formatting, tests, JaCoCo and PIT. For SonarCloud, create a separate project (for example `rent-cars-recommendations-job`) with the team's quality gate, then configure repository variable `SONAR_RECOMMENDATIONS_JOB_PROJECT_KEY`, the existing `SONAR_ORGANIZATION` and secret `SONAR_TOKEN`. Do not reuse the recommendations service project key. Until provisioned, the workflow explicitly reports that Spark Sonar analysis is not configured; it does not claim a passing Sonar gate. Fork PRs do not receive the token.
