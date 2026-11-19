# Kubernetes local (Docker Desktop)

Enable Kubernetes in Docker Desktop. Install `kubectl`, Helm and OpenSSL.
Run the commands below from the repository root.

## Start or update

After building and pushing the API, web and recommendations images:

```sh
make k8s-up
```

Open http://localhost:5173. Sign in with `test@test` / `test@tesT@dev`.

The command creates the `rent-cars` namespace and credentials when absent,
prepares Kafka, installs the `fleet` Helm release and restarts API/web/recommendations to pull the published
`latest` images. Existing database records and uploaded photos are preserved.
It uses the `docker-desktop` context and does not build or push images.

`make k8s-up` also sets `rent-cars` as the default namespace of `docker-desktop`.
It does not switch your active context. Select the local cluster once if needed:

```sh
kubectl config use-context docker-desktop
kubectl get pods
kubectl get services
kubectl logs -f deployment/fleet-recommendations
```

No `-n rent-cars` is needed for these commands while `docker-desktop` is active.
The scripts keep explicit context and namespace flags so they always target the local project.

## Inspect the cluster with Headlamp (macOS)

Headlamp is an optional desktop application. It connects to the existing cluster
using your kubeconfig credentials; it does not start Rent Cars or require another
Helm release, Service or port-forward.

1. Install once, following the [official macOS guide](https://headlamp.dev/docs/latest/installation/desktop/mac-installation/):

   ```sh
   brew install --cask headlamp
   ```

2. Enable Docker Desktop Kubernetes. From the repository root, stop local app
   terminals with Ctrl+C, then start the Kubernetes environment if not already running:

   ```sh
   make infra-stop
   make k8s-up
   ```

3. Confirm the context and namespace, then open the desktop application:

   ```sh
   kubectl config use-context docker-desktop
   kubectl config set-context docker-desktop --namespace=rent-cars
   kubectl get pods
   open -a Headlamp
   ```

4. In Headlamp, select the `docker-desktop` cluster and filter the namespace to
   `rent-cars`. The UI may have its own namespace selection even when kubectl
   already defaults to `rent-cars`.
5. Browse workloads and pods. Expect `fleet-api`, `fleet-web`,
   `fleet-recommendations`, `kafka-ui`, the Strimzi operator and `rent-combined-0`.
   Inspect readiness, restarts and events. PostgreSQL and S3 run as StatefulSets.
6. Open the recommendations pod and its logs. Keep them visible, open
   [React](http://localhost:5173), sign in as a driver and rent/return a car.
   Expect `Rental event received` with `eventId`, `rentalId`, partition and offset.
   A Running pod alone does not prove that the consumer processed an event.
7. Use [Kafbat](http://localhost:8084) to inspect message contents and consumer
   offsets, and [Grafana](http://localhost:3000) for API metrics, logs and traces.
   Headlamp does not replace either tool. See [the E2E checklist](../docs/local-stack-validation.md).

If no cluster appears, confirm Docker Desktop is running and `kubectl get pods`
works. Headlamp desktop normally reads `~/.kube/config`; for a custom kubeconfig,
follow the [desktop configuration guide](https://headlamp.dev/docs/latest/installation/desktop/).
CPU/memory graphs depend on the cluster's metrics integration; missing graphs
alone do not mean the application failed. Closing Headlamp leaves the cluster running.

### Headlamp versus K9s

| Tool | Interface | Useful workflow |
| --- | --- | --- |
| [K9s](https://k9scli.io/) | Terminal, keyboard shortcuts | Fast resource navigation and debugging without leaving the terminal. |
| [Headlamp](https://headlamp.dev/) | Graphical desktop or web UI | Explore resources visually and inspect workloads with mouse navigation. |

Both manage the same Kubernetes resources through the permissions of the
connected identity. Switching tools does not change the application, its Helm
chart or its namespace. Keep K9s if you prefer the terminal; use Headlamp when
a visual walkthrough helps. Headlamp desktop is the documented local option here.

## Switch between local development and Kubernetes

Run one environment at a time. Both use the same browser and API addresses:

| Interface | URL |
| --- | --- |
| React | http://localhost:5173 |
| API / Swagger | http://localhost:8080/swagger-ui.html |
| Kafka UI | http://localhost:8084 |
| Grafana | http://localhost:3000 |
| Mailpit | http://localhost:8025 |

The Make startup commands check for the other environment before starting services.
Install `jq` for these checks. They never stop an environment automatically.
Do not bypass the checks with direct Compose, Helm or Maven startup commands.
If Docker Desktop Kubernetes is configured but unreachable, restore access so the
check can confirm that it is stopped; it does not assume an unreachable cluster is safe.

**Local → Kubernetes:** press Ctrl+C in the terminals running the API, React and
recommendations, then run:

```sh
make infra-stop
make k8s-up
```

**Kubernetes → local:**

```sh
make k8s-stop
make dev
```

`k8s-stop` scales the application workloads down, stops the dedicated Strimzi
operator before stopping Kafka, and changes LoadBalancer services to ClusterIP
to release host ports. It retains PVCs, Kafka events/offsets and credentials.
`k8s-up` restores the operator, workloads and public services. In-memory data and
Mailpit's temporary inbox are not retained. This targets only the local
`rent-cars` namespace, which must not contain unrelated workloads.

The databases and Kafka clusters remain separate: switching does not copy data.
Sign in again after switching because localhost cookies can belong to the previous
environment. Use `k8s-stop` to switch; `k8s-down` is a destructive reset.

## Delete everything and start fresh

```sh
make k8s-down
make k8s-up
```

**`k8s-down` deletes the application's namespace, including database data,
uploaded photos, Kafka events/offsets, volumes and credentials.** It does not delete the cluster
or Docker Compose data. Demo records are recreated on the next `k8s-up`;
uploaded photos must be uploaded again.

Run `make help` to list the commands.

## What runs

- API: Spring Boot, with database migrations and demo data.
- Recommendations: supporting Spring Boot consumer that logs rental event receipts.
- Web: Nginx serving React and forwarding `/api` to the API.
- PostgreSQL: persistent database volume.
- SeaweedFS: S3-compatible storage with a persistent photo volume.
- Mailpit: local SMTP sink and browser inbox for development mail.
- Grafana LGTM: local Prometheus metrics, Tempo traces, Loki logs and an OTLP collector, with a persistent volume.

The chart defaults target local Docker Desktop: release `fleet`, port `5173`,
demo data enabled and SeaweedFS storage. Images and storage settings are in
`values.yaml`. API/web/recommendations use
`imagePullPolicy: Always`, so restarting them pulls the current published tag.
The credentials Secret is created once and reused until `k8s-down`.
The API exports telemetry to `fleet-observability:4318` with full trace sampling
in this local chart. Grafana and the collector use local LoadBalancer ports 3000 and 4318;
Grafana's anonymous Admin access is intended only for this local test cluster.
For a different release name, change `storage.endpoint` and
`storage.credentialsSecret` to match its S3 Service and Secret.
To disable demo data on a fresh database, set `api.flywayLocations` to
`classpath:db/migration`. This does not remove existing demo records.

## Check the environment

```sh
kubectl --context docker-desktop -n rent-cars get pods,svc,pvc
helm test fleet --kube-context docker-desktop -n rent-cars --logs
```

Open a car, upload the two PNG files from `requests/fixtures`, and refresh.
Run `make k8s-up` again and check that the car and photos remain available.

## Verify observability

Use an API image that includes the observability code from this project.
`make k8s-up` pulls the published image; it does not build the current working tree.
The same `grafana/otel-lgtm:0.37.0` simulator used by local Compose is installed
inside Kubernetes. Its main data lives in the `fleet-observability` PVC and
survives pod restarts; in-flight Tempo state can be lost during a restart.
`make k8s-down` deletes the PVC and its data.

1. Confirm `fleet-observability` and `fleet-api` are ready:

   ```sh
   kubectl --context docker-desktop -n rent-cars get pods,pvc
   ```

2. Open Grafana directly at <http://localhost:3000>. No port forwarding is needed.

3. Open <http://localhost:5173>, browse cars and rent/return one as a driver.
   Open <http://localhost:3000>. In **Explore → Tempo**, search for service
   `rent-cars-api` and confirm request traces and `rental.start` /
   `rental.return` observations. In **Prometheus**, query
   `http_server_requests_milliseconds_count` and
   `rental_start_milliseconds_count`; allow about one minute for metrics export.
   In **Loki**, query `{service_name="rent-cars-api"}` and open a log with a
   `trace_id` that also appears in Tempo.

This verifies API telemetry. The published React image does not enable browser
trace export: its Vite `/otel` proxy exists only in `make dev`. The Spring API
still receives and traces requests made through the Kubernetes web proxy.
For this local chart, set `observability.enabled: false` in `values.yaml` if
you do not want the LGTM pod; `make k8s-up` resets Helm values on each run.

## External S3

For AWS, change these settings in `values.yaml`:

```yaml
s3Emulator:
  enabled: false
storage:
  bucket: your-existing-bucket
  region: sa-east-1
  endpoint: ""
  pathStyle: false
  credentialsSecret: your-existing-aws-secret
```

The existing Secret must contain `AWS_ACCESS_KEY_ID` and
`AWS_SECRET_ACCESS_KEY`. The bucket must already exist.
Do not put AWS credentials in `values.yaml`. Existing photos are not copied
between stores automatically.

## Rental events and Kafka

All Kubernetes configuration lives under this directory:

- `templates/`: application chart resources.
- `values.yaml`: application chart settings.
- `kafka/local.yaml`: local Strimzi broker and rental topic.
- `kafka/ui-local.yaml`: optional local Kafbat UI.

The files in `kafka/` are standalone Kubernetes manifests, not chart templates.
`make k8s-kafka-up` installs the Strimzi operator and applies `kafka/local.yaml`
in the shared `rent-cars` namespace. `make k8s-up` runs that preparation
before deploying the application chart. A direct `helm upgrade` does not apply
these standalone manifests; run `make k8s-kafka-up` first.

All namespaced resources share `rent-cars`, including Strimzi, Kafka and Kafbat.
Deleting this namespace also deletes Kafka PVCs, events and offsets. Strimzi CRDs
and cluster-level RBAC remain cluster-scoped by Kubernetes design. Local development is unchanged: `make dev` still uses the root `compose.yaml`.


Follow the [Kafka producer walkthrough](../docs/kafka.md) for Compose, the read/write browser UI on port 8084, Kubernetes via Strimzi, REST Client rental events, broker outage recovery and the event contract.

## Verify the supporting consumer

Recommendations is an independent Kafka consumer used to study event processing,
not an HTTP dependency of the rental API. `make k8s-up` now prepares the existing
Strimzi/Kafka resources in `rent-cars`, waits for the broker and topic, and then
installs the application including `fleet-recommendations` in `rent-cars`.
If invoking Helm directly, first run `make k8s-kafka-up` yourself.

Publish all three images first:

```sh
docker login -u guuhworship
./scripts/push-images.sh
make k8s-up
kubectl --context docker-desktop -n rent-cars get deployment fleet-recommendations
kubectl --context docker-desktop -n rent-cars logs -f deployment/fleet-recommendations
```

Rent or return a car at http://localhost:5173. Expect `Rental event received`
with its event ID, type, rental ID, partition and offset. The consumer uses the
same topic and bootstrap address as the API and its own consumer group. It has
no public port or Kubernetes Service; it runs as non-root with a read-only
filesystem and a writable temporary volume, like the API.

A running/Ready pod only confirms process startup here, not successful event
processing. No synthetic HTTP/TCP probe is attached to this non-web process.
The current error policy stops the listener on an invalid record while leaving
the JVM alive. Inspect receipt logs and group lag; fix the underlying cause
before restarting. Production listener health and retry/dead-letter policy
remain future work, as explained in the [consumer guide](../apps/recommendations/README.md).

The image push script publishes:

- `docker.io/guuhworship/rent-cars-api:latest`
- `docker.io/guuhworship/rent-cars-web:latest`
- `docker.io/guuhworship/rent-cars-recommendations:latest`

For another registry, set `IMAGE_NAMESPACE` when pushing and change all three
`*.image.repository` values in `values.yaml`. The recommendations image tag,
pull policy, resource requests/limits and `groupId` are configurable there.
`make k8s-up` uses `--reset-values`; persist overrides in that file or use your
own Helm command with a values file rather than expecting earlier `--set`
arguments to survive the Make target.

To omit only this consumer, set `recommendations.enabled: false` in the values
file. To skip both Kafka provisioning and API event publication, run:

```sh
make k8s-up KAFKA_ENABLED=false
```

The chart omits recommendations when messaging is disabled, even if its own
flag is true. Neither option deletes existing Kafka data. `make k8s-down`
deletes the shared namespace, including application data and Kafka events/offsets.
Use `make k8s-stop` to preserve data. Do not reset offsets automatically on deploy.

## Validate deployment changes without deploying

```sh
helm lint ./helm --strict
helm template fleet ./helm > /tmp/rent-cars-manifests.yaml
docker compose --profile kafka-ui config --quiet
```

These manual checks validate the chart and Compose configuration without publishing
images or deploying to Kubernetes.

Follow the [complete E2E checklist](../docs/local-stack-validation.md) to verify browser actions, rental events and consumer offsets after deployment.

### Helm 4 Service ownership after stopping the environment

`make k8s-stop` patches LoadBalancer Services to ClusterIP to release local ports.
`make k8s-up` uses `--server-side=false` for the fleet and operator releases so Helm can restore
these Services without an ownership conflict with `kubectl-patch`. This uses
client-side updates, not resource replacement; namespaces, PVCs and credentials
are preserved. This command requires Helm 4.

Before `make k8s-down`, keep Kafka and its operator running (`make k8s-up` if
the environment was stopped). The reset deletes topics first so their finalizers
can finish before the shared namespace is removed. If topic deletion times out,
namespace deletion does not proceed; restore the operator and retry.
