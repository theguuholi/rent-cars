# Rental events: local Kafka and Kubernetes walkthrough

This application publishes `RentalOpened` and `RentalReturned`. Publishing rental events does not run Spark or send rental emails. The [separate Spark batch](recommendations-spark.md) runs explicitly after HDFS ingestion. PostgreSQL remains authoritative: rental changes and their Outbox record commit together. The relay retries publication independently. Read the [design and limitations](decisions/0008-rental-event-publication.md) before treating the lab as a production design.

## 1. Run on the host with Docker infrastructure

Prerequisites: Java 25, repository Node version, Docker running. Run commands from the repository root.

```sh
make api-dev
# Or make dev for API + React + Kafka UI.
```

These targets start Kafka and explicitly create `rentals.events`, then enable the application relay. To start only Kafka:

```sh
make kafka-up
```

When starting Java from an IDE or directly with Maven, set `RENT_MESSAGING_ENABLED=true`. The default is false: events are still saved, but remain pending until enabled. Default bootstrap is `localhost:9092`; override with `KAFKA_BOOTSTRAP_SERVERS`. A custom Compose `KAFKA_PORT` must also be reflected in that bootstrap address.

## 2. See the broker and messages

Kafka's port **9092 is a Kafka protocol endpoint, not an HTTP page**. Check it with Kafka tools:

```sh
docker compose ps kafka
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server kafka:19092 --describe --topic rentals.events
make kafka-events
```

The last command keeps consuming until Ctrl+C. It displays keys and JSON values from the retained history.

`make dev` starts the browser interface automatically. With `make api-dev` or Kafka alone, start it explicitly:

```sh
make kafka-ui
```

Open **http://localhost:8084** → `rent-cars-local` → Topics → `rentals.events` → Messages. The optional Kafbat UI allows reading and writing messages and is bound to localhost in Compose, without authentication. This is a development tool, not a production admin console.

### Read, publish and resend messages

After updating the configuration, recreate the UI with `make kafka-ui`. For Kubernetes, apply `kubectl --context docker-desktop -n rent-kafka apply -f helm/kafka/ui-local.yaml` and wait for `kubectl --context docker-desktop -n rent-kafka rollout status deployment/kafka-ui`.

1. Open `rentals.events` → **Messages**, select the earliest offset to inspect retained messages, and start browsing. Expand a row to inspect its key and JSON value.
2. To publish, use **Produce Message**, set the key to the rental UUID and paste a valid rental-event JSON value. Keep the key and `rentalId` consistent.
3. To replay an event, copy its key and full JSON value and publish them again through **Produce Message**. Preserve `eventId` when testing consumer deduplication. The replay creates a new Kafka record; it does not modify the original one.
4. Refresh the message browser and check that the new record has a new offset in its partition.

Manual Kafka publishing bypasses the rental API and Outbox: it does not create or update a rental in PostgreSQL. Prefer a separate topic such as `rentals.events.sandbox` for free-form experiments. These unauthenticated write permissions are intended only for the local development cluster.

| Location | Bootstrap address |
| --- | --- |
| Java/CLI on the host | `localhost:9092` |
| API/UI inside Compose | `kafka:19092` |
| API in Kubernetes | `rent-kafka-bootstrap.rent-kafka.svc.cluster.local:9092` |

Kafka advertises broker addresses after initial connection. Connecting only a bootstrap port is not enough if the advertised addresses are unreachable; use the matching listener above.

## 3. Generate real rental events through the REST Client

Use HTTP Yac in VS Code and the existing [REST Client walkthrough](../requests/README.md). Complete its account/driver and car prerequisites; do not skip domain validation.

1. In `requests/http/rentals.http`, send `rentals_create`: expect 201 and note the returned rental ID.
2. In the Kafka UI or console, find `RentalOpened` with that ID as the key and `rentalId`.
3. Send `rentals_return`: expect 200 and then `RentalReturned` with the same key.
4. Inspect agreed daily/fine prices and final charge. `totalCents` is null in opening and a final charge in return; it does not assert payment occurred.
5. Repeating an invalid opening/return must follow the existing HTTP error contract and must not add a committed event.

HTTP success means the domain transaction and event are durable in PostgreSQL; it does not mean a consumer has processed the event.

## 4. Prove recovery instead of assuming it

With the application running:

```sh
docker compose stop kafka
```

Open a valid rental through the same requests. It should still return 201. Inspect pending rows (no admin HTTP endpoint is added):

```sh
docker compose exec postgres psql -U postgres -d rent_cars_dev -c \
  'SELECT sequence,event_id,rental_id,attempts,next_attempt_at,published_at FROM outbox_messages ORDER BY sequence;'
```

Restore Kafka, keeping its volume:

```sh
docker compose start kafka
```

Wait for the current retry deadline (at most five minutes of application backoff after repeated failures). Expect pending rows to become published and the event to appear in Kafka. Restarting the API also resumes from the persisted backlog. Do not delete volumes or recreate event IDs to recover.

An ambiguous send can be delivered twice. Compare `eventId`: future consumers must persist deduplication together with their database effect. Producer idempotence alone does not solve this. The automated test `givenAcknowledgedSendAndDatabaseRollbackWhenRetryingThenSameEventCanArriveTwice` demonstrates it deterministically.

## 5. Observe recommendations

`make dev` already starts recommendations alongside API and React. Inspect the
`recommendations` log prefix and the group in Kafbat. Do not start a second
consumer unless you are intentionally studying group rebalancing.

To stop/restart only this consumer for study, first stop `make dev`, then use
three separate terminals: `make api-dev`, `make web-dev` and
`make recommendations-dev`. Kafka keeps retained messages while this consumer
is stopped. See [the E2E walkthrough](local-stack-validation.md) and
[ACKs, partitions and hot partitions](kafka-concepts.md).

## 6. Kubernetes on Docker Desktop

The Kafka installation is separate from the application Helm release. Pinned lab versions: Strimzi 0.49.1 and Kafka 4.1.1, compatible with the project's Docker Desktop Kubernetes 1.32. Review version compatibility before upgrading.

```sh
kubectl --context docker-desktop get nodes
make k8s-kafka-up
kubectl --context docker-desktop -n rent-kafka get kafka,kafkanodepool,kafkatopic,pods,pvc
```

This installs the operator and a **single broker/controller with persistent storage** in namespace `rent-kafka`. Three topic partitions do not mean three replicas. There is no broker high availability, TLS or authentication in this local lab.

Publish API, web and recommendations with `./scripts/push-images.sh`, then run
`make k8s-up`. It now prepares Kafka first and deploys the application with
publication and the supporting consumer enabled by default. `make k8s-kafka-up`
remains useful when you only want the broker for a study session.

The chart bootstrap defaults to the internal Kafka DNS name. `make k8s-up`
resets Helm values; persist image/group settings in `helm/values.yaml` or use an
explicit values file with your own Helm command. `make k8s-up KAFKA_ENABLED=false`
omits recommendations and disables API publication, without deleting Kafka.
See the [consumer verification](../helm/README.md#verify-the-supporting-consumer)
for receipt logs and the distinction between pod readiness and consumer health.

To see messages from inside the cluster:

```sh
kubectl --context docker-desktop -n rent-kafka exec -it rent-combined-0 -- \
  /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server rent-kafka-bootstrap:9092 \
  --topic rentals.events --from-beginning --property print.key=true
```

For the browser UI (stop the Compose UI first to free 8084):

```sh
kubectl --context docker-desktop -n rent-kafka apply -f helm/kafka/ui-local.yaml
kubectl --context docker-desktop -n rent-kafka rollout status deployment/kafka-ui --timeout=180s
kubectl --context docker-desktop -n rent-kafka get service kafka-ui
```

Docker Desktop exposes its LoadBalancer at **http://localhost:8084**. The broker itself remains internal. Other Kubernetes providers may assign a different LoadBalancer address. The UI manifest is **local-only** and has no login; do not expose it on a shared/public cluster. Removing the application release does not remove Kafka or its PVC.

To exercise a broker restart:

```sh
kubectl --context docker-desktop -n rent-kafka delete pod rent-combined-0
kubectl --context docker-desktop -n rent-kafka wait --for=create pod/rent-combined-0 --timeout=120s
kubectl --context docker-desktop -n rent-kafka wait pod/rent-combined-0 --for=condition=Ready --timeout=300s
```

The operator recreates it using persistent storage. Generate a rental while it is recovering and inspect Outbox publication afterward. For longer failure tests use the Docker stop/start exercise above. Do not delete the PVC as an outage simulation.

## 7. Contract, metrics and checks

See [event contract](events/rentals.md). Tuning variables: `RENT_MESSAGING_ENABLED`, `KAFKA_BOOTSTRAP_SERVERS`, `RENT_MESSAGING_TOPIC`, `RENT_MESSAGING_POLL_INTERVAL` (default `1s`). Changing topic requires provisioning it first. Standard Spring Kafka environment properties allow authenticated/TLS external brokers; credentials belong in Secrets, not this local manifest.

Actuator/Micrometer records `outbox.pending`, `outbox.oldest.pending.seconds`, `outbox.send{result=acknowledged|failed}` and the `outbox.publish` observation. Use the existing OTLP observability stack; no additional public metrics endpoint is exposed. Send success counts Kafka acknowledgment, which can still be followed by database rollback. Outbox backlog and consumer lag are different measurements.

```sh
make check-api
cd apps/api
./mvnw -B -Dtest=RentalEventTest,RentalEventRecorderTest,RentalEventRecorderIntegrationTest,OutboxRepositoryTest,OutboxPublisherTest,OutboxScheduleTest,OutboxMessageTest test
```

The integration tests use disposable PostgreSQL/Kafka containers, including a paused broker, real records, retry, duplicate publication and competing database transactions. No notification or analytics service is needed to validate the producer.

Study checkpoints: explain commit vs acknowledgment; identify every crash window; distinguish application replay from producer idempotence; justify the serial relay's connection/throughput cost; explain why a stuck head blocks later events; design consumer deduplication before adding external side effects.

`make dev` now includes recommendations. Use `make recommendations-dev` only when running applications independently; do not start it alongside the full stack. See [the E2E walkthrough](local-stack-validation.md).
