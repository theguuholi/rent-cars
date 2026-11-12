# Observe the application locally

`make dev` starts Grafana LGTM alongside PostgreSQL, SeaweedFS and Mailpit. LGTM
contains a local OpenTelemetry Collector, Prometheus, Tempo and Loki. The API
exports metrics, traces and logs over OTLP HTTP; the browser exports fetch spans
through Vite's `/otel` proxy. No external account is needed.

1. Run `make setup` once, then `make dev` from the repository root.
2. Open <http://localhost:5173>, browse the catalog, sign in and open/return a
   rental. Failed API calls are useful too; their HTTP status appears on the
   request span. Use the [HTTP Yac walkthrough](getting-started.md#step-4-open-the-requests-in-vs-code)
   to exercise the same API paths without the browser.
3. Open <http://localhost:3000>. The current local LGTM image allows anonymous
   access, so no sign-in is needed. It includes **RED Metrics** and **JVM Metrics**
   dashboards for request rate, errors, latency and JVM health.
4. In **Explore**, select **Tempo** and search for service `rent-cars-api` or
   `rent-cars-web`. Open a request trace to follow the browser `/api` call into
   Spring. The browser traces only fetch requests, so static page navigation
   does not create a span.
5. Select **Prometheus** and query `http_server_requests_milliseconds_count` to see
   API request counts. Metrics are exported on a one-minute interval; wait for
   the first export after starting the API. Select **Loki** and query
   `{service_name="rent-cars-api"}` to see API logs. Open a log line with a
   `trace_id` to correlate it with a trace.

Spring Boot applies Micrometer `@Observed` to the rental service's create and
return operations, producing `rental.start` and `rental.return` observations.
Search these names in Tempo to inspect the operation, or query
`rental_start_milliseconds_count` and `rental_return_milliseconds_count` in
Prometheus. Failed attempts are recorded by the observation
and the enclosing HTTP request; no account, car or rental ID is used as a
metric label.

With `make dev` still running, verify browser propagation from another terminal:

```sh
npm --prefix apps/web run test:e2e -- e2e/browser-tracing.pw.ts
```

The scenario checks the `traceparent` header on `/api/cars` and a successful
OTLP export to the local collector. In Tempo, one catalog trace should contain
spans from both `rent-cars-web` and `rent-cars-api`.

The API writes structured JSON logs to stdout, including trace context when a
request is active. The `/actuator/health` endpoint is public for health probes;
other Actuator endpoints are not exposed over HTTP. Do not put credentials,
tokens, images or request bodies in span names, metric labels or log messages.
For custom metrics and spans, use fixed operation names instead of car, rental
or account IDs.

## Configuration

| Variable | Default in local development | Purpose |
| --- | --- | --- |
| `RENT_OBSERVABILITY_ENABLED` | `true` | Turn API OTLP exports on/off. Defaults to `false` outside the `dev` profile. |
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` | `http://localhost:4318/v1/traces` | API traces collector URL. |
| `OTEL_EXPORTER_OTLP_METRICS_ENDPOINT` | `http://localhost:4318/v1/metrics` | API metrics collector URL. |
| `OTEL_EXPORTER_OTLP_LOGS_ENDPOINT` | `http://localhost:4318/v1/logs` | API logs collector URL. |
| `RENT_TRACING_SAMPLE_RATE` | `0.1` | Fraction of server traces sampled; set `1` while verifying a specific flow. |
| `VITE_OTEL_TRACES_URL` | `/otel/v1/traces` in Vite development | Browser trace endpoint. Unset in production builds, so browser export is off. |
| `VITE_SENTRY_DSN` | unset | Optional frontend runtime error reporting. Build with a DSN to enable it. |

For a production deployment, provide a managed OTLP collector, set the endpoint
variables and enable API export. The single-container LGTM image is intended
for local development and testing. A production web deployment needs a
same-origin `/otel` collector route or an allowed CORS endpoint before enabling
browser export. Do not expose the collector directly to untrusted clients
without access controls and rate limits. The Kubernetes chart keeps telemetry
export disabled by default; inject the collector URLs and enable it when the
cluster has a collector.

To stop local services, run `make infra-stop`. To remove only telemetry data,
stop the stack and remove the `rent-cars_observability_data` Docker volume.
