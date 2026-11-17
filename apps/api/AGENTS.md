# Java API conventions

- Organize by domain under `com.github.theguuholi.rentcars`. Inspect `categories` for a read-only catalog and `specifications` for mutations. Introduce a service when it owns a business operation or transaction.
- Preserve each endpoint's public JSON, status codes, nullability and update semantics. For Specifications, an omitted update field preserves its value; an explicit null fails validation. Use the shared HTTP exception handler for cross-resource error mapping.
- Keep database guarantees in versioned Flyway migrations under `src/main/resources/db/migration/`. Add a migration for a schema change already applied elsewhere. Development-only seeds belong under `db/dev/`. Exercise upgrades with representative existing data when a migration touches it.
- Put transaction boundaries around the whole operation that needs atomicity. Use pessimistic locking only when the concurrency requirement justifies it. Verify uniqueness, rollback and competing writes against PostgreSQL through Testcontainers when relevant.
- Give each new production component a corresponding behavior-focused test suite. Use unit tests for transformations and orchestration, MockMvc for HTTP contracts and PostgreSQL for persistence guarantees. For a repository interface, test the queries or operations it declares.
- Use `categories/Category.java` as the reference when adding or changing a JPA entity. Use explicit imports, a protected no-argument constructor, field-level persistence and validation annotations aligned with Flyway, and short Javadoc for non-obvious domain rules.
- Document the entity itself with a class-level `@Schema` and annotate each property exposed by the API. Follow Category's annotated getters when they exist. For a getter-free entity, annotate the fields, make them discoverable by the schema generator and exclude internal audit fields. Include examples, read-only IDs, units, bounds and nullability where relevant.
- For new CRUDs, return the entity when an endpoint sends its representation. Follow [CarController](src/main/java/com/github/theguuholi/rentcars/cars/CarController.java) for the collection (`List<Car>`), detail and update (`Car`), and creation (`ResponseEntity<Car>` with a `Location` header). Deletion can return 204 with an empty body. Introduce a response DTO only when the public representation must differ from the entity, and explain that requirement in the PR.
- When writes need a JSON body, use one input-only `<Entity>Request` for creation and updates that share writable fields. Keep server-managed fields out of the request. Validate required values on creation and define how PATCH treats omitted fields, explicit nulls, zero, false and empty collections. A read-only resource needs no request class. Prefer native Jackson binding and scope stricter rules to the affected input type.
- In Given/When/Then HTTP tests, compare the response fields and types with the generated `/v3/api-docs` schema and exercise the resource's create and partial-update rules.
- Use one JUnit Jupiter `@Nested` group per application-defined method or HTTP endpoint under test. Name each scenario with Given/When/Then and cover the relevant success, boundary and error outcomes. Exercise private helpers through their callers. Inherited CRUD, generated accessors and framework behavior do not warrant standalone groups or tests.
- Update OpenAPI descriptions and the matching file under `requests/` when a public endpoint changes. Check the generated schema for changed payloads. Finish with the backend verification command in the root guide.

## Apply responsibility boundaries without mandatory layers

Follow the root [readability review](../../AGENTS.md#single-responsibility-and-human-readable-code). Use these boundaries when the operation needs them:

| Responsibility | Usual owner |
| --- | --- |
| Bind/validate HTTP input and choose the HTTP response | Controller and input Request |
| Coordinate a business operation and its transaction | Service |
| Protect domain invariants and valid state transitions | Entity or focused domain value |
| Express database queries and persistence operations | Repository, with database guarantees in migrations |
| Handle Kafka, storage or email protocol details | Focused integration component |

A simple catalog read can call its repository without a pass-through service. Do not introduce all these roles for every CRUD. Keep framework wiring separate from business decisions when both would otherwise obscure each other.

For example, opening a rental should read as a coherent operation: load the required state, check eligibility, change availability, persist the rental and record its event. Name useful steps, but preserve the transaction that makes the database writes atomic. A controller should not assemble Kafka records, and an entity should not call HTTP or storage clients. Do not move a write to another bean merely to shorten a method if that changes transaction or locking behavior.

Prefer explicit statements when a stream or callback chain hides writes, exception handling or operation order. Private helpers stay private and are tested through the operation they support; readability is not a reason to expose implementation details for tests.
