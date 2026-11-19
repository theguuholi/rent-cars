# 0009 — Optional raw rental event archive

Status: proposed for review with the HDFS PR.

The recommendations service needs a Hadoop learning path without coupling the
rental transaction to analytics or introducing Spark before raw ingestion works.

Use an optional HDFS archive behind HttpFS, accessed through Spring RestClient.
Validate each Kafka event, publish an immutable JSON file identified by eventId,
and return from the listener only after successful archival or an identical
replay. Use unique temporary files plus atomic rename; reject conflicting reuse
of an eventId. Reuse the consumer's retry/DLT policy for failures.

Alternatives considered:

- Native Hadoop Java client: adds runtime dependencies whose documented Java
  compatibility does not currently establish support for this Java 25 service.
- Direct WebHDFS against NameNode: introduces client-visible DataNode redirects
  and additional networking requirements for host processes and Kubernetes.
- Date-only partitioned paths: allow the same eventId with a changed date to
  bypass identity checks. Keep raw identity stable; partition analytical output
  in a later compaction job.
- PostgreSQL as an intermediate archive: adds a second persistence stage that
  is unnecessary for the HDFS exercise. The API outbox remains separate.

Consequences: this is at-least-once delivery with idempotent final files, not a
transaction spanning Kafka and HDFS. One file per event is an intentional lab
trade-off and must be revisited for production volume. HttpFS adds a gateway,
local pseudo-authentication must be replaced for a secured cluster, and archival
must remain enabled once its consumer group begins acknowledging records.

See the [system design, failure analysis and testing walkthrough](../recommendations-hdfs.md).
