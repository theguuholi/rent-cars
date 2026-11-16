# Study 2: car search indexes and execution plans

Question: what work does PostgreSQL do when a person filters and pages through
the car catalog? Measure before choosing an index. This lab adds no production
migration and does not change `CarRepository`.

## Trace the full-stack search

1. Open the React car catalog and its Network panel. Change name, brand, category,
   page and sorting; inspect the `/api/cars` URL and JSON pagination metadata.
2. Read [`cars-api.ts`](../../apps/web/src/features/cars/cars-api.ts) and the feature's routes.
   A browser request may be skipped by TanStack Query's cache; that is different
   from making the database query faster.
3. Follow [`CarService.list`](../../apps/api/src/main/java/com/github/theguuholi/rentcars/cars/CarService.java)
   to [`CarRepository.search`](../../apps/api/src/main/java/com/github/theguuholi/rentcars/cars/CarRepository.java).
   Filters use `lower(...) LIKE '%fragment%' ESCAPE '!'`; the category predicate
   requires a join. Paging also needs total-count metadata.
4. SQL duration is only one part of browser latency: HTTP, authentication,
   Hibernate loading, serialization and React rendering are not measured by EXPLAIN.

## Run the disposable experiment

Requirements: Docker and Python 3. From the repository root:

```sh
python3 scripts/studies/car-search-plans.py
# Optional repeat with a different cardinality:
python3 scripts/studies/car-search-plans.py --rows 200000
```

The script creates a randomly named `postgres:17` container with no published
ports, applies the repository's versioned SQL migrations in numeric order, and
inserts 100,000 synthetic cars by default. It does not connect to your dev database.
It removes the container in `finally`; reports remain in the printed temporary
directory. Interrupting normally also runs cleanup. After a forced process kill,
remove only its `rent-car-plans-*` container if still present.

It executes a literal SQL equivalent of the search predicates, availability filter,
category join, name/id ordering and pagination. This isolates PostgreSQL's plan;
it is not Hibernate-generated SQL and does not model prepared/generic-plan caching
or the additional association queries studied in the next lab. Recheck this SQL
against the repository whenever search changes.

Three stages use the same data:

1. Existing schema only, with `ANALYZE` statistics.
2. Candidate B-tree on `lower(name)`.
3. Remove that B-tree, then test GIN `lower(name) gin_trgm_ops` with `pg_trgm`.

Each stage measures three repetitions of a selective fragment, its count query,
a common fragment, a two-character fragment, and a deeper offset. `EXPLAIN
(ANALYZE, BUFFERS, FORMAT JSON)` executes the SELECT and saves the full plan.
The script never disables sequential scans to manufacture index usage.

## Read the output, not just the elapsed time

- `environment.json`: actual PostgreSQL version, relevant settings and applied migrations.
- `summary.csv`: execution/planning time, result rows, approximate car rows visited,
  root shared hit/read buffers and car scan node types.
- `*.json`: full plan for each stage/query/repetition, including filters and loops.
- `*.sql`: exact query used for each scenario.

Compare `Actual Rows` with estimated `Plan Rows`, and inspect `Rows Removed by
Filter`, rechecks and `Actual Loops` at the car scan. The script's visited estimate
multiplies returned/removed rows by loops; parallel-plan averages can be rounded.
Do not interpret a LIMIT of 20 as only 20 examined rows. Do not sum root and child
buffer counters: parent counters already include descendant work. A shared-buffer
read is not proof of a physical disk read; the operating system may cache it.
See the [PostgreSQL EXPLAIN guide](https://www.postgresql.org/docs/current/using-explain.html).

## Measured example, not a production promise

[Recorded CSV](results/car-search/summary.csv) and
[environment](results/car-search/environment.json): 100,000 cars, PostgreSQL 17.11,
aarch64, shared buffers 128 MB, work memory 4 MB. Values below are medians of three
execution times; buffers and visited rows are from repetition three.

| Scenario | Baseline ms | B-tree ms | Trigram ms |
| --- | ---: | ---: | ---: |
| Rare name fragment, first page | 8.545 | 8.378 | 0.575 |
| Rare name fragment, count | 8.343 | 7.800 | 0.503 |
| Common fragment, first page | 18.206 | 19.270 | 18.431 |
| Two-character fragment | 18.672 | 18.583 | 18.116 |
| Common fragment, offset 10,000 | 25.364 | 24.700 | 25.529 |

The rare-page baseline used a sequential scan, about 100,000 visited car rows
(the parallel-plan estimate is 99,999) and 3,303 shared hits. The trigram candidate
used a bitmap heap scan, 100 visited car rows and 116 shared hits. Both recorded
zero shared reads on that repetition. Inspect the committed
[baseline](results/car-search/baseline-rare-page-3.json) and
[trigram](results/car-search/trigram-rare-page-3.json) plans yourself.

Data insertion, statistics collection and index construction warm caches; these
are not controlled cold-cache comparisons. Three runs are illustrative, not a
latency SLA. UUIDs and ANALYZE samples vary between runs.

The [pg_trgm documentation](https://www.postgresql.org/docs/17/pgtrgm.html) explains
why trigram indexes can help LIKE patterns without a fixed prefix. Pattern length
and selectivity still matter. Here broad/short filters kept sequential scans and
saw no useful gain. A normal B-tree, even on the matching lowercase expression,
was not a solution to the leading wildcard in this dataset.

## Decide what evidence an index proposal needs

- Real filter frequencies, text lengths, table size and value distribution.
- Both result-page and count-query plans; first and deep pages.
- Separate measurement of name, brand and category filters and combinations.
- Index size/build cost and INSERT/UPDATE overhead (not measured by this lab).
- Extension availability and migration strategy in the target environment.
- SQL produced by the actual Hibernate/driver version and prepared-plan behavior.

For now the result supports further evaluation of trigram search for selective
fragments, not installing it everywhere. Explain why an order-supporting B-tree
or keyset pagination might solve a different problem from substring matching.

Next: [ORM, joins and pagination](car-orm-pagination.md) counts the additional SQL
behind one HTTP request.
