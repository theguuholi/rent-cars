# Study: retries and dead-letter recovery

A real analytics consumer must distinguish a temporary outage from a poison event.
Retries absorb temporary failures. The dead-letter topic (DLT, Kafka's equivalent of
DLQ) preserves failures for investigation while later events continue. It is not a
successful business result and it needs an owner, alerts and a replay procedure.

## Boundaries and guarantees

```text
API transaction in PostgreSQL
  rental + car availability + outbox_messages
                    |
              OutboxPublisher -- retries pending publication
                    |
              rentals.events
                    |
              recommendations
             /               \
       success                failure
       commit offset          retry after 1s, 2s, 4s
                              or immediately recover invalid input
                                      |
                        rentals.events.recommendations.DLT
                          /                         \
                  Kafka confirms                publication fails
                  commit source offset          keep source pending
```

There is **no new PostgreSQL table or migration**. The API's existing transaction
commits the rental and Outbox together. The publisher marks an Outbox row as
published only after Kafka acknowledges the send. Kafka acceptance followed by a
failed database commit can publish the same event again.

The recommendations consumer also provides **at-least-once recovery**, not an
atomic transaction across source offsets and DLT publication. A crash after the DLT
write but before the offset commit can create duplicate DLT records. Producer
idempotence only protects producer-internal retries. Preserve `eventId` during
replay; future business writes need durable deduplication in the same transaction
as those writes. This consumer currently only validates and logs receipts.

`enable-auto-commit=false` and `ack-mode=record` remain enabled. The
`DeadLetterPublishingRecoverer` in the pinned Spring Kafka 4.1 version already has
`failIfSendResultIsError=true` by default. We preserve that behavior rather than
repeat a redundant setter: the code comment and failed-send tests explicitly guard
this guarantee on upgrades. A failed DLT send throws; the error handler must not return successful recovery
and the source offset must not advance past that record. There is no fallback to
logging-and-discarding. Source records can still expire under Kafka retention:
restore recovery before the seven-day source retention expires.

The original key, payload and existing headers (including tracing headers when
present) are retained. Spring adds original topic, partition, offset, consumer group
and exception headers. Invalid JSON may have no readable `eventId`; use the source
coordinates to identify it. Exception headers and payloads can contain personal
data: restrict DLT access and retention in production. Observer logs contain only
coordinates and attempt numbers, not payloads. Framework error logs may include
additional exception details; protect access to those logs too.

## Error policy

- JSON parsing errors, Bean Validation failures and invalid payload/key contracts:
  direct to DLT. Repeating an unchanged invalid input cannot fix it.
- Other processing exceptions: initial attempt plus three retries, with exponential
  waits of 1, 2 and 4 seconds. Retry state is in memory and can restart on a process
  restart/rebalance; this is not a lifetime delivery-attempt limit.
- DLT publication failure: retain the source position and retry recovery. A
  persistent broker or permission failure requires operator intervention.
- Blocking retries delay consumption on that listener thread (potentially multiple
  assigned partitions). Moving an event to DLT allows later events to proceed, so
  replay does not preserve business-event order. Revisit this before adding
  order-dependent aggregates.

## Configuration and storage

| Variable | Default | Purpose |
| --- | --- | --- |
| `RENT_MESSAGING_DLT` | `rentals.events.recommendations.DLT` | Existing DLT; create it before consuming |
| `RECOMMENDATIONS_MAX_RETRIES` | `3` | Retries after the initial attempt |
| `RECOMMENDATIONS_RETRY_INTERVAL_MS` | `1000` | Initial exponential wait; multiplier 2 |

The producer uses string serializers, `acks=all`, idempotence enabled and bounded
send timeouts. Local Compose and the Strimzi manifest create the DLT explicitly:
three partitions, delete retention of 14 days, one replica and minimum ISR of one.
The DLT preserves the source partition, so it must have at least as many partitions
as the source. If customizing the topic names/partition count, provision matching
topics; application properties alone do not create them.

The local broker has persistent storage but **no broker redundancy**. For production,
provision at least three brokers in separate failure domains, source and DLT with
replication factor 3 and `min.insync.replicas=2`, together with `acks=all`. With too
few synchronized replicas, publication must fail rather than acknowledge reduced
durability. Add authentication, TLS, least-privilege ACLs, disk monitoring, PostgreSQL
backups and recovery drills. Retention is not permanent archival; use object storage
if investigation/audit obligations exceed the retention window. None of these
production infrastructure guarantees are established by a single-broker local test.

## Local exercise

Run commands from the repository root, in an isolated development environment.

1. Run `make dev`. It starts infrastructure, initializes both topics and starts
   API, web, recommendations and Kafbat. Alternatively, `make recommendations-dev`
   starts only the consumer and Kafka dependencies.
2. Open/return a rental using the normal UI or HTTP examples. In Kafbat at
   `http://localhost:8084`, check `rentals.events` and the recommendations group.
   Expect the receipt log and committed offset to advance.
3. Publish a deliberately malformed record (local study only):

   ```sh
   printf 'study-key:not-json\n' | docker compose exec -T kafka \
     /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server kafka:19092 \
     --topic rentals.events --property parse.key=true --property key.separator=:
   ```

4. Inspect `rentals.events.recommendations.DLT` in Kafbat. Expect the same key and
   `not-json` value, original source coordinates and exception headers. The consumer
   logs `DLT confirmed`; its source offset advances after recovery.
5. Open/return another rental. Expect normal processing to continue. Restart the
   consumer with the same group; committed history is not automatically replayed.
6. For repeatable transient failures and DLT outages, run the Kafka integration
   tests instead of adding artificial failure switches to production code:

   ```sh
   ./apps/recommendations/mvnw -f apps/recommendations/pom.xml \
     -Dtest=RentalEventsKafkaTest test
   ```

A CLI view of retained dead letters:

```sh
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server kafka:19092 --topic rentals.events.recommendations.DLT \
  --from-beginning --property print.key=true --property print.headers=true
```

## Kubernetes exercise

Do not run local and Kubernetes environments together. Stop `make dev` and run
`make infra-stop`. Publish the new recommendations image with the existing
`scripts/push-images.sh`, then run `make k8s-up`. The Kafka setup now waits for both
the source and DLT to be Ready before deploying applications.

Follow the namespace/context used by the existing [Helm guide](../helm/README.md).
This PR preserves that infrastructure convention. Verify:

```sh
kubectl --context docker-desktop -n rent-kafka get kafkatopic
kubectl --context docker-desktop -n rent-cars logs deployment/fleet-recommendations -f
```

Open Kafbat at `http://localhost:8084`. Produce the same local-study malformed
record into `rentals.events`, inspect the DLT and publish a valid rental through
the app. Expect quarantine followed by successful consumption. No recommendation
HTTP port or database is needed. `messaging.deadLetterTopic` in Helm must match the
provisioned topic name.

## Observability and replay

Micrometer counters are exported through the JVM's JMX registry (no HTTP server):

- `rental.consumer.failures`: failed deliveries, including the initial attempt.
- `rental.consumer.dlt.published`: confirmed recoveries, including possible duplicates.
- `rental.consumer.dlt.failures`: failed DLT publication/recovery attempts.

Inspect local JMX with JConsole and Kafka topic contents/group lag with Kafbat.
These new counters are **not automatically connected to Grafana**; production
needs a configured collector/exporter and alerts. Alert on any DLT publication,
recovery failures, growing source lag, and unresolved events nearing retention.
A counter is not the number of unresolved events: Kafka reading does not delete
records, and this iteration has no case-management database.

Replay is intentionally manual:

1. Record the DLT/source coordinates and diagnosis in an incident/task.
2. Fix the actual cause; replaying the same poison input unchanged loops back to DLT.
3. Decide whether replaying the original topic is safe for **every consumer group**.
   It delivers again to notifications and other groups too. For consumer-specific
   replay, design a dedicated replay path before using it in production.
4. Using Kafbat's produce/resend functionality, preserve key and `eventId` when
   present. If repairing malformed data, explicitly review the corrected contract;
   never fabricate a successful business event just to clear an error.
5. Verify receipt and committed offset, then record resolution. The old DLT record
   remains until retention; do not delete the topic or reset the whole group.

## Test contract

Given/When/Then tests verify exact retry intervals/count, non-retryable input,
confirmed DLT recovery, failed DLT publication without offset advancement,
restoration, next-record processing, payload/key/source/tracing preservation,
restart, metrics and logs without payloads. Kafka Testcontainers establishes
broker behavior; unit tests cover policy and observability. Existing JaCoCo and
PIT thresholds remain 100%.

References: [Spring Kafka error handling](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html),
[Kafka durability configuration](https://kafka.apache.org/41/configuration/broker-configs/).
