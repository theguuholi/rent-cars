# Study: replication, partitioning and cache

This is a design exercise, not authorization to add infrastructure. Complete the [pool and capacity lab](pool-capacity.md) first. Preserve rental correctness even when a catalog is stale.

## 1. Classify reads before choosing a technology

Trace the controllers, `RentalService`, `CarRepository`, and the React query keys/invalidation paths. Review `V10__create_rentals.sql`, including `rentals_open_car` and `rentals_open_account`.

| Read or operation | Proposed freshness requirement | Routing decision to evaluate |
| --- | --- | --- |
| Public car names, descriptions, images, category/specification labels | A bounded delay may be acceptable; product owner must define it | Candidate for a future read replica or cache after measurement |
| Catalog availability and displayed price | A browsing hint, never authority to rent or quote the final transaction | Display may lag only with explicit UX and authoritative revalidation |
| Open rental, final price calculation, car/account eligibility | Must use authoritative state within the transaction | Primary; keep locks, constraints and rollback guarantees |
| Return rental and release availability | Must commit consistently | Primary; do not report successful availability before commit |
| User's rentals immediately after a mutation | Read-your-writes | Primary, or a proven consistency mechanism; blind replica routing is insufficient |
| Session, role and authorization decisions | Security-sensitive; no generic stale catalog policy | Keep existing authentication/authorization semantics and isolate user data |

These are proposed boundaries, not an approved staleness SLA. Specify an owner and an explicit maximum acceptable delay for each candidate read before implementation.

- [ ] Draw Browser → API → primary for current requests.
- [ ] Mark potential stale-tolerant paths in a second drawing.
- [ ] Explain why a GET is not automatically safe to send to a replica.

## 2. Walk through a stale catalog fullstack

Use two browser sessions and a disposable local dataset. Both users inspect the same available car; user A rents it first. User B attempts to rent from their already-open view.

```text
A and B read "available" → A's rental transaction commits
B submits stale choice → primary checks current state → conflict
B sees the conflict → refresh authoritative state → choose another car
```

- [ ] Inspect the existing HTTP conflict handling and UI recovery, including the case of a lost response after a successful commit.
- [ ] Verify the persisted invariant: one open rental for the car and one per account.
- [ ] Explain why disabling a button or trusting cached availability cannot enforce that invariant.
- [ ] Repeat the drawing with a replica that has not replayed A's commit. Refetching alone must not be claimed to guarantee freshness.

Reuse the [ACID/rollback lab](rental-acid-rollback.md) for executable transaction and recovery checks. This exercise does not install or simulate a real replication topology; mark any replication conclusions as design reasoning until tested.

## 3. Separate caches and their failure modes

TanStack Query is a client-side server-state cache. PostgreSQL buffers cache database pages. A CDN/HTTP cache and Redis would introduce different sharing and invalidation boundaries; none makes transaction checks optional.

- [ ] Inspect current query keys: filters, pagination and user scope must distinguish results.
- [ ] Confirm sensitive user state is cleared when the session changes.
- [ ] Sketch future keys and TTLs only for measured hot, stable reads. Do not globally cache sessions or another user's rentals.
- [ ] Explain the race: a reader fetches an old value, a writer commits and invalidates, then the reader repopulates the cache with the old value.
- [ ] Explain why invalidating before commit can publish old data, and why after-commit invalidation alone does not solve stale refill or replica lag.

A design must specify bounded staleness, invalidation ownership, failure behavior and observability. Consider versioned values or authoritative bypass where justified. Do not add a distributed cache merely because a client cache exists.

## 4. Evaluate a replica with explicit consistency

Asynchronous replication can lag committed writes. A read replica can offload eligible reads, but does not increase primary write capacity. Failover can lose writes not yet replicated; availability and recovery objectives need a separate decision. Read the [PostgreSQL standby documentation](https://www.postgresql.org/docs/17/warm-standby.html).

Write down: measured read bottleneck, allowed lag, lag monitoring, read-your-writes policy, fallback to primary, connection budget, failure recovery and tests. A time-based sticky-primary window is a heuristic unless tied to a proven lag bound. No replica is introduced by this PR.

## 5. Evaluate partitioning and sharding separately

Partitioning divides a table inside a database. Sharding distributes ownership across databases. Neither is a substitute for inspecting query plans and data volume.

For a hypothetical date-partitioned rental history, inspect retention, table/index size, pruning opportunities and maintenance pressure. PostgreSQL partitioned-table unique constraints must include partition keys. A unique open-car or open-account index on each date partition would **not** enforce uniqueness across all dates. Preserve global rental invariants before considering such a design. See [PostgreSQL partitioning limitations](https://www.postgresql.org/docs/17/ddl-partitioning.html#DDL-PARTITIONING-DECLARATIVE-LIMITATIONS).

For sharding, test the ownership conflict on paper: account-based shards can split one car's rentals; car-based shards can split one account's rentals. Both can require cross-shard coordination to preserve the current two uniqueness rules. Include distributed transaction, operational and recovery costs.

- [ ] Identify the measured volume/retention problem that partitioning would solve, or write “none demonstrated”.
- [ ] Draw which shard owns each invariant and how a competing write is rejected.
- [ ] Reject designs that silently weaken database guarantees.

## 6. Record the decision and review it

Complete this record in the PR discussion, step by step:

| Item | Your evidence or decision |
| --- | --- |
| Workload and measured bottleneck | Link the pool report and any relevant SQL plans |
| Stale-tolerant reads | List fields, maximum delay and owner |
| Authoritative operations | List transactions and immediate reads |
| Alternatives | Existing primary/client cache; query optimization; replica; shared cache |
| Selected approach | Default: keep the primary and existing client cache until evidence justifies more |
| Revisit trigger | A measurable unmet latency/throughput, retention or maintenance target, agreed with its owner |
| Correctness tests | Competing rentals, read-after-write, lag, invalidation races and cross-user isolation |
| Operations | Connection budget, monitoring, failure recovery and rollback |

Expected outcome: a defensible **do not add yet** decision is valid. Do not add a replica, cache, partition or shard without the recorded need and a design preserving current invariants.
