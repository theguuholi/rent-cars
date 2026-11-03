# Rent Cars

Rent Cars is a fleet management application. Register vehicles, assign their category and equipment, and manage availability, daily rates and fines. The React frontend calls a Spring Boot API backed by PostgreSQL.

## Run the full stack locally

Install JDK 25, Node.js 24.16.0 with npm, Make, and Docker with Compose v2. Start Docker and make sure ports 5173 (web), 8080 (API), and 5432 (PostgreSQL) are available.

```sh
git clone https://github.com/theguuholi/rent-cars.git
cd rent-cars
make setup
make dev
```

Open the [web application](http://localhost:5173) or explore the endpoints in [Swagger UI](http://localhost:8080/swagger-ui.html). The API applies migrations and seeds the development catalogs on startup. Press Ctrl+C to stop the applications and run `make db-stop` to stop PostgreSQL.

### Development admin

The `dev` profile seeds an `ADMIN` account with email `test@test` and password `test@tesT@dev`. Start the API, then run the CSRF, admin login and profile requests in [sessions.http](requests/sessions.http). The profile returns `"role": "ADMIN"`.

## Repository layout

| Directory | Contents |
| --- | --- |
| [apps/api/](apps/api/README.md) | Java API, Maven Wrapper, Flyway migrations and backend tests |
| [apps/web/](apps/web/README.md) | React and TypeScript application, feature routes and frontend tests |
| [requests/](requests/) | Executable HTTP examples for cars, categories and specifications |
| [.github/workflows/](.github/workflows/) | CI workflows for verification, coverage and mutation testing |

From the repository root, run `make dev` to start PostgreSQL, the API and the frontend together. Use `make api-dev` or `make web-dev` to start a single application, and `make check` to verify both. See the [contributor guide](AGENTS.md) for development conventions.
