# Your first rental

Start with the product. Kafka, HDFS, Spark and Kubernetes can be studied after this journey works.
Run commands from the repository root in a POSIX shell (macOS/Linux or WSL2).

## 1. Prepare your tools

Install JDK 25, Node 24.16.0, Git, Make, Docker Desktop with Compose v2, jq and curl.
For installation links and IDE configuration, see [tool setup](api-and-environment-walkthrough.md#step-1-install-and-verify-the-tools).
Start Docker Desktop and wait for its engine.

```sh
git clone https://github.com/theguuholi/rent-cars.git
cd rent-cars
make setup
make doctor
```

Expected: `Prerequisites OK`. If a check fails, follow the printed correction and retry.
Occupied ports may belong to an already-running environment; doctor reports them without stopping anything.
JDK 21 is additionally required only for direct Spark job development; its container supplies it otherwise.

## 2. Start the environment

```sh
make dev
```

This prepares infrastructure once, then starts API, React and recommendations.
Keep the terminal open. Printed URLs are destinations, not readiness confirmations.
Wait for application startup messages, then in another terminal:

```sh
curl -fsS http://localhost:8080/actuator/health
curl -fsS http://localhost:8080/cars
```

Expected: health reports `UP`, and cars returns paginated JSON. Flyway applies migrations and development seeds automatically.
If startup fails, read the named application log, run `make doctor`, and consult [troubleshooting](api-and-environment-walkthrough.md#step-16-common-problems).
Local and Kubernetes environments must not run together. The environment guard explains how to stop the active one.

## 3. Browse and manage cars

Open http://localhost:5173. Browse cars as a visitor.
Sign in as the development administrator (`test@test` / `test@tesT@dev`) to inspect the fleet and images.
These credentials are for development only.

Expected: visitors can read cars; administrators can manage them.

## 4. Register a driver and rent

Sign out, register a new driver in React, and sign in with that account.
Open an available car, choose a valid expected return date, and rent it.

Expected: the rental appears in My rentals and the car becomes unavailable.
If the car was already rented, select another available car; do not bypass the conflict.

## 5. Return the car

Open My rentals and return the active rental.

Expected: the history remains visible and the car becomes available again.
You have now completed the first end-to-end journey.

## 6. Stop without deleting data

Press Ctrl+C in the `make dev` terminal, then:

```sh
make infra-stop
```

This preserves Docker volumes. Restart with `make dev`.
`make db-reset` is destructive and is not part of normal shutdown.

## Choose your next exercise

| Goal | Guide | Expected outcome |
| --- | --- | --- |
| Navigate the source | [Project walkthrough](project-walkthrough.md) | Follow React → HTTP → Java → database → test |
| Use HTTP Yac | [API exercises](api-and-environment-walkthrough.md#step-4-open-the-requests-in-vs-code) | Authenticate and rent without copying tokens |
| Debug separate services | [Development terminals](development-terminals.md) | Restart a consumer without restarting the API |
| Observe a request | [Observability](observability.md) | Find logs, metrics and a trace |
| Follow rental events | [Kafka](kafka.md) | Inspect an event and committed offsets |
| Recover failed messages | [Retries and DLT](recommendations-dlt.md) | Replay a failed event deliberately |
| Archive events | [HDFS](recommendations-hdfs.md) | Find a rental JSON file |
| Compute rankings | [Spark](recommendations-spark.md) | Run a manual batch and inspect the snapshot |
| See rankings in React | [Popular cars](popular-cars.md) | Follow the stored ranking to the catalog |
| Deploy locally | [Helm](../helm/README.md) | Repeat the rental journey in Kubernetes |

Each advanced lab has its own prerequisites. Start HDFS with `make dev HDFS_ENABLED=true` before creating the events you want to archive. Spark remains manual via `make recommendations-build`.
