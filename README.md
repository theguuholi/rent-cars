# Rent Cars

A car rental API built with Java, Spring Boot, and PostgreSQL.

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
