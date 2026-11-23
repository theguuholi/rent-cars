# Rent Cars

Rent Cars is a car rental application built to practice Java, React and system design. Visitors browse cars; drivers register, sign in, rent and return vehicles; administrators manage cars, categories, specifications and photos.

**Start with the product: React + Spring Boot + PostgreSQL.** Once you can complete a rental, use the supporting services to study storage, email, observability and asynchronous events. You do not need to understand all the infrastructure before using the application.

## 1. Run your first rental locally

Install **JDK 25**, **Node 24.16.0 with npm**, **Make** and **Docker with Compose v2**. Start Docker, then run:

```sh
git clone https://github.com/theguuholi/rent-cars.git
cd rent-cars
make setup
make dev
```

Keep this terminal open. `make setup` installs the frontend dependencies. `make dev` starts the API, React and recommendations on your machine, with PostgreSQL and the supporting infrastructure in Docker. The API applies Flyway migrations and development seeds automatically.

1. Open [React](http://localhost:5173) and browse the cars as a visitor.
2. Sign in as the development administrator: `test@test` / `test@tesT@dev`. Explore categories, specifications and car editing.
3. Sign out, register a driver and complete a rental/return from a car's detail page.
4. Open [Swagger](http://localhost:8080/swagger-ui.html) to see the HTTP contracts behind those screens.

Expected result: a rented car becomes unavailable; returning it makes it available again. The rental history remains visible.

Need help with tools, roles or request order? Follow the [step-by-step onboarding](docs/getting-started.md). It includes checkpoints and troubleshooting. These accounts and seeds are for local learning only.

## 2. Know what each part does

The main request path is:

```text
Browser → React → Spring Boot API → PostgreSQL
                          ├─ photos → S3-compatible storage
                          ├─ recovery email → SMTP
                          └─ rental + outbox → Kafka → optional event consumers
```

| Part | Responsibility | When to explore it |
| --- | --- | --- |
| [React](apps/web/README.md) | Screens, forms, authentication and HTTP calls | Start here to use the product |
| [Spring Boot API](apps/api/README.md) | Business rules, security, persistence and rental transactions | Trace a request or implement a feature |
| PostgreSQL | Accounts, fleet, rentals and transactional outbox | Study constraints, transactions and concurrency |
| SeaweedFS | Local S3-compatible photo storage | Upload/read/delete car and profile images |
| Mailpit | Captures development email | Test password recovery without sending real email |
| Grafana LGTM | API logs, metrics and traces | Investigate requests and failures |
| Kafka + Kafbat | Retains rental events and lets you inspect them | Study publication, partitions, offsets and replay |
| [Recommendations consumer](apps/recommendations/README.md) | Validates and logs rental event receipts | Study a separate consumer after completing the rental flow |

**Recommendations is a supporting learning service, not part of the synchronous rental request.** It currently consumes `RentalOpened` and `RentalReturned`; it does not calculate suggestions or use Spark/Hadoop yet. Stopping it does not prevent the API from completing a rental. Events can be consumed later, within Kafka's retention period.

## 3. Open the development tools

`make dev` starts these interfaces:

| Interface | URL | Try this |
| --- | --- | --- |
| React | http://localhost:5173 | Browse, rent and return a car |
| Swagger | http://localhost:8080/swagger-ui.html | Inspect API schemas and endpoints |
| Mailpit | http://localhost:8025 | Read a password recovery email |
| Grafana | http://localhost:3000 | Follow the [observability walkthrough](docs/observability.md) |
| Kafka UI | http://localhost:8084 | Inspect `rentals.events` and consumer group offsets |

Ports 5432 (PostgreSQL), 8333 (local S3), 1025 (SMTP), 4318 (telemetry) and 9092 (Kafka) are also used. Avoid running another environment on the same ports. `SERVER_PORT` and `KAFKA_UI_PORT` can override their corresponding local ports.

For executable API examples, install **HTTP Yac** in VS Code and follow [requests/README.md](requests/README.md). Start with `requests/http/auth.http`; successful requests share tokens, IDs and versions across files. No manual token copying is needed.

## 4. Follow a rental into Kafka

Do this after the rental flow works. Keep `make dev` running; recommendations is already started in the same terminal.

Then rent or return a car. Follow the flow:

1. The API commits the rental and its outbox event in one database transaction.
2. The outbox publisher sends that event to `rentals.events`.
3. Recommendations consumes it using its own group, `rent-cars-recommendations-v1`.
4. Its terminal prints `Rental event received` with the event ID, partition and offset.
5. In [Kafka UI](http://localhost:8084), compare the message and the group's committed offset.

Stop/restart the consumer to study resuming from committed offsets. Use a different group to study replay. Follow the [consumer guide](apps/recommendations/README.md) for exact commands and invalid-event recovery; use the [Kafka guide](docs/kafka.md) for publisher retries and broker outages. `make kafka-events` is just a terminal viewer, not a required service.

For the next exercise, follow the [retry and DLT walkthrough](docs/recommendations-dlt.md):
quarantine a malformed event, verify the next rental is consumed, and understand
how PostgreSQL Outbox guarantees differ from consumer recovery.

## 5. Choose a runtime

Choose one path first. Each environment has its own database, photo storage and sessions; repeat login/setup when switching.

| Mode | Startup | React | Supporting consumer |
| --- | --- | --- | --- |
| Daily development | `make dev` | http://localhost:5173 | Included in `make dev` |
| Local Kubernetes | Publish images, then `make k8s-up` | http://localhost:5173 | Included by default, with Kafka prepared first |

For the optional local consumer, follow [onboarding step 12](docs/getting-started.md#step-12-run-the-supporting-consumer-locally). For Kubernetes, follow [step 13](docs/getting-started.md#step-13-alternative-docker-desktop-kubernetes) and the [Helm guide](helm/README.md). Kubernetes is a local deployment exercise, not a production-ready environment.

### Publish all application images

```sh
docker login -u guuhworship
./scripts/push-images.sh
```

The script builds API, web and recommendations from your working tree **before publishing any image**, then pushes their `latest` tags under `docker.io/guuhworship`. Set `IMAGE_NAMESPACE` to use your own registry/namespace. If a build fails, nothing is pushed; if a push fails, earlier pushes are not rolled back. Images use your build machine's platform.

`make k8s-up` pulls these published images; it does not build your source. Set all three image repositories in `helm/values.yaml` when using another namespace. See the [deployment guide](helm/README.md) for checking the consumer and disabling the event stack when needed.

## 6. Navigate and verify the code

Follow the [developer walkthrough](docs/project-walkthrough.md) to trace a screen into an API operation and its tests.

| Location | Contents |
| --- | --- |
| `apps/api/` | Spring Boot API, Maven Wrapper, Flyway and backend tests |
| `apps/web/` | React, TypeScript, feature routes and component tests |
| `apps/recommendations/` | Supporting Spring Boot Kafka consumer and its tests |
| `requests/` | Executable HTTP Yac workflows |
| `helm/`, `compose.yaml`, `scripts/` | Runtime definitions and image publication |
| `docs/` | Onboarding, architecture walkthroughs and study labs |
| `.github/workflows/`, `.github/actions/` | CI orchestration and application-specific checks |

Run commands from the repository root:

```sh
make check-api              # Java API with isolated PostgreSQL/storage tests
make check-recommendations  # Consumer with isolated Kafka tests
make check-java             # Both Java applications
make check-web              # Format, lint, architecture, coverage and build
make check                  # All checks above
```

Docker must be running for Java integration tests. Install frontend dependencies with `make setup`. JaCoCo reports live under each Java application's `target/site/jacoco/`; web coverage lives in `apps/web/coverage/`. See [AGENTS.md](AGENTS.md) for testing conventions and the application READMEs for PIT/Stryker commands.

### CI by application and responsibility

The [main workflow](.github/workflows/ci.yml) handles PR/push/manual triggers,
runners, timeouts and an explicit API mutation matrix. Jobs run independently in parallel.
When adding an API package or root-level application class, update that matrix to keep
all production classes in the PIT scope.
Weekly runs execute mutation checks only.

Each local composite action owns one application's checks:

| Application | Quality | Mutation | End-to-end |
| --- | --- | --- | --- |
| API | [.github/actions/api/quality](.github/actions/api/quality/action.yml): Maven verification, JaCoCo and Sonar | [.github/actions/api/mutation](.github/actions/api/mutation/action.yml): PIT per package | Covered by the browser journey |
| Frontend | [.github/actions/frontend/quality](.github/actions/frontend/quality/action.yml): formatting, lint, coverage and build | [.github/actions/frontend/mutation](.github/actions/frontend/mutation/action.yml): Stryker | [.github/actions/frontend/e2e](.github/actions/frontend/e2e/action.yml): API and Playwright |
| Recommendations | [.github/actions/recommendations/quality](.github/actions/recommendations/quality/action.yml): Maven verification, JaCoCo and Sonar | [.github/actions/recommendations/mutation](.github/actions/recommendations/mutation/action.yml): PIT | No browser interface |

Quality and mutation actions invoke Maven or npm directly inside each application.
Root Make commands remain optional shortcuts for local use.
Actions contain steps, not job scheduling or runner selection. GitHub workflows
must remain directly in .github/workflows; actions can be organized in subfolders.
See [.github/actions/README.md](.github/actions/README.md) before adding a check.

Each Java application owns its POM and Maven Wrapper. There is no root POM.
The API and recommendations use separate Sonar project keys and independent analyses.
Configure each project's gate and profiles in Sonar; CI does not provision projects
or copy policies between them. The frontend retains its own lint, coverage and
Stryker checks. E2E is an integration job that intentionally uses both API and web.

### Merge a reviewed PR after CI

Use GitHub's **Enable auto-merge → Squash and merge**. This is opt-in per PR.
The required **Merge readiness** job depends on every CI job, including all PIT
shards, Stryker, browser tests and both Java quality jobs that wait for Sonar.
A failed, cancelled or skipped dependency blocks the gate. There is no polling
script or separate workflow to rerun: use **Re-run failed jobs** in the CI run.

The existing required check names **Java verification**, **Web verification** and
**Merge readiness** are preserved. When adding another CI job, add it to the gate's
needs list too.

## 7. Stop without losing your work

For local development, press Ctrl+C in the `make dev` terminal, then run `make infra-stop`. This stops the Docker infrastructure and preserves its volumes.

`docker compose down -v` removes that Compose project's data. **`make k8s-down` deletes the application namespace, including its database, photos and credentials.** Kafka lives in a separate Kubernetes namespace and is retained; see the Helm guide before resetting/replaying across a fresh database.

## Learn one topic at a time

- [Onboarding](docs/getting-started.md): first successful end-to-end journey.
- [Code walkthrough](docs/project-walkthrough.md): Java/React responsibilities and tests.
- [Database labs](docs/studies/README.md): integrity, ACID/rollback, concurrency and query plans.
- [Kafka](docs/kafka.md) and [consumer](apps/recommendations/README.md): outbox, delivery, offsets and independent processing.
- [Observability](docs/observability.md): logs, metrics and traces.
- [API storage](apps/api/README.md#s3-configuration): local S3 and external bucket configuration.

Use only one environment at a time. See [switching environments](helm/README.md#switch-between-local-development-and-kubernetes) for the safe stop/start commands and shared ports.

For the complete local/Kubernetes acceptance checklist, follow [the end-to-end walkthrough](docs/local-stack-validation.md).

Make commands are organized by responsibility under [`make/`](make/README.md). Continue running them from the repository root; the root Makefile includes the category files.

### Optional Hadoop study

The recommendations consumer can archive rental events in HDFS. Start with `make dev HDFS_ENABLED=true`; follow the [system design and step-by-step local/Kubernetes guide](docs/recommendations-hdfs.md). This optional lab prepares raw events for later analytics; it does not run Spark or calculate recommendations.

## New to Hadoop?

Follow [Hadoop from zero: local first, then Kubernetes](docs/hadoop-first-steps.md) for a guided code tour, hands-on exercises, expected results and troubleshooting.
