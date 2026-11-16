# Study 3: ORM loading, joins and pagination

Goal: separate the HTTP requests visible in React from the SQL statements needed
to fulfill them. An HTTP request is not necessarily one SQL query, and one joined
SQL query is not necessarily cheap.

## Run the query-count experiments

With Docker and Java 25, from the repository root:

```sh
cd apps/api
./mvnw -B -Dtest=CarQueryCountTest test
# Optional: read the actual SQL alongside the assertions
./mvnw -B -Dtest=CarQueryCountTest -Dspring.jpa.show-sql=true test
```

[`CarQueryCountTest`](../../apps/api/src/test/java/com/github/theguuholi/rentcars/cars/CarQueryCountTest.java)
creates eight cars with two specifications and three image metadata rows each.
It enables Hibernate statistics only for this test context, clears counters
before each measured operation and uses real PostgreSQL. The HTTP tests finish
JSON serialization before checking the statement count. JDBC fixture writes are
outside those measurements. The tests do not read image bytes from object storage.

## Predict, then compare the current results

| Operation | Observed prepared SQL statements | Explanation |
| --- | ---: | --- |
| List first page, size 2 or size 6, with 8 matches | 3 | Page SELECT, count SELECT, eager specification-ID collection loading |
| List first page, size 12, with only 8 matches | 2 | Spring Data can infer the total from an incomplete first page and skip COUNT |
| Read one car detail with its category, two specifications and three images | 2 | Detail graph SELECT plus the eager specification-ID collection |
| Search 6 cars, then deliberately initialize 6 distinct lazy categories | 9 | The normal 3 queries plus one category query per car |

These counts are regression expectations for the current mapping, Hibernate version,
fixture and endpoint shape, not a universal JPA promise. Revisit them deliberately
when the API needs different data. Shared references can hide N+1: if every car has
the same category, the persistence context may only need one additional lookup.
That is why the explicit N+1 experiment creates distinct categories.

## Read the mappings and the API views

Inspect [`Car`](../../apps/api/src/main/java/com/github/theguuholi/rentcars/cars/Car.java)
and [`CarRepository`](../../apps/api/src/main/java/com/github/theguuholi/rentcars/cars/CarRepository.java):

- `search` joins category to filter it. A regular JPQL join is not a fetch join;
  it does not require Hibernate to initialize `car.category`.
- Category and expanded specifications are lazy. The summary JSON view does not
  serialize them, avoiding the deliberate N+1 illustrated in the test.
- `specificationIds` is an eager element collection with `FetchMode.SUBSELECT`.
  The list response includes these IDs, so this mapping still performs SQL even
  though expanded specifications are absent. Subselect fetching keeps its statement
  count from growing one per car; inspect its row scope in the logged SQL as well.
- `findDetailedById` uses a LOAD `EntityGraph` containing category, specifications
  and images. The detail JSON includes their data, while the eager ID collection
  still follows its mapping. `EntityGraph` does not mean exactly one statement.

The test counts through MockMvc, so extra loads caused by JSON serialization would
also be visible. Open Session in View can allow late loads instead of immediately
throwing a lazy-loading exception; do not use that as evidence that the loads are
free. Test the endpoint, not just the repository call.

## Why multiple collections make pagination subtle

For one car with two specifications and three images, a flat SQL join can return
six rows before Hibernate deduplicates entities. A raw `LIMIT 2` on joined rows may
contain only one distinct car. In `CollectionPagination`, the fixture's raw join
returns the first car twice, while the ORM query returns two distinct cars.

**Version-specific finding:** this repository currently resolves Hibernate
**7.4.13.Final**. Its SQL for a limited collection fetch wraps the limited car
selection in a derived table, then joins the collection. We inspected that SQL;
it is incorrect to claim that this version always paginates a collection fetch
in memory. This behavior is described in [Hibernate 7.4's release notes](https://docs.hibernate.org/orm/7.4/whats-new/).

Conceptually, the inspected shape is:

```sql
SELECT car_page.*, specification.*
FROM (
    SELECT * FROM cars
    WHERE brand = ?
    ORDER BY name, id
    OFFSET ? ROWS FETCH FIRST ? ROWS ONLY
) car_page
LEFT JOIN cars_specifications link ON link.car_id = car_page.id
LEFT JOIN specifications specification ON specification.id = link.specification_id;
```

This fixes parent-row pagination in the tested dialect/version; it does not remove
row multiplication, transferred bytes, hydration cost or the need for stable
ordering. Do not copy the full detail graph onto the list merely to reduce a query
counter. With other versions/dialects, inspect SQL again. An explicit ID page followed
by a bounded graph fetch is another design to evaluate when needed, not a required
rewrite of the current working search.

## Observe React without confusing caches

1. Run `make dev`, open the catalog, and open Network with Preserve log enabled.
2. Filter cars, then navigate to another page. Inspect query parameters and the
   response's `content`, `number`, `size`, `totalElements` and `totalPages`.
3. Open a detail. Its response already contains category and specifications with
   descriptions; React need not fetch the administrative catalogs to display them.
4. Image byte requests are separate HTTP requests from image metadata. Do not
   add them to the SQL count of the car-detail request.
5. Return to the list and reopen the detail. TanStack Query may serve cached data
   or refetch it. Count network requests separately from backend SQL statements.
6. Compare page sizes in the integration test. The assertion of 3 queries for
   both 2 and 6 cars is stronger evidence against per-car N+1 than testing one car.

Existing browser coverage is in `apps/web/e2e/catalog-search.pw.ts` and
`apps/web/e2e/public-cars.pw.ts`. These are UI journeys; the new PostgreSQL-backed
HTTP tests supply SQL measurements that a browser alone cannot provide.

## Questions for review

- Why does the category filter join not initialize the category association?
- Why can a page execute two statements in one case and three in another?
- Why can six joined rows represent only one car?
- Does eliminating all extra queries necessarily improve latency or memory use?
- Which changes to the summary JSON could accidentally introduce N+1?
- What evidence would justify changing fetch strategy or pagination?

Combine this with the [EXPLAIN lab](car-search-explain.md): statement count, rows,
buffers, database time and end-to-end latency answer different questions.
