# Recommendations consumer

An independent Spring Boot application that consumes rental events from Kafka. This first step validates the event envelope and logs a receipt. It does not calculate recommendations, write business data or expose an HTTP server.

## Run locally

Prerequisites: Java 25 and Docker. Run all commands from the repository root.

```sh
# Start Kafka, create rentals.events and start the consumer in this terminal.
make recommendations-dev
```

In another terminal, run `make dev` to start the API, web application and Kafka UI. Open or return a rental using the web application or the existing [HTTP examples](../../requests/). The API's outbox publishes the event; this application logs:

```text
Rental event received: eventId=... eventType=RentalOpened rentalId=... partition=... offset=...
```

Open [Kafka UI](http://localhost:8084), select `rentals.events`, and inspect its messages and consumer group `rent-cars-recommendations-v1`. `make kafka-ui` starts the UI separately. This consumer has no browser port of its own.

Stop the consumer with Ctrl+C. Restart it with the same group to resume from committed offsets. To study replay, use a new group (the broker must already be running):

```sh
cd apps/recommendations
RECOMMENDATIONS_GROUP_ID=rent-cars-recommendations-study ./mvnw spring-boot:run
```

`earliest` applies only when the group has no valid committed offset; it does not replay all messages on every restart. Replay is limited by Kafka retention.

| Variable | Default | Purpose |
| --- | --- | --- |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Broker address reachable by this process |
| `RECOMMENDATIONS_GROUP_ID` | `rent-cars-recommendations-v1` | Independent consumption position |
| `RENT_MESSAGING_TOPIC` | `rentals.events` | Existing rental topic |

If you change the Compose host Kafka port, also set `KAFKA_BOOTSTRAP_SERVERS=localhost:<port>` when running the JVM locally. Instances sharing a group divide partitions; distinct groups each receive their own stream.

## Run in Docker

Use the existing root Compose file:

```sh
docker compose --profile recommendations up -d --build recommendations
docker compose logs -f recommendations
# Stop only this consumer, preserving Kafka and its data.
docker compose stop recommendations
```

The profile starts Kafka and the topic initializer as dependencies. Inside Docker, the consumer uses `kafka:19092`. The multi-stage image contains the application and a distroless Java runtime; Maven and the JDK remain in the build stage. Run either the local JVM or the container when learning; running both with the same group splits partitions between them.

For local Kubernetes, the root Helm chart includes this supporting consumer by default. Publish all images with `./scripts/push-images.sh`, then run `make k8s-up`; it prepares Kafka before deploying the application. Follow the [Helm guide](../../helm/README.md#verify-the-supporting-consumer) to inspect receipt logs and configure image/group settings. There is no public consumer endpoint or Service.

## Delivery and failures

The source is the [versioned rental event contract](../../docs/events/rentals.md), keyed by `rentalId`. The consumer accepts `RentalOpened` and `RentalReturned`, schema version 1, required envelope fields and an object payload. Additional envelope fields are ignored for additive compatibility. Payload business fields are deliberately opaque until this service uses them.

`enable-auto-commit: false` and listener `ack-mode: record` commit the next offset after a successful listener invocation. A crash after logging but before the offset commit may replay that receipt: delivery is **at least once**, not exactly once. Duplicate `eventId` values may appear in logs. There are no business side effects yet; add durable deduplication before introducing them.

Malformed JSON, missing required fields, unsupported types/versions and mismatched keys stop the listener through Spring Kafka's `CommonContainerStoppingErrorHandler`. There is no automatic retry loop, discard or dead-letter topic in this first step. The failed offset remains uncommitted and later records are not processed. The JVM remains running even when the listener stops; inspect logs and group lag rather than treating a running process as consumer health.

To recover, inspect the failing partition/offset, correct the consumer or upstream contract as appropriate, and restart the application. Restart alone repeats a permanently invalid record. Kafka records cannot be edited in place: a deliberate offset skip loses that record for the group and requires an explicit operator decision. A later production deployment needs monitored listener health and a defined retry/dead-letter policy.

## Code navigation

- `RecommendationsApplication`: Spring Boot entry point.
- `rentals/RentalEvent`: consumer-owned integration envelope, independent of API entities.
- `rentals/RentalEventsConsumer.consume`: deserialize, validate, verify key, log receipt.
- `rentals/KafkaConsumerConfiguration`: stop consumption on failure.
- `application.yaml`: broker, group and commit policy.
- Tests mirror these responsibilities; `RentalEventsKafkaTest` verifies broker behavior with a disposable Kafka.

## Quality checks

The existing Java harness applies here, including Given/When/Then tests, Spotless, JaCoCo, PIT and the repository's Sonar quality gate. No quality threshold is lowered.

```sh
make check-recommendations  # Formatting, unit/integration tests, coverage and packaging
make check-java            # Run the two independent Maven builds
make check                 # Both Java applications plus the web checks

./apps/recommendations/mvnw -f apps/recommendations/pom.xml spotless:apply
./apps/recommendations/mvnw -f apps/recommendations/pom.xml -Pmutation test-compile org.pitest:pitest-maven:mutationCoverage
```

Reports: `apps/recommendations/target/site/jacoco/index.html` and `apps/recommendations/target/pit-reports/index.html`. This module enforces 100% line and branch coverage and a 100% PIT mutation score. Integration tests cover retained events, both event types, duplicate delivery, independent groups, restart from committed offsets and failure without skipping the invalid record.

There is no root POM. API and recommendations each own their Spring Boot parent, Maven Wrapper, build and Docker context. `make check-java` is only a convenience target that invokes both builds. Keep quality plugin versions aligned.

CI files use `api-*`, `frontend-*` and `recommendations-*` prefixes, with quality and mutation separated for each application. This application has its own verification, PIT and infrastructure jobs, and a separate Sonar project selected by `SONAR_RECOMMENDATIONS_PROJECT_KEY`. On first use the workflow provisions it in the existing organization and binds the API quality gate and profiles, using `SONAR_TOKEN`; the token needs project creation/administration rights. The API project and its policy are not changed.
