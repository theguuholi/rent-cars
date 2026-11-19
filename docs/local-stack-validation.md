# End-to-end walkthrough: local development and Kubernetes

Run commands from the repository root. Use one environment at a time.
This is the acceptance checklist for a contributor: product behavior first,
then the asynchronous event behind it. Recommendations logs events; it does not
calculate recommendations yet.

## 1. Choose and start one environment

Prerequisites: Docker Desktop, Java 25, Node 24.16.0, npm, Make and jq.
Kubernetes also needs kubectl, Helm and OpenSSL. Use Docker Desktop's
`docker-desktop` context, not a separate kind cluster.

### Local development

```sh
make setup
# If Kubernetes is already running this project:
make k8s-stop
make dev
```

`make dev` prepares PostgreSQL, S3 storage, Mailpit, Grafana LGTM, Kafka and
Kafbat in Compose. After Kafka's topic initializer succeeds, it starts API,
React and recommendations together on the host. Logs have `api`, `web` and
`recommendations` prefixes. Do not also start `make recommendations-dev`.
Wait for both Spring applications to start and the consumer to receive partition
assignments. Flyway runs inside the API and applies migrations and DEV seeds;
Compose itself does not execute application migrations.

### Kubernetes

Stop the local `make dev` terminal with Ctrl+C first. Then:

```sh
make infra-stop
docker login -u guuhworship
./scripts/push-images.sh
make k8s-up
kubectl --context docker-desktop -n rent-cars get pods,svc,pvc
kubectl --context docker-desktop -n rent-kafka get pods
helm test fleet --kube-context docker-desktop -n rent-cars --logs
```

The push script builds and publishes API, web and recommendations for your
machine's architecture. `k8s-up` prepares Kafka and Kafbat, then deploys all
applications and supporting services using the published images. It does not
build source code. The chart enables recommendations and messaging by default;
keep both enabled for this exercise. Flyway runs on API startup here too.

Check the consumer in another terminal:

```sh
kubectl --context docker-desktop -n rent-cars logs -f deployment/fleet-recommendations
```

A Ready pod alone does not prove the Kafka listener is consuming. Verify the
receipt and committed offsets in step 4.

### The same URLs in either mode

| Interface | Address |
| --- | --- |
| React | http://localhost:5173 |
| API / Swagger | http://localhost:8080/swagger-ui.html |
| Kafbat | http://localhost:8084 |
| Grafana | http://localhost:3000 |
| Mailpit | http://localhost:8025 |

No port-forward is needed with this local Docker Desktop chart. Sign out and
sign in again when switching modes: databases, signing keys and Kafka clusters
are separate, but localhost cookies can survive the switch.

## 2. Verify the public catalog and administrator

1. Open React in a private window. Browse Cars and open a seeded car.
   **Expect:** category, equipment and availability without signing in.
2. Open `/categories` directly. **Expect:** redirect to Sign in.
3. Sign in with `test@test` / `test@tesT@dev`.
   **Expect:** access to administrative catalogs and car editing.
4. Create a specification with a unique name. Create a car with a unique plate,
   a category, one to three specifications and nonnegative BRL rates.
   **Expect:** the saved car is visible with the selected category/equipment.
5. Upload `requests/fixtures/car-front.png` and `car-side.png` on the car detail.
   Reload. **Expect:** both photos remain. An invalid file must show an error.
6. Open the same car editor in two tabs. Save a name change in the first and
   try saving in the second. **Expect:** a conflict requiring a fresh editor,
   not a silent overwrite. Do not remove version checks to make this pass.

### Verify refresh tokens without losing an editor draft

For this exercise, stop `make dev` and run `make api-dev`, `make web-dev` and
`make recommendations-dev` in separate terminals. This lets you restart only
the API while keeping React and the draft open.

Sign in, edit a car name and leave it unsaved. In DevTools, open Network, clear
the list and switch to a blank browser tab. Stop only the API with Ctrl+C in
terminal 1, then run `make api-dev` again. Keep the React process running and do
not reload the editor.

When development signing keys are generated at startup, restarting the API
invalidates the old JWT while retaining the database-backed refresh session.
Return to the editor and trigger a protected request. Expect a successful
`POST /api/sessions/refresh` followed by a retry; the unsaved draft must remain.
If you configured persistent signing keys, restarting alone does not expire
the JWT; use the expiry procedure in the authentication guide.

For HTTP Yac, select `local`. Run `auth_login_admin` and `auth_me_admin`,
restart the API with generated development keys, then repeat `auth_me_admin`
with the old bearer (401). Run `auth_refresh`, `auth_me_session` and
`auth_logout`; expect 200, 200 and 204. Keep the cookie jar enabled.

## 3. Register a driver and complete a rental

1. Sign out. Open `/register` and enter unique email, username and 11-digit CNH.
   Use a password accepted by the displayed password rules and confirm it.
   **Expect:** account-created confirmation and a link to sign in.
2. Sign in as that driver. Open the available car created in step 2.
3. Enter an Expected return date in the future and select **Rent car**.
   **Expect:** success, the car becomes unavailable and the rental appears in
   **My rentals**. Note the rental ID from the POST `/api/rentals` response in
   the browser's Network tab; it is the Kafka key used in the next step.
4. Reload. **Expect:** the rental persists and the session is restored.
5. Select **Return car**. **Expect:** the car becomes available; the receipt
   shows the final charge and dates. Reload My rentals to verify persistence.
6. Visit `/categories` as the driver. **Expect:** access denied, not admin access.

The browser sends `/api/...`; Vite or Nginx forwards it to the API without the
`/api` prefix. API writes complete before asynchronous consumption completes.

## 4. Prove delivery to recommendations

1. Open Kafbat, select the cluster, then **Topics → rentals.events → Messages**.
   Read from an offset/time that includes your rental.
2. Find `RentalOpened` and `RentalReturned` with the rental ID from step 3.
   **Expect:** Kafka key equals `rentalId`; each event has its own `eventId`.
3. In the recommendations log, find `Rental event received` for those exact
   event IDs, with partition and offset. Local: use the prefixed `make dev`
   output. Kubernetes: use the `kubectl logs` command above.
4. Open **Consumers** and select `rent-cars-recommendations-v1`.
   **Expect:** an active member, assigned partitions and committed offsets
   advancing after processing. Once caught up, lag should reach zero.
5. Compare the log offset with the committed offset for that partition. Kafka
   commits the next offset to read; after processing offset 10, expect 11 or
   greater if later records have also been processed.

An HTTP 201 proves the rental committed, not that recommendations consumed it.
A Kafka message proves publication; the receipt plus committed offset proves
consumption. At-least-once delivery can produce repeated receipts.

For a read-only terminal view in local mode:

```sh
make kafka-events
```

This command is a separate viewer, not the recommendations service. Do not
publish arbitrary invalid JSON into the normal topic: the current error handler
stops the recommendations listener on invalid records.

## 5. Verify observability and its boundaries

Open Grafana → Explore. Tempo shows API request traces and rental observations;
Prometheus shows API and Outbox metrics; Loki shows API logs. In local Vite dev,
browser fetch spans can join the API trace. The published React image currently
does not export browser traces.

Current limits: recommendations logs to stdout without OTLP export; Outbox does
not persist trace context for later Kafka publication. There is no complete
browser → API → Outbox → Kafka → recommendations trace yet. Kafbat shows offsets
and lag, but is not a tracing backend. Follow [observability](observability.md)
and [Kafka concepts](kafka-concepts.md) rather than assuming a single trace exists.

## 6. Run automated checks

Keep the selected stack running. Install the browser once:

```sh
(cd apps/web && npx playwright install chromium)
```

Local Vite development:

```sh
npm --prefix apps/web run test:e2e -- --workers=1
```

Kubernetes, with the current published image (browser tracing is not enabled):

```sh
npm --prefix apps/web run test:e2e -- --workers=1 --grep-invert 'Given local tracing is enabled'
```

The excluded scenario checks Vite's browser tracing integration; it is not a
production-image journey. Other API, auth, catalog and rental journeys
remain selected. These tests create data in the active DEV database; completed
rentals and test accounts may remain. They do not replace step 4's Kafka check.

Run the code quality checks separately:

```sh
make check
```

For executable HTTP requests, select `local` or `k8s` in HTTP Yac (both API URLs
are now `http://localhost:8080`) and follow [requests/README.md](../requests/README.md).
Run authentication first; do not reuse tokens or resource IDs from the other mode.

## 7. Stop, switch or reset

- Local: Ctrl+C in `make dev` ends all three application processes. Then run
  `make infra-stop` to stop infrastructure while preserving volumes.
- Kubernetes: `make k8s-stop` stops workloads and releases host ports, preserving
  PVCs, Kafka records and credentials. Resume with `make k8s-up`.
- Local database reset: stop applications, run `make db-reset`, then `make dev`.
  **This deletes database records.** Flyway recreates schema/seeds on API startup.
  Kafka events and photo objects remain separate and can refer to deleted records.
- Kubernetes reset: `make k8s-down` deletes the application's namespace,
  **including database and photo volumes**. It retains the separate Kafka
  namespace; this is not a routine stop command.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Startup says the other environment is active | Use the stop sequence above; do not bypass the guard. |
| recommendations has no browser URL | Expected: it is a background Kafka consumer. |
| Kafbat shows no group | Confirm the JVM is running, its bootstrap server matches this cluster, and the listener has not stopped on an error. |
| Group is active but lag grows | Inspect each partition and consumer errors/processing duration; more replicas do not fix a single hot key. |
| No event after successful rental | Inspect API Outbox warnings and pending messages, then Kafka connectivity. |
| Kubernetes image is stale | Rebuild/push all images, then run `make k8s-up`; local source edits alone do not update images. |
| Reset fails with active database sessions | Stop the API/test processes first; reset intentionally does not force-disconnect them. |
