# Rent Cars API

The Spring Boot API manages cars, vehicle categories and equipment specifications. Start the stack with the [local setup guide](../../README.md#run-the-full-stack-locally). Browse the running API in [Swagger UI](http://localhost:8080/swagger-ui.html) or open a [REST Client request](../../requests/).

## Accounts and JWT sessions

In [the API walkthrough](../../requests/rent-cars.http), use the CSRF, admin login and profile requests to verify the seeded development account. The separate driver registration example is optional. First call `GET /csrf` and use its `XSRF-TOKEN` cookie and `X-XSRF-TOKEN` response header on the registration and login POSTs. The REST Client examples carry both forward, and Swagger UI sends the CSRF header automatically. A missing or mismatched cookie/header pair returns 403.

Register with a unique email and a password of at least 12 characters and at most 72 UTF-8 bytes. Registration returns the public profile with role `USER`. Email comparisons ignore case and surrounding whitespace.

For the seeded development admin, sign in with `test@test` and `test@tesT@dev`. The login response contains an `accessToken` valid for 15 minutes. The profile request automatically uses that token. In Swagger UI, copy the token from the login response, select **Authorize**, paste the token without the Bearer prefix and execute `GET /sessions/me`. Sign in again after expiration.

Fleet browsing is public through `GET /cars` and `GET /cars/{id}`. The detail includes a `category` object and an alphabetical `specifications` array. Each catalog entry exposes its ID, name and description. The car also retains `categoryId` and `specificationIds`. Clients can display these names directly. Creating, editing or deleting a car and all category and specification endpoints require an `ADMIN` token. An anonymous request to those endpoints receives 401. A signed-in driver receives 403. Signup and login remain public, as does the Swagger documentation.

Flyway creates this admin only when the `dev` migrations are enabled. If that email already exists, the seed preserves its password and role. The protected catalog requests reuse the named login response in the same walkthrough.

Development and test profiles generate an ephemeral RSA key pair at startup. Restarting invalidates local tokens. For deployment, activate `prod`, serve the API over HTTPS and supply the same key files to every replica. Mount a PKCS#8 RSA private key and its X.509 public key from your secret store. Configure their Spring resource locations, for example:

```sh
export SPRING_PROFILES_ACTIVE=prod
export RENT_AUTH_PRIVATE_KEY=file:/run/secrets/rent-cars-private.pem
export RENT_AUTH_PUBLIC_KEY=file:/run/secrets/rent-cars-public.pem
```

## Development database

The `dev` profile includes ten sample vehicle specifications. Start the API with `make api-dev` or `make dev`, then send `GET /specifications` from [step 7 of the API walkthrough](../../requests/rent-cars.http). Flyway runs the development seed after the schema migrations and preserves existing specifications with matching names. Other explicit profiles use only the shared migrations; set `SPRING_PROFILES_ACTIVE=prod` in production because the project's default profile is `dev`.

The local connection settings are in [application-dev.yaml](src/main/resources/application-dev.yaml). The `rent_cars_dev` database uses port `5432`, with `postgres` as both the username and password. The port must be available for Docker to start the service.

To connect to the database, open another terminal at the repository root and run:

```sh
docker compose exec postgres psql -U postgres -d rent_cars_dev
```

In psql, use `\dt` to list tables and `\q` to quit.

## Java quality checks

Run these commands from this directory. Start Docker before running the full test suite so Testcontainers can launch PostgreSQL 17.

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

## Concurrent catalog edits

Car and specification responses include a read-only numeric version. Send it as expectedVersion on every PATCH or PUT. Creation needs no version. Missing versions return 400. Stale forms and competing updates return 409 with an application/problem+json response. Reopen the editor to read the latest data before resubmitting, even if another user changed a different field. The REST Client examples use the version from the last response.

### Renewing a session

Sign-in sets the HTTP-only `rent_refresh` cookie. Keep the 15-minute access JWT in memory. Send the cookie and CSRF header to `POST /sessions/refresh` to rotate it and obtain a new JWT with current permissions. Fetch `GET /csrf` first and echo its `X-XSRF-TOKEN` header. The browser sends both cookies automatically on same-origin requests.

Each login starts a separate device session with an absolute seven-day lifetime (`rent.auth.refresh-lifetime`). Reusing a consumed refresh token revokes that device session. Serialize refresh requests across tabs. Use `DELETE /sessions/current` with the same CSRF proof to revoke renewal and expire the cookie. An issued JWT stays valid until expiry. Use HTTPS in production and activate a production profile with secure cookies. Only the development profile permits the refresh cookie over HTTP.


## Car images

The existing `CarController`, `CarService` and `CarRepository` handle images as
part of the car aggregate. JPA cascade/orphan removal persists the association.
There is no separate image controller/repository, scheduler or cleanup queue.

- `POST /cars`: existing JSON or multipart with one `car` JSON part and repeated
  optional `images` file parts. Creation returns the existing summary and Location.
- `PATCH /cars/{id}`: JSON or multipart. Include `expectedVersion` in the `car`
  JSON part. New files are appended; `removeImageIds` explicitly selects old images
  to remove. Omitted or empty selections retain images. IDs must belong to the car.
- `GET /cars/{id}`: detail includes image `id`, `contentType` and `sizeBytes`.
- `GET /cars/{id}?imageId={imageId}`: image bytes through the same public car route.
- `DELETE /cars/{id}`: deletes the car, its image rows and associated files.

Existing permissions remain unchanged: public car reads, ADMIN mutations. The
React client is unchanged. Follow **Upload example 1–8** in
[`requests/rent-cars.http`](../../requests/rent-cars.http); two AI-generated PNG views of the same silver Fiat Argo
are included, so no local file edits are needed.

Spring handles multipart parsing and the 5 MiB/file and 30 MiB/request limits.
The service checks JPEG/PNG contents because multipart support does not validate
image bytes. Decoded images are limited to 20 million pixels. Invalid images
return 422; excessive upload size returns 413. Tomcat drains up to 32 MiB of
rejected data; much larger rejected requests may terminate the connection.

### Local and S3 configuration

The cars service generates `cars/{carId}/{imageId}.jpg` or `.png`; the database
stores the complete key. Both adapters use that key without adding a second prefix.

The default `dev` profile stores files in `./uploads`, relative to the Java process
working directory (`apps/api` with `make api-dev`). Set `UPLOAD_DIRECTORY` to an
absolute directory if desired. Use a persistent volume in containers and restrict
write access to the application. Files are never cleared at startup.

`application-prod.yaml` uses an existing private S3 bucket:

```dotenv
SPRING_PROFILES_ACTIVE=prod
DATABASE_URL=jdbc:postgresql://database-host:5432/rent_cars
DATABASE_USERNAME=rent_cars
DATABASE_PASSWORD=your-database-password
S3_BUCKET=your-existing-private-bucket
AWS_REGION=sa-east-1
# Also supply the existing RENT_AUTH_PRIVATE_KEY and RENT_AUTH_PUBLIC_KEY settings.
```

The SDK uses an explicit default credentials provider chain. Prefer an attached
IAM role; never put access keys in YAML. Grant `s3:PutObject`, `s3:GetObject` and
`s3:DeleteObject` on `arn:aws:s3:::YOUR_BUCKET/cars/*`. The API serves private objects
using its own credentials. Provision the bucket/region separately. For versioned
buckets, configure noncurrent-version expiration if old versions must be reclaimed.
Changing providers requires copying existing files with the same keys first.

### File lifecycle

A transaction callback deletes removed files only after the database commits,
and attempts to delete newly uploaded files on rollback. A failed deletion logs
the key for manual cleanup; there are no automatic retries. Process termination
can also leave orphan files, and direct SQL/cascaded category deletion bypasses
this service-level file cleanup. This implementation deliberately does not add
background infrastructure for those cases.

V9 removes the queue/trigger from the initial PR version while preserving the V8
checksum for developers who already ran it. Existing image metadata is retained.
Tests cover transactions, content checks, storage failures, actual HTTP upload
limits and migration. S3 tests use the real SDK against an HTTP stub; live IAM and
bucket connectivity require validation in the deployment environment.
