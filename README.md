# Rent Cars

Rent Cars is a fleet management application. Register vehicles, assign their category and equipment, and manage availability, daily rates and fines. The React frontend calls a Spring Boot API backed by PostgreSQL.

New to the project? Follow the [step-by-step onboarding tutorial](docs/getting-started.md): tools, React/Java startup, HTTP Yac requests, containers and Kubernetes.

The local stack also includes [Grafana observability](docs/observability.md) for API logs, metrics and traces and browser request traces. After starting `make dev`, follow [Step 3a of the onboarding tutorial](docs/getting-started.md#step-3a-verify-observability) to confirm that all three signals reach Grafana.

## Run the full stack locally

Install JDK 25, Node.js 24.16.0 with npm, Make, and Docker with Compose v2. Start Docker and make sure ports 5173 (web), 8080 (API), 5432 (PostgreSQL), 8333 (development S3), 8025 (email inbox), 1025 (development SMTP) and 3000 (Grafana) are available.

```sh
git clone https://github.com/theguuholi/rent-cars.git
cd rent-cars
make setup
make dev
```

Open the [web application](http://localhost:5173) or explore the endpoints in [Swagger UI](http://localhost:8080/swagger-ui.html). The API applies migrations and seeds the development catalogs on startup. `make dev` and `make api-dev` also start SeaweedFS, an S3-compatible object store with a persistent Docker volume and an automatically created `rent-cars-images` bucket. No cloud account or activation token is required. Press Ctrl+C to stop the applications and run `make infra-stop` to stop PostgreSQL, SeaweedFS, Mailpit and Grafana LGTM.

Password recovery emails are captured in Mailpit. Open the inbox that matches
how you started the application:

| Environment | Mailpit inbox |
| --- | --- |
| `make dev` or `make api-dev` | [http://localhost:8025](http://localhost:8025) |
| `docker compose -f compose.stack.yaml up --build -d --wait` | [http://localhost:8026](http://localhost:8026) |
| Kubernetes (`make k8s-up`) | Run `kubectl --context docker-desktop -n rent-cars port-forward svc/fleet-mailpit 8025:8025`, then open [http://localhost:8025](http://localhost:8025) |

The local API sends email to Mailpit over SMTP on port 1025. Production uses
the SMTP server configured in `application-prod.yaml` and the
`MAIL_FROM`/`WEB_BASE_URL` variables. See the [recovery walkthrough](docs/getting-started.md#step-9a-recover-a-drivers-password)
for the full flow.

For container images and a local Kubernetes deployment, follow the [Docker and Helm guide](helm/README.md). The Kubernetes chart also runs the local Grafana LGTM simulator; use the [observability walkthrough](helm/README.md#verify-observability) to inspect API traces, metrics and logs.

### Publish the latest application images

Authenticate with Docker Hub and run the script from the repository root:

```sh
docker login -u guuhworship
./scripts/push-images.sh
```

The script builds both images from the current working tree for the local Docker platform, then pushes `guuhworship/rent-cars-api:latest` and `guuhworship/rent-cars-web:latest`. Set `IMAGE_NAMESPACE` to publish to a different registry and namespace.

### Explore the API in VS Code

1. Start Docker and run `make api-dev` from the repository root. If `make dev` is already running, use that API.
2. Install the recommended **HTTP Yac** extension in VS Code and open `requests/http/auth.http`. Click **Send** above a request.
3. Local is the default. Follow the [HTTP Yac walkthrough](requests/README.md); tokens and IDs are shared across files.

Login requests capture separate administrator and driver tokens. Catalog queries and creation responses capture IDs and versions for the following requests. Credentials are development-only; tokens remain in runtime memory. On 401, sign in again. The walkthrough documents refresh, uploads, rentals and expected errors.

## Explore the codebase

Follow [the developer walkthrough](docs/project-walkthrough.md) to try visitor, driver and admin access, trace a browser request into Spring and locate the tests for each layer.

## Repository layout

| Directory | Contents |
| --- | --- |
| [apps/api/](apps/api/README.md) | Java API, Maven Wrapper, Flyway migrations and backend tests |
| [apps/web/](apps/web/README.md) | React and TypeScript application, feature routes and frontend tests |
| [requests/](requests/) | HTTP Yac files for authentication, catalogs, images and rentals |
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
and open http://localhost:8081. For Kubernetes, run `make k8s-up` with published images as described in
[the Helm guide](helm/README.md), then open http://localhost:8082.
In each environment check:

1. Sign in as the development admin, create a car and upload both Argo fixtures.
2. Open each original image, reload the page and confirm both photos remain.
3. Add another image; the previous images must remain visible.
4. Reject a non-JPEG/PNG file and a file larger than 5 MiB without altering the gallery.
5. Sign out: photos remain readable, but upload controls disappear. Anonymous
   upload/removal requests must return 401.
6. Restart the API, database and S3 service, then reopen the car and photos.
7. Use the HTTP Yac `car-images.http → car_images_delete` request: expect 204, then 404 when reading that image.
8. Delete the car and verify its remaining images are no longer available.

For Compose persistence checks, use
`docker compose -f compose.stack.yaml restart api postgres s3`, followed by
`docker compose -f compose.stack.yaml up -d --wait`. Do not delete volumes.
For Kubernetes use the rollout commands in the Helm guide and run
`helm test fleet -n rent-cars --logs` to verify the web proxy and API routing.
These checks validate the S3-compatible development setup; repeat the image
lifecycle against the real AWS bucket before deploying there to validate IAM
permissions and AWS-specific behavior.
