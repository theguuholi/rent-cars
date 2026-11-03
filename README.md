# Rent Cars

Rent Cars is a car rental project built with Java, Spring Boot, and PostgreSQL. The API and web application manage a rental fleet, its vehicle categories and equipment specifications.


## Run the full stack locally

The Spring Boot API lives in `apps/api/` and the React frontend in `apps/web/`. Install JDK 25, Node.js 24.16.0 (also pinned in `apps/web/.node-version`), npm, Make and Docker with Compose v2. Start Docker. From the repository root, run:

```sh
make setup   # Install the frontend dependencies from the lockfile
make dev     # Start PostgreSQL, the Java API and Vite
```

Run `make setup` on a fresh checkout and after the npm lockfile changes. The Maven Wrapper downloads Java dependencies on the first API start. PostgreSQL listens on port 5432, the API on 8080 and the frontend on 5173. These ports must be available. If you already started the API or Vite separately, stop that instance before running the combined command.

Open the [web catalog](http://localhost:5173) when the API log shows `Started RentCarsApiApplication`. Flyway applies pending migrations at startup. Vite forwards `/api/categories` to the Java endpoint `/categories`. The combined command prints labeled API and web logs in one terminal. Press Ctrl+C to stop both applications. PostgreSQL stays up with its data until you run `make db-stop`.

### Run a single application

| Command from the repository root | What it starts |
| --- | --- |
| `make api-dev` | PostgreSQL and the Java API |
| `make web-dev` | Only Vite; run `make setup` first |
| `API_TARGET=http://127.0.0.1:8081 make web-dev` | Vite connected to the separately running Go API |
| `make db-up` / `make db-stop` | Start or stop the development database |

For frontend-only development, you can also set `API_TARGET` in `apps/web/.env.local` and restart Vite. The combined `make dev` command defaults to the Java API it starts. To choose a different Java port for both the API and proxy, use `SERVER_PORT=8082 make dev`.

## Try the API

With the API running, list the vehicle categories from any terminal:

```sh
curl -i -H 'Accept: application/json' http://localhost:8080/categories
```

Expect `200 OK` and a JSON array sorted by name in ascending order. Each entry contains a category UUID, a name and an optional description. For example, the seeded catalog includes this entry:

```json
{
  "id": "a99aaadc-ddd5-4fa9-9bbc-4927c48e01d5",
  "name": "HATCHBACK",
  "description": "Compact car with a rear hatch."
}
```

An empty catalog returns `200 OK` with `[]`.

- **From the editor:** install the [VS Code REST Client extension](https://marketplace.visualstudio.com/items?itemName=humao.rest-client), open [requests/categories.http](requests/categories.http), and click **Send Request** above the request. Set `@baseUrl` at the top of the file to target another environment. Requests are grouped by resource in `requests/`.
- **From the browser:** open [Swagger UI](http://localhost:8080/swagger-ui.html), expand `GET /categories`, and select **Try it out** followed by **Execute**.
- **For tooling:** read the generated [OpenAPI specification](http://localhost:8080/v3/api-docs).

### Manage the fleet

Open **Cars** in the web menu or use the [Cars REST Client](requests/cars.http). Register a car with a category ID and any existing specification IDs. The license plate is stored in uppercase and is immutable after registration. Availability defaults to true. Daily rates and fines use Brazilian reais. In the form enter `150,50` for R$ 150,50. The API represents the same value as `15050` integer cents.

Use `GET /cars` to list the fleet, `POST /cars` to register, and `GET`, `PATCH` or `DELETE /cars/{id}` for an individual car. A patch that omits `specificationIds` preserves equipment, while an empty array clears the links. Deleting a car preserves its catalog entries.

### Upgrade an existing fleet

Flyway V5 limits a car name to 100 characters, brand to 80, and license plate to 20. On a database already running V4, review any rows returned by this query before upgrading. Choose corrected values for those records before restarting the API. The migration fails if existing values exceed a limit.

```sql
SELECT id, name, brand, license_plate
FROM cars
WHERE char_length(name) > 100
   OR char_length(brand) > 80
   OR char_length(license_plate) > 20;
```

## Development database

The `dev` profile includes ten sample vehicle specifications. Start the API with `make api-dev` or `make dev`, then send `GET /specifications` from [the specifications REST Client](requests/specifications.http). Flyway runs the development seed after the schema migrations and preserves existing specifications with matching names. Other explicit profiles use only the shared migrations; set `SPRING_PROFILES_ACTIVE=prod` in production because the project's default profile is `dev`.

The local connection settings are in `apps/api/src/main/resources/application-dev.yaml`. The `rent_cars_dev` database uses port `5432`, with `postgres` as both the username and password. The port must be available for Docker to start the service.

To connect to the database, open another terminal at the repository root and run:

```sh
docker compose exec postgres psql -U postgres -d rent_cars_dev
```

In psql, use `\dt` to list tables and `\q` to quit.

## Java quality checks

Run these commands from the `apps/api` directory. Start Docker before running the full test suite so Testcontainers can launch PostgreSQL 17.

| Task | Command |
| --- | --- |
| Check Java formatting | `./mvnw spotless:check` |
| Apply formatting fixes | `./mvnw spotless:apply` |
| Run the tests and generate fresh JaCoCo coverage reports | `./mvnw clean verify` |
| Run mutation tests for the Java API | `./mvnw -Pmutation test-compile org.pitest:pitest-maven:mutationCoverage` |

**Before pushing**, run `./mvnw spotless:apply`, review the formatting changes, then run `./mvnw verify`. Spotless enforces formatting. SonarQube Cloud reports reliability, maintainability and security findings on pull requests.

### Read the reports

- **Test coverage:** open `target/site/jacoco/index.html` in a browser (on macOS, run `open target/site/jacoco/index.html`). Select a package or class to inspect uncovered lines and branches. Maven generates the report without enforcing a project-wide minimum. The Sonar quality gate requires **80% coverage on new code**, so a successful local build can still receive a failing PR check.
- **Mutation testing:** open `target/pit-reports/index.html` (on macOS, run `open target/pit-reports/index.html`). Review surviving mutations to see which changes the tests missed. The current profile targets all application classes and tests under the root Java package and has no minimum mutation score.
- **In GitHub Actions:** download `jacoco-coverage` from the Java quality workflow or `pitest-api` from the API mutation tests workflow. Mutation testing runs on Mondays, on demand, and on pull requests changing the API directory or its mutation workflow.

### SonarQube Cloud

Import the GitHub repository into SonarQube Cloud, choose CI-based analysis, and set the main branch to `master`. Configure the new-code coverage condition described above. In the GitHub repository settings, set the Actions secret `SONAR_TOKEN` and the variables `SONAR_ORGANIZATION` and `SONAR_PROJECT_KEY` using the identifiers displayed by Sonar. Missing settings fail verification on trusted branches. Run Java quality on the default branch to establish the baseline after merging this workflow.

For trusted branches, the Java verification job uploads the JaCoCo XML to Sonar and waits for the server's quality gate. A failed gate fails the job. Fork and Dependabot PRs run the build and tests without access to Sonar credentials.

## Web quality checks

GitHub runs the full Stryker suite on every PR touching the web application. A mutation score below 100% fails the job. See the [web mutation testing guide](apps/web/README.md#mutation-testing) for the local command and reports.

From `apps/web/`, use the Node version in `.node-version` and install dependencies with `npm ci`. Run `npm run format:check`, `npm run lint`, `npm run test:coverage` and `npm run build` to reproduce **Web verification**. Use `npm run format` to apply Oxfmt fixes or `npm run lint:fix` for automatic Oxlint fixes.

Vitest requires at least **95% each** for global line, statement, function and branch coverage across application TypeScript under `src/`. Tests, test helpers and declarations are excluded. Open `apps/web/coverage/index.html` for the HTML report; `apps/web/coverage/lcov.info` is available for tooling. Download **web-coverage** from the Web quality workflow to inspect a PR, including one that failed its coverage threshold.

## Contributor and agent workflow

Start with [AGENTS.md](AGENTS.md) for scope, local checks and how to propose an exception. Use `make check-api` for backend changes, `make check-web` for frontend changes, or `make check` for both. Start Docker for backend tests and run `make setup` to install web dependencies on a fresh checkout. The Java and web CI jobs call the same Make targets.
