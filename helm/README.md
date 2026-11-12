# Kubernetes local (Docker Desktop)

Enable Kubernetes in Docker Desktop. Install `kubectl`, Helm and OpenSSL.
Run the commands below from the repository root.

## Start or update

After building and pushing the API and web images:

```sh
make k8s-up
```

Open http://localhost:8082. Sign in with `test@test` / `test@tesT@dev`.

The command creates the `rent-cars` namespace and credentials when absent,
installs the `fleet` Helm release and restarts API/web to pull the published
`latest` images. Existing database records and uploaded photos are preserved.
It uses the `docker-desktop` context and does not build or push images.

## Delete everything and start fresh

```sh
make k8s-down
make k8s-up
```

**`k8s-down` deletes the application's namespace, including database data,
uploaded photos, volumes and credentials.** It does not delete the cluster
or Docker Compose data. Demo records are recreated on the next `k8s-up`;
uploaded photos must be uploaded again.

Run `make help` to list the commands.

## What runs

- API: Spring Boot, with database migrations and demo data.
- Web: Nginx serving React and forwarding `/api` to the API.
- PostgreSQL: persistent database volume.
- SeaweedFS: S3-compatible storage with a persistent photo volume.
- Mailpit: local SMTP sink and browser inbox for development mail.
- Grafana LGTM: local Prometheus metrics, Tempo traces, Loki logs and an OTLP collector, with a persistent volume.

The chart defaults target local Docker Desktop: release `fleet`, port `8082`,
demo data enabled and SeaweedFS storage. Images and storage settings are in
`values.yaml`. API/web use
`imagePullPolicy: Always`, so restarting them pulls the current published tag.
The credentials Secret is created once and reused until `k8s-down`.
The API exports telemetry to `fleet-observability:4318` with full trace sampling
in this local chart. Grafana and the collector are internal ClusterIP services;
Grafana's anonymous Admin access is intended only for this local test cluster.
For a different release name, change `storage.endpoint` and
`storage.credentialsSecret` to match its S3 Service and Secret.
To disable demo data on a fresh database, set `api.flywayLocations` to
`classpath:db/migration`. This does not remove existing demo records.

## Check the environment

```sh
kubectl --context docker-desktop -n rent-cars get pods,svc,pvc
helm test fleet --kube-context docker-desktop -n rent-cars --logs
kubectl --context docker-desktop -n rent-cars port-forward svc/fleet-mailpit 8025:8025
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

2. In a separate terminal, forward Grafana to port **3001** so it does not
   conflict with the Compose Grafana on port 3000:

   ```sh
   kubectl --context docker-desktop -n rent-cars port-forward svc/fleet-observability 3001:3000
   ```

3. Open <http://localhost:8082>, browse cars and rent/return one as a driver.
   Open <http://localhost:3001>. In **Explore → Tempo**, search for service
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
