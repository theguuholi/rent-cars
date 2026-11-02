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
