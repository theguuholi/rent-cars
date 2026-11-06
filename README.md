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

For container images and a local Kubernetes deployment, follow the [Docker and Helm guide](helm/README.md).

### Publish the latest application images

Authenticate with Docker Hub and run the script from the repository root:

```sh
docker login -u guuhworship
./scripts/push-images.sh
```

The script builds both images from the current working tree for the local Docker platform, then pushes `guuhworship/rent-cars-api:latest` and `guuhworship/rent-cars-web:latest`. Set `IMAGE_NAMESPACE` to publish to a different registry and namespace.

### Explore the API in VS Code

1. Start Docker and run `make api-dev` from the repository root. If `make dev` is already running, use that API.
2. Install the **REST Client** extension by Huachao Mao and open [the API walkthrough](requests/rent-cars.http). Click **Send Request** above each numbered request.
3. Run **1–2** to browse cars without signing in. Step 2 reads the first ID from step 1. Skip it if the fleet is empty.
4. Run **3–5** to get a CSRF cookie and header, sign in with the seeded dev admin (`test@test` / `test@tesT@dev`) and verify that your profile shows `ADMIN`. The empty `204` response in step 3 is expected.
5. Run **6–8** to explore categories and specifications. Each protected example reads the JWT from the named login response in this file. You only sign in once per token.
6. To practice writes, follow either optional CRUD exercise in order. The update and delete requests target the record returned by that exercise’s create request. The car exercise also uses the category and specification lists from steps 6–7.

If a protected request returns **401**, rerun steps 3–5 in the same file. Tokens expire after 15 minutes and restarting the dev API invalidates them. For **403** during login, rerun step 3 before signing in. For **403** on a catalog request, check the role in step 5.

## Car photos

Open a car's detail page to view its photos. Administrators can use **Add photos**
to select multiple JPEG or PNG files and **Upload photos** to append them to the gallery.
Create the car first when registering a new vehicle. Each photo must be at most
5 MiB, with less than 30 MiB selected per upload. Failed uploads preserve the
selection for retry. Click a photo to open its original size; failed image loads
have an individual retry button.

The gallery uses the existing public car image endpoint through `/api`.
TanStack Query refreshes the car detail (including its version) and fleet after an
upload; TanStack Form manages the selected files and validation. The web Nginx
proxy accepts request bodies up to 30 MiB. Any additional Ingress must also allow
that request size, and the API must have working local-volume or S3 storage.

## Explore the codebase

Follow [the developer walkthrough](docs/project-walkthrough.md) to try visitor, driver and admin access, trace a browser request into Spring and locate the tests for each layer.

## Repository layout

| Directory | Contents |
| --- | --- |
| [apps/api/](apps/api/README.md) | Java API, Maven Wrapper, Flyway migrations and backend tests |
| [apps/web/](apps/web/README.md) | React and TypeScript application, feature routes and frontend tests |
| [requests/](requests/) | Executable HTTP examples for cars, categories and specifications |
| [.github/workflows/](.github/workflows/) | CI workflows for verification, coverage and mutation testing |

From the repository root, run `make dev` to start PostgreSQL, the API and the frontend together. Use `make api-dev` or `make web-dev` to start a single application, and `make check` to verify both. See the [contributor guide](AGENTS.md) for development conventions.
