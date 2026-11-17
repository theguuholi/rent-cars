# Recommendations Java conventions

Read the root [testing and exception policy](../../AGENTS.md), the [Java feature skill](../../.agents/skills/rent-cars-java-feature/SKILL.md), and this application's [README](README.md).

- Reuse the API's Java quality standards: Java 25, Maven Wrapper, Spring Boot dependency management, Google Java Format through Spotless, JUnit Jupiter, AssertJ, JaCoCo, PIT and Sonar. Keep tool versions aligned with `apps/api/pom.xml`.
- Organize by domain under `com.github.theguuholi.recommendations`. Keep this application independently buildable; do not import API entities, repositories or services. The integration boundary is the versioned [rental event contract](../../docs/events/rentals.md).
- For now, consume events and record receipt only. No recommendation algorithm, database, HTTP endpoint, Spark or Hadoop. Do not apply the API's JPA/HTTP conventions to a consumer without those responsibilities.
- Give each production component a behavior-focused test suite. Use `@Nested` groups per application-defined method, Given/When/Then scenario names and observable assertions. Exercise private helpers through callers. Do not test generated accessors separately just for coverage.
- Test parsing and validation with real Jackson and Bean Validation. Use Kafka Testcontainers for offset commits, restart behavior, independent consumer groups and failed records; mocks cannot establish broker guarantees.
- Keep automatic offset commits disabled. A failed record must not be silently skipped. Document recovery and duplicate delivery. Before adding business writes, design durable idempotency using `eventId` and test replay against the real persistence layer.
- Run `make check-recommendations`, inspect JaCoCo and run the README's PIT command. Preserve 100% line/branch coverage and mutation score. Root `make check-java` verifies both Java applications; the existing Sonar analysis covers both.
- Update this README and the event documentation for contract/operational changes. No REST Client or Swagger change is needed unless an HTTP contract changes.
