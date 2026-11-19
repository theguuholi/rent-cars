# Recommendations consumer

An independent Spring Boot application that consumes rental events from Kafka. It validates the event envelope and logs a receipt; an optional HDFS lab archives the original JSON before the offset can advance. It does not calculate recommendations, write business data or expose an HTTP server.

## Run locally

Prerequisites: Java 25 and Docker. Run all commands from the repository root.

```sh
# Start Kafka, create rentals.events and start the consumer in this terminal.
make recommendations-dev
```

For the complete stack, run only `make dev`: it already starts this consumer, the API, React and Kafka UI. Do not run both commands together. For independent consumer study, use the command above and start API/React with `make api-dev` and `make web-dev` in separate terminals. Open or return a rental using the web application or the existing [HTTP examples](../../requests/). The API's outbox publishes the event; this application logs:

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

## Local runtime and deployment image

Run this application on the host with `make recommendations-dev`.
The root `compose.yaml` runs infrastructure only; Kafka is available to the local
JVM at `localhost:9092`. Recommendations does not need the API or frontend to
start; it can consume retained events while they are stopped.

The Dockerfile remains available for Kubernetes deployment. Its multi-stage
image contains the application and a distroless Java runtime; Maven and the JDK
remain in the build stage.

For local Kubernetes, the root Helm chart includes this supporting consumer by default. Publish all images with `./scripts/push-images.sh`, then run `make k8s-up`; it prepares Kafka before deploying the application. Follow the [Helm guide](../../helm/README.md#verify-the-supporting-consumer) to inspect receipt logs and configure image/group settings. There is no public consumer endpoint or Service.

## Delivery and failures

The source is the [versioned rental event contract](../../docs/events/rentals.md), keyed by `rentalId`. The consumer accepts `RentalOpened` and `RentalReturned`, schema version 1, required envelope fields and an object payload. Additional envelope fields are ignored for additive compatibility. Payload business fields are deliberately opaque until this service uses them.

`enable-auto-commit: false` and listener `ack-mode: record` commit the next offset after a successful listener invocation. A crash after logging but before the offset commit may replay that receipt: delivery is **at least once**, not exactly once. Duplicate `eventId` values may appear in logs. With HDFS enabled, immutable files keyed by `eventId` provide archive replay safety. Other future side effects need their own idempotency design.

Malformed JSON, invalid envelopes and key mismatches go directly to
`rentals.events.recommendations.DLT`. Other processing failures receive three
retries (1s, 2s, 4s) before DLT recovery. The source offset advances only after
confirmed DLT publication; if that publication fails, the record stays pending.
Recovery is at-least-once and can duplicate a DLT record after a crash.

Follow the [DLT design and local/Kubernetes exercises](../../docs/recommendations-dlt.md)
for explicit PostgreSQL/Outbox boundaries, retention, production durability,
observability, configuration and safe manual replay. No new table or migration
is introduced. Micrometer recovery counters are exported via JMX; this consumer
still has no HTTP server.

## Code navigation

- `RecommendationsApplication`: Spring Boot entry point.
- `rentals/RentalEvent`: consumer-owned integration envelope, independent of API entities.
- `rentals/RentalEventsConsumer.consume`: deserialize, validate, verify key, optionally archive, log receipt.
- `rentals/KafkaConsumerConfiguration`: classify failures, retry and publish to DLT.
- `rentals/RentalEventRecoveryObserver`: recovery counters and coordinate-only logs.
- `application.yaml`: broker, group and commit policy.
- Tests mirror these responsibilities; `RentalEventsKafkaTest` verifies broker behavior with a disposable Kafka.

## Quality checks

The existing Java harness applies here, including Given/When/Then tests, Spotless, JaCoCo, PIT and this application's Sonar quality gate. No quality threshold is lowered.

```sh
make check-recommendations  # Formatting, unit/integration tests, coverage and packaging
make check-java            # Run the two independent Maven builds
make check                 # Both Java applications plus the web checks

./apps/recommendations/mvnw -f apps/recommendations/pom.xml spotless:apply
./apps/recommendations/mvnw -f apps/recommendations/pom.xml -Pmutation test-compile org.pitest:pitest-maven:mutationCoverage
```

Reports: `apps/recommendations/target/site/jacoco/index.html` and `apps/recommendations/target/pit-reports/index.html`. This module enforces 100% line and branch coverage and a 100% PIT mutation score. Integration tests cover retained events, both event types, duplicate delivery, independent groups, restart from committed offsets and confirmed quarantine, retries and recovery failure without offset advancement.

There is no root POM. API and recommendations each own their Spring Boot parent, Maven Wrapper, build and Docker context. `make check-java` is only a convenience target that invokes both builds. Keep quality plugin versions aligned.

CI quality and mutation steps live in .github/actions/recommendations/.
The root workflow orchestrates independent application jobs. Recommendations runs
its own Maven Wrapper directly; it does not build the API or frontend.

### Sonar setup

Create the recommendations project in Sonar before its first analysis. Configure
its quality gate and quality profiles there, independently of the API. CI only
submits the analysis and waits for this project's gate; it does not provision
projects or copy another application's policy.

GitHub Actions settings:

- Variable SONAR_RECOMMENDATIONS_PROJECT_KEY: this consumer's Sonar project key.
- Variable SONAR_ORGANIZATION: organization containing that project.
- Secret SONAR_TOKEN: token authorized to execute analysis on this project.

The API uses its own SONAR_PROJECT_KEY. Recommendations does not read that key.
A shared organization or authorized token does not combine the analyses.
Fork and Dependabot PRs still run Maven verification; Sonar runs only when the
workflow can access the analysis credential.

## Optional HDFS archive

Run `make dev HDFS_ENABLED=true` from the repository root to include the archive lab. Read the [system design and local/Kubernetes walkthrough](../../docs/recommendations-hdfs.md) before switching consumer groups or replaying records. It explains atomic publication, retries/DLT, storage inspection, platform requirements and production limitations.
