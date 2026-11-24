# Spark job conventions

Read the root AGENTS.md and this application's README. This is an independent Java 21 Maven application using Apache Spark, not Spring Boot. Do not import service classes or use the Spring dependency BOM: Spark owns its runtime dependency versions.

- Read only the versioned rental event contract; process only validated archives.
- Keep ranking computation separate from publication. Complete Parquet before inserting a snapshot; one atomic insert publishes all bounded results. No automatic schema creation in the job.
- Cover behavior in Given/When/Then tests using real local Spark and PostgreSQL Testcontainers. No private-method tests, gate reductions or exclusions.
- Run `./mvnw verify` and `./mvnw -Pmutation test-compile org.pitest:pitest-maven:mutationCoverage` on JDK 21. Preserve 100% JaCoCo line/branch and PIT thresholds.
- Keep Spark optional for product development. Never add a runtime dependency from the main API or React in this phase.
