# Kafka in Rent Cars: delivery, consumers and observability

## Follow one message

1. `RentalService` changes the rental/car and `RentalEventRecorder` inserts an
   `outbox_messages` row in the same database transaction.
2. `OutboxSchedule` polls; `OutboxPublisher` acquires a database advisory lock,
   selects the oldest pending row and respects its retry delay.
3. It sends JSON to `rentals.events` with `rentalId` as the Kafka key.
4. It waits for the producer acknowledgment, then marks the row published.
5. Recommendations polls Kafka, parses/validates the envelope, checks the key,
   and logs receipt. Spring commits the next offset after successful processing.

A topic is a retained event log, not a queue that deletes a message when someone
acknowledges it. `RentalOpened` and `RentalReturned` are event types inside this
one topic. Another consumer group can read the same retained records independently.

## Two different acknowledgments

**Producer (`acks: all`):** Kafka acknowledges the write after the in-sync replicas
required by its replication configuration acknowledge it. This does not mean
recommendations has processed it. The local setup has one broker and replication
factor one, so `all` does not provide multi-broker fault tolerance here.

**Consumer (`ack-mode: record`, `enable-auto-commit: false`):** no manual
`Acknowledgment.acknowledge()` call is needed. The Spring listener container
commits the offset when this synchronous listener returns successfully. The
Kafka client's periodic auto-commit is disabled; Spring manages commits.
A committed offset is the next record to read within a partition for that group.
The message remains in Kafka until retention removes it.

The current consumer only logs. If business writes are added later, complete them
before returning and deduplicate `eventId` transactionally with those writes.
A crash between the write and offset commit can cause replay. Producer idempotence
handles producer-internal retries, not Outbox republication or consumer side effects.
Delivery is at least once, not end-to-end exactly once.

References: [Spring listener commit modes](https://docs.spring.io/spring-kafka/reference/kafka/receiving-messages/message-listener-container.html),
[Kafka producer configuration](https://kafka.apache.org/41/configuration/producer-configs/).

## Partitions and consumer groups

The local topic is created with **three partitions**, one replica and a seven-day
retention setting. Existing topics are not resized by Compose's `--if-not-exists`
initializer; inspect the actual topic in Kafbat when reusing an older volume.
Recommendations uses group `rent-cars-recommendations-v1`, one application
instance and default listener concurrency one. It can consume all three partitions.

Within the same group, a partition is assigned to one consumer at a time:

| Consumers in the group | With three partitions |
| --- | --- |
| 1 | One consumer handles all three. |
| 2 | Assignments are typically two partitions and one. |
| 3 | Each can handle one partition. |
| 4 | At least one has no partition to consume. |

A replica is a copy of partition data on another broker; it is not a consumer.
A pod can host multiple consumers if listener concurrency is configured.
Assignments rebalance when group members join/leave. Offsets are per
**group + topic + partition**, not a global message number.

The key groups events for one rental into the same partition while partitioning
configuration is unchanged. Ordering is per partition, not across the whole topic.
Changing the partition count can change key placement for new records, so plan
ordering-sensitive migrations rather than increasing it casually.

Read: [Kafka introduction and partitions](https://kafka.apache.org/41/getting-started/introduction/).

## Hot partitions

A hot partition receives more work or slower records than the others. Diagnose
per-partition lag, bytes/record rate and processing duration before scaling.

- All partitions fall behind: consider more consumers/concurrency up to the
  available partition count, after checking CPU, broker and downstream capacity.
- One partition falls behind: inspect its keys and expensive records. Extra
  consumers cannot split that partition within the same group.
- One key dominates: more partitions still send that key to a single partition.
  Consider a finer key or explicit subkeys only if the business can relax ordering.
- A broker is overloaded: assess partition/leader placement and broker capacity;
  moving a partition does not split its hot key.

For this application's opened/returned events, `rentalId` is a sensible initial
key because each rental contributes few events. It is not proof of balanced load;
measure it. Do not key all Brazilian rentals by a constant such as `BR`.

The current Outbox serializes publication with a global advisory lock and waits
for one send per transaction. A failed oldest message blocks later publication.
This is intentionally simple; adding consumer replicas does not remove this
producer bottleneck. Preserve delivery/ordering guarantees when evolving it.

## Failures and retries today

- Producer: Kafka internal retries plus Outbox retry with increasing delay,
  capped at five minutes. The row remains pending until acknowledged.
- Consumer: `CommonContainerStoppingErrorHandler` stops the listener on a failed
  record. Earlier successful records can be committed; the failing record is
  not silently skipped. There is no consumer retry/DLQ policy yet.
- Restart resumes from committed offsets. `earliest` applies when no valid offset
  exists, not on every restart. Replay only reaches data still retained.
- A running recommendations JVM/pod can have a stopped listener. Check group
  membership, lag and receipt logs, not only process health.

Read: [Spring Kafka error handling](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html).

## Observability actually present

| Signal | Current implementation |
| --- | --- |
| API metrics, traces, logs | Exported to local Grafana LGTM via OTLP in dev; also configured by the local Helm chart. |
| Browser fetch traces | Enabled in Vite dev; not enabled in the published React image. |
| Outbox | Pending count, oldest pending age, acknowledged/failed send metrics and publisher observation. |
| Kafka messages, members, offsets, lag | Kafbat UI on port 8084. |
| Recommendations | Receipt/error logs on stdout; no OTLP telemetry export configured. |
| Complete asynchronous trace | Not implemented: trace context is not persisted in Outbox and propagated through to this consumer. |

To add the last item, persist the originating trace context with the Outbox row,
restore it when sending, propagate Kafka headers and instrument/export consumer
observations. Turning on a dashboard alone cannot join these asynchronous spans.
Use bounded metric labels; event IDs belong in logs/traces, not metric labels.

Read: [Spring Kafka monitoring and observations](https://docs.spring.io/spring-kafka/reference/kafka/micrometer.html).
Follow the [local/Kubernetes E2E walkthrough](local-stack-validation.md) to prove
publication and consumption before studying throughput or changing partition counts.
