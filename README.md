# Rent Cars

Rent Cars is a fleet management application. Register vehicles, assign their category and equipment, and manage availability, daily rates and fines. The React frontend calls a Spring Boot API backed by PostgreSQL.

## Run the full stack locally

Install JDK 25, Node.js 24.16.0 with npm, Make, and Docker with Compose v2. Start Docker and make sure ports 5173 (web), 8080 (API), 5432 (PostgreSQL), and 8333 (development S3) are available.

```sh
git clone https://github.com/theguuholi/rent-cars.git
cd rent-cars
make setup
make dev
```

Open the [web application](http://localhost:5173) or explore the endpoints in [Swagger UI](http://localhost:8080/swagger-ui.html). The API applies migrations and seeds the development catalogs on startup. `make dev` and `make api-dev` also start SeaweedFS, an S3-compatible object store with a persistent Docker volume and an automatically created `rent-cars-images` bucket. No cloud account or activation token is required. Press Ctrl+C to stop the applications and run `make infra-stop` to stop PostgreSQL and the S3-compatible development store.

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
that request size, and the API must have working S3-compatible storage.

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

## Development S3 storage

Both development Compose files use SeaweedFS 4.52. The API uses its existing S3
adapter with path-style requests, and the browser continues to access images
through `/api/cars/{id}?imageId={imageId}`. Only the S3 port is published locally,
at `http://localhost:8333`; the full container stack keeps it internal.

```sh
# Spring and React on the host, PostgreSQL and S3 in Docker:
make setup
make dev

# Alternatively, everything in Docker (open http://localhost:8081):
docker compose -f compose.stack.yaml up --build -d --wait
```

The Make targets provide the development-only AWS credentials. When launching
Spring directly from an IDE, set `AWS_ACCESS_KEY_ID=rent-cars-dev` and
`AWS_SECRET_ACCESS_KEY=rent-cars-dev-secret`. These are public local credentials,
not AWS credentials. Production still uses the default AWS credential chain.

Images survive container recreation in the `s3_data` volume. `make infra-stop`
and `docker compose -f compose.stack.yaml down` preserve volumes; removing
volumes discards their data. Existing disk uploads are not automatically moved
into the new bucket. See the [API storage guide](apps/api/README.md#s3-configuration)
for migration instructions and the [Kubernetes guide](helm/README.md) for the
same storage setup in a cluster.

## Verify the complete environment

Run `make check-api` and `make check-web` with Docker running. Backend image HTTP
tests use PostgreSQL and a real SeaweedFS container, including failed upload
rollback, ownership, authorization and object deletion. Every runtime uses the
same S3 implementation; endpoint, bucket, region and credentials select the service.

For a full local stack, run `docker compose -f compose.stack.yaml up --build -d --wait`
and open http://localhost:8081. For Kubernetes, build/load both application images
and follow [the Helm guide](helm/README.md), then open the forwarded web address.
In each environment check:

1. Sign in as the development admin, create a car and upload both Argo fixtures.
2. Open each original image, reload the page and confirm both photos remain.
3. Add another image; the previous images must remain visible.
4. Reject a non-JPEG/PNG file and a file larger than 5 MiB without altering the gallery.
5. Sign out: photos remain readable, but upload controls disappear. Anonymous
   upload/removal requests must return 401.
6. Restart the API, database and S3 service, then reopen the car and photos.
7. Use the REST Client removal endpoint: expect 204, then 404 when reading that image.
8. Delete the car and verify its remaining images are no longer available.

For Compose persistence checks, use
`docker compose -f compose.stack.yaml restart api postgres s3`, followed by
`docker compose -f compose.stack.yaml up -d --wait`. Do not delete volumes.
For Kubernetes use the rollout commands in the Helm guide and run
`helm test fleet -n rent-cars --logs` to verify the web proxy and API routing.
These checks validate the S3-compatible development setup; repeat the image
lifecycle against the real AWS bucket before deploying there to validate IAM
permissions and AWS-specific behavior.
