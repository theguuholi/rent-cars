# Rent Cars

Rent Cars is a car rental project built with Java, Spring Boot, and PostgreSQL. The API currently exposes the vehicle category catalog. Use it to build a category picker or show drivers what each body style means.

## Vehicle categories

Call `GET /categories` to retrieve the full catalog in alphabetical order. The request takes no parameters. Each category has two public fields:

| Field | Type | Meaning |
| --- | --- | --- |
| `name` | string, up to 50 characters | Unique uppercase catalog name, such as `SUV`. |
| `description` | string, up to 255 characters | Short explanation of the body style that a client can display alongside its name. |

A fresh database includes these categories:

| Name | Description |
| --- | --- |
| HATCHBACK | Compact car with a rear hatch. |
| PICKUP | Truck with an open cargo bed. |
| SEDAN | Car with a trunk separate from the passenger compartment. |
| SUV | Sport utility vehicle. |

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

Expect `200 OK` and a JSON array sorted by name in ascending order. Each entry contains a name and a description. For example, the seeded catalog includes this entry:

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

From the API directory, run `./mvnw spotless:apply` to format Java sources and `./mvnw clean verify` to check formatting, run all tests with PostgreSQL 17 in Testcontainers, and generate coverage. Open `target/site/jacoco/index.html` locally or download the **jacoco-coverage** artifact from the Java quality workflow.

Run `./mvnw -Pmutation test-compile org.pitest:pitest-maven:mutationCoverage` to check whether the category unit tests detect changes to the entity. Open `target/pit-reports/index.html` and review surviving mutations. Extend the mutation profile's target classes and tests when adding rental rules. GitHub runs this workflow on Mondays, on demand, and on pull requests changing its configuration.

### SonarQube Cloud

Import the GitHub repository into SonarQube Cloud, choose CI-based analysis, and set the main branch to `master`. Choose a quality gate for new code (for example, 80% coverage on new code). In the GitHub repository settings, set the Actions secret `SONAR_TOKEN` and the variables `SONAR_ORGANIZATION` and `SONAR_PROJECT_KEY` using the identifiers displayed by Sonar. Missing settings fail verification on trusted branches. Run Java quality on the default branch to establish the baseline after merging this workflow.

For trusted branches, the Java verification job uploads the JaCoCo XML to Sonar and waits for the server's quality gate. A failed gate fails the job. Fork and Dependabot PRs run the build and tests without access to Sonar credentials.
