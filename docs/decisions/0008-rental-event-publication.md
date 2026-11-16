# Rental event publication through a transactional outbox

Status: proposed for review in this PR.

## Decision

Keep rental writes authoritative in PostgreSQL. Persist a JSON snapshot and stable event ID in the same transaction as each opening/return. Spring Data JPA stores the outbox; Spring Kafka publishes it. Boot manages Kafka dependencies and auto-configuration. The application is a producer only; notifications and Spark belong to independent projects.

Use a polling publisher with one PostgreSQL transaction-scoped advisory lock shared by all application replicas. Each transaction sends one oldest pending message and waits for Kafka acknowledgment before marking it published. The schedule processes at most 20 messages, each in its own transaction, then waits one second. The lock is non-blocking to other publishers and released by PostgreSQL on commit, rollback or connection loss. Domain writes do not acquire it.

This deliberately simple relay holds one connection during a bounded network wait: up to one second for producer metadata/buffer allocation and five seconds for the returned future under the committed configuration. Producer delivery timeout is four seconds. It is not a high-throughput relay; measure queue age and pool impact before scaling. A failed head blocks later messages; backoff grows from two seconds to five minutes. A permanent error needs operator repair, not silent deletion or automatic abandonment. No producer DLQ is added.

For the same rental, return follows committed opening and the serial relay submits the opening first. Kafka uses rentalId as key. There is no global ordering across partitions and no absolute ordering of duplicate attempts: timeouts, process failure or late network delivery can yield old duplicates after later records. Consumers must deduplicate by eventId atomically with their effect; stateful consumers must also reject stale lifecycle transitions. Do not change partition count on a live topic without planning the key/ordering impact.

## Alternatives

- Direct send from RentalService: fewer classes, but database/broker commits are not atomic and an outage couples rental success to Kafka.
- After-commit in-memory listener: still loses publication on process failure.
- Leases/per-rental workers: more throughput but more coordination, lease fencing and stale-worker ordering complexity.
- CDC/Debezium: a future alternative if measured backlog justifies extra infrastructure.

The serial relay is selected for a bounded educational producer; these trade-offs are intentional and must be reviewed, not presented as a production capacity result.

## Delivery and contracts

Delivery is at-least-once assuming pending data is retained and failures are eventually repaired. Producer idempotence does not deduplicate application-level replay after PostgreSQL rollback. A successful Kafka acknowledgment can be followed by a database failure; tests intentionally demonstrate two records with the same eventId. Send metrics count acknowledgments, not atomic database publication commits.

Events are JSON records independent of JPA entities, package names and HTTP responses. Account IDs are pseudonymous identifiers and still need access controls/retention policies; no email, password, token or license plate is published. A future notifier must define an authenticated recipient lookup or a narrowly scoped recipient contract; it must not query this application's database directly. Snapshotted agreed prices and final charges are not payment receipts. Existing historical rentals are not backfilled.

## Operations

Recording is always active. RENT_MESSAGING_ENABLED controls only the relay and defaults false. Disabling it accumulates a backlog, never silently drops events. Local Make/Compose workflows enable it. Keep Kafka out of the API readiness dependency so broker outages do not stop rental requests. Monitor pending count, oldest age, failures and database capacity. No metrics endpoint is exposed publicly.

Retention: Kafka local topic retains seven days (time retention is approximate and segment-based); this is a study setting, not an approved business policy. The outbox retains all records initially. Operators may delete only acknowledged records after an agreed retention/backup window; do not use cleanup to discard pending messages. Before production, define retention, topic ACLs, TLS/authentication, replication, storage alerts and recovery objectives.
