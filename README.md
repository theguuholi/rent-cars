# Rent Cars

Rent Cars is a car rental project built with Java, Spring Boot, and PostgreSQL. The API currently exposes the vehicle category catalog. Use it to build a category picker or show drivers what each body style means.


## Run the API locally

Install JDK 25, Make, and Docker with Compose v2. Start Docker, then run the following commands from the repository root:

```sh
make db-up
cd api
./mvnw spring-boot:run
```

The first command starts PostgreSQL 17 and waits until it accepts connections. The Maven Wrapper starts the API with the default `dev` profile. Flyway applies pending migrations, and Hibernate validates the schema. When the log shows `Started RentCarsApiApplication`, the API is available at `http://localhost:8080`.

## Try the API

With the API running, list the vehicle categories from any terminal:

```sh
curl -i -H 'Accept: application/json' http://localhost:8080/categories
```

Expect `200 OK` and a JSON array sorted by name in ascending order. Each entry contains a name and an optional description. For example, the seeded catalog includes this entry:

```json
{
  "name": "HATCHBACK",
  "description": "Compact car with a rear hatch."
}
```

An empty catalog returns `200 OK` with `[]`.

- **From the editor:** install the [VS Code REST Client extension](https://marketplace.visualstudio.com/items?itemName=humao.rest-client), open [requests/categories.http](requests/categories.http), and click **Send Request** above the request. Set `@baseUrl` at the top of the file to target another environment. Requests are grouped by resource in `requests/`.
- **From the browser:** open [Swagger UI](http://localhost:8080/swagger-ui.html), expand `GET /categories`, and select **Try it out** followed by **Execute**.
- **For tooling:** read the generated [OpenAPI specification](http://localhost:8080/v3/api-docs).

## Development database

The local connection settings are in `api/src/main/resources/application-dev.yaml`. The `rent_cars_dev` database uses port `5432`, with `postgres` as both the username and password. The port must be available for Docker to start the service.

To connect to the database, open another terminal at the repository root and run:

```sh
docker compose exec postgres psql -U postgres -d rent_cars_dev
```

In psql, use `\dt` to list tables and `\q` to quit.

## Stop the API and database

Press `Ctrl+C` in the API terminal. To stop PostgreSQL and keep its data, run this command from the repository root:

```sh
docker compose stop postgres
```

## Java quality checks

Run these commands from the `api` directory. Start Docker before running the full test suite so Testcontainers can launch PostgreSQL 17.

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
