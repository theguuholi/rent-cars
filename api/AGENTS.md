# Java API conventions

- Organize by domain under `com.github.theguuholi.rentcars`. Inspect `categories` for a read-only catalog and `specifications` for mutations. Introduce a service when it owns a business operation or transaction.
- Preserve each endpoint's public JSON, status codes, nullability and update semantics. For Specifications, an omitted update field preserves its value; an explicit null fails validation. Use the shared HTTP exception handler for cross-resource error mapping.
- Keep database guarantees in versioned Flyway migrations under `src/main/resources/db/migration/`. Add a migration for a schema change already applied elsewhere. Development-only seeds belong under `db/dev/`. Exercise upgrades with representative existing data when a migration touches it.
- Put transaction boundaries around the whole operation that needs atomicity. Use pessimistic locking only when the concurrency requirement justifies it. Verify uniqueness, rollback and competing writes against PostgreSQL through Testcontainers when relevant.
- Give each new production component a corresponding behavior-focused test suite. Use unit tests for transformations and orchestration, MockMvc for HTTP contracts and PostgreSQL for persistence guarantees. For a repository interface, verify its actual query behavior.
- Update OpenAPI descriptions and the matching file under `requests/` when a public endpoint changes. Check the generated schema for changed payloads. Finish with the backend verification command in the root guide.
