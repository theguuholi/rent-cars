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

## Avatar storage: choose your environment

Run the commands in this section **from the repository root**. Storage is configured on the Java API; the React application and REST Client use the same endpoints for every provider.

| Goal | Recommended setup | Where the files live |
| --- | --- | --- |
| Everyday development | `local` with `make dev` | `apps/api/.rent-cars/uploads` |
| Test the complete container stack | `compose.stack.yaml` | The dedicated `avatar_data` Docker volume |
| Test SDK behavior without a cloud account | `CloudImageStorageTest` | Disposable S3Mock and fake-gcs-server containers |
| Test a real development bucket | `s3` or `gcs` with development credentials | A separate private development bucket |
| Test local Kubernetes | Helm with `api.storage.provider=local` | The release's retained avatar PVC |
| Deploy using cloud storage | Helm with `s3` or `gcs` and a configured workload identity | A private bucket belonging to that environment |

### 1. Develop locally without cloud services

Start Docker, then run:

```sh
make setup
RENT_STORAGE_PROVIDER=local make dev
```

Open http://localhost:5173, sign in and select **My avatar**. For API-only development, use `RENT_STORAGE_PROVIDER=local make api-dev`. Set `RENT_STORAGE_DIRECTORY` to an absolute path if you want files outside the API's working directory. Restarting the API preserves those files; sign in again if your development JWT becomes invalid.

To run the API, web and database entirely in containers:

```sh
docker compose -f compose.stack.yaml up --build -d --wait
```

Open http://localhost:8081. This Compose file uses local storage and its own database volume. To stop while retaining data, run `docker compose -f compose.stack.yaml down`. Adding `--volumes` removes both database and avatar data. Keep backups of the database and image storage together.

### 2. Test S3 or Google Cloud Storage

Create a **private development bucket**, separate from production, and configure the provider credentials before starting the API. For S3, use the AWS SDK default credential chain, such as an authenticated AWS profile or temporary environment credentials:

```sh
RENT_STORAGE_PROVIDER=s3 \
RENT_STORAGE_BUCKET=your-dev-avatar-bucket \
RENT_STORAGE_REGION=us-east-1 \
make api-dev
```

For GCS, authenticate locally with `gcloud auth application-default login`, then:

```sh
RENT_STORAGE_PROVIDER=gcs \
RENT_STORAGE_BUCKET=your-dev-avatar-bucket \
make api-dev
```

In another terminal, run `make web-dev`. No frontend storage variables are needed. These commands run the API with the development profile and use a real bucket; they are not production deployment commands. See [provider permissions and credentials](../../docs/avatars.md#select-a-production-storage-provider) for bucket setup, workload identity and production settings.

Changing the provider or bucket does **not** copy existing images. For a fresh environment, use a separate database and bucket. For existing data, follow the [storage migration instructions](../../docs/avatars.md#kubernetes) before switching all API instances together. Do not share one database between instances configured for different storage locations.

To exercise both SDKs locally without credentials or cloud charges:

```sh
./apps/api/mvnw -f apps/api/pom.xml -Dtest=CloudImageStorageTest test
```

Docker must be running. Testcontainers starts the emulators on random ports and removes them afterward. `make check-api` includes these tests, PostgreSQL persistence tests and unit tests. Emulator success does not validate production IAM or network access.

### 3. Test on local Kubernetes

First follow the [Helm prerequisites, namespace and Secret setup](../../helm/README.md). Publish images containing this feature using the [image instructions](../../helm/README.md#use-published-images); prefer a unique tag for each build. A registry's existing `latest` tag does not automatically contain your local changes.

For Docker Desktop, select the context before creating the namespace and Secret:

```sh
kubectl config use-context docker-desktop
kubectl get nodes
```

For a new local installation, or one already using local storage, deploy with the existing Secret and your image tags. Replace `your-build-tag` with the published tag:

```sh
helm upgrade --install fleet ./helm --kube-context docker-desktop -n rent-cars \
  --set api.storage.provider=local \
  --set api.demoData=true \
  --set-string api.image.tag=your-build-tag \
  --set-string web.image.tag=your-build-tag \
  --set web.service.type=LoadBalancer --set web.service.port=8082 \
  --wait --timeout 6m
helm test fleet -n rent-cars --kube-context docker-desktop --logs
```

This uses the chart's default image repositories. Override the repositories and `existingSecret` if your setup differs. Enable `demoData` only on a private local cluster. Open http://localhost:8082, sign in and upload an avatar. Verify persistence after restarting the API:

```sh
kubectl --context docker-desktop -n rent-cars rollout restart deployment/fleet-api
kubectl --context docker-desktop -n rent-cars rollout status deployment/fleet-api --timeout=180s
```

Reload the page and verify the avatar is still readable. Flyway applies pending migrations during startup. To diagnose startup or storage failures, inspect `kubectl --context docker-desktop -n rent-cars logs deployment/fleet-api` and `kubectl --context docker-desktop -n rent-cars get pods,pvc`.

For cloud storage, use the [S3/GCS Helm values example](../../docs/avatars.md#kubernetes). Docker Desktop does not automatically provide an AWS or Google workload identity: setting a ServiceAccount name alone does not authenticate to a cloud bucket. Use local storage for the standard local-cluster walkthrough; verify cloud identity on the target platform or supply a separately configured development credential integration. Keep credentials out of the image, repository and frontend bundle.

### Verify the upload contract in every environment

Open [the REST Client walkthrough](../../requests/rent-cars.http). Set `baseUrl` to `http://localhost:8080` for the host API, `http://localhost:8081/api` for Compose, or `http://localhost:8082/api` for the Kubernetes example. Run the CSRF and login steps, then:

1. Upload the PNG fixture: expect **204**.
2. Read the avatar: expect **200**, `image/png` and `Cache-Control: no-store`.
3. Restart the API, sign in again if needed and read the same avatar.
4. Delete it: expect **204**, then **404** on the next read.

Removal clears the account reference immediately. Physical files are deleted by the scheduled cleanup after the grace period; see [consistency and cleanup](../../docs/avatars.md#consistency-and-cleanup).

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

## Car photo galleries

`GET /cars/{carId}/images` lists the public gallery and `GET /cars/{carId}/images/{imageId}` returns a normalized PNG. Administrators add one image with multipart `POST /cars/{carId}/images` and remove it with `DELETE /cars/{carId}/images/{imageId}`. The API returns the `CarImage` entity on creation, with a `Location` header. Storage configuration is shared with avatars; no additional bucket or local volume is required. See [the car photo walkthrough](../../docs/car-images.md) for permissions, validation, cleanup and REST Client examples.
