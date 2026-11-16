# Full-stack database study path

Use the application as a laboratory: trace a React action to HTTP, Spring, SQL
and the persisted result. The guides distinguish measured results from assumptions.

| Order | Topic | Experiment |
| --- | --- | --- |
| Prerequisite | [Relational integrity](rental-database-integrity.md) | Reject invalid relationships, dates and duplicate open rentals |
| Prerequisite | [ACID and rollback](rental-acid-rollback.md) | Fail after both writes; distinguish rollback from a lost HTTP response |
| 1 | [Isolation and concurrency](rental-isolation-concurrency.md) | Simultaneous HTTP requests: one 201, one 409, one open rental |
| 2 | [Indexes and execution plans](car-search-explain.md) | Seed disposable PostgreSQL and compare real EXPLAIN measurements |
| 3 | [ORM, joins and pagination](car-orm-pagination.md) | Count SQL through JSON serialization; expose N+1 and joined-row pagination |

Each topic has reading pointers, commands, expected observations, React exercises
and review questions. No production index or fetch strategy is changed by these labs.
