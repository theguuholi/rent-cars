# From checkout to your first rental

This tutorial takes a new contributor from installing tools to running React,
Spring Boot, image storage and the basic API workflows in Bruno. Start with
**local development**, then try Docker Compose or Kubernetes as alternatives.
Commands use a POSIX shell (macOS/Linux, or a configured WSL2 environment on Windows).
Unless a command explicitly changes directory, run it from the repository root.

## 1. Install and verify the tools

| Tool | Version / installation | Why it is needed |
| --- | --- | --- |
| Git | [Install Git](https://git-scm.com/downloads) | Clone and review the repository |
| JDK | [Temurin JDK 25](https://adoptium.net/temurin/releases/?version=25), **JDK**, matching your CPU | Run/build/test Spring locally |
| Node.js and npm | [Node 24.16.0](https://nodejs.org/dist/v24.16.0/) | Match the web Dockerfile; run React and Bruno CLI |
| Docker Desktop | [Install for your OS](https://docs.docker.com/desktop/) | PostgreSQL, SeaweedFS, containers and local Kubernetes |
| Make | macOS command-line developer tools; Linux/WSL package manager | Repository shortcuts |
| VS Code + Bruno | [Install the official Bruno extension](https://docs.usebruno.com/vs-code-extension/install-config) | Edit and send `.bru` requests; Bruno desktop is also supported |
| kubectl | [Installation](https://kubernetes.io/docs/tasks/tools/) | Only needed for Kubernetes |
| Helm | [Installation](https://helm.sh/docs/intro/install/) | Only needed for Kubernetes; chart checked with Helm 4.3.1 |
| OpenSSL | Your OS package manager | Generate local Kubernetes signing keys |

Open Docker Desktop and wait until its engine is running. In a new terminal:

```sh
git --version
java -version
javac -version
node --version
npm --version
make --version
docker version
docker compose version
```

Java and javac should report **25**; Node should report **24.16.0**. If your IDE
uses a different Java installation, select JDK 25 for this project as well.
Use a version manager or the installer to select the required Node version;
there is no root `.nvmrc` to select it automatically.

No global Maven installation is needed: `apps/api/mvnw` downloads the configured
Maven version. No separate PostgreSQL, SeaweedFS, React or Spring installation
is needed. Docker supplies the data services and project dependencies supply
the frameworks. Initial builds need network access to download dependencies/images.

## 2. Get the project

```sh
git clone https://github.com/theguuholi/rent-cars.git
cd rent-cars
make help
make setup
```

If you already have a checkout, open that directory instead of cloning again.
`make setup` installs the exact frontend dependencies from the lockfile. Open
this repository folder in VS Code. Do not run `npm install` at the repository
root; the frontend package lives in `apps/web`.

## 3. Start local development

```sh
make dev
```

Leave this terminal running. This starts PostgreSQL and SeaweedFS in Docker,
then starts the Spring API and Vite/React on your machine. It supplies the local
S3 credentials. Flyway creates/updates the schema and loads development catalogs
and the development administrator automatically.

| Surface | Address |
| --- | --- |
| React application | http://localhost:5173 |
| Spring API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Bruno environment | `local` |

From a second terminal in the repository root:

```sh
docker compose ps
curl -i http://localhost:8080/cars
```

Both data services should be healthy. The API should return **200** and a JSON
array. If it is still compiling or starting, wait for the API startup message.

Open React and sign in with the development administrator:

- Email: `test@test`
- Password: `test@tesT@dev`

Browse cars, open the editor, and inspect categories/specifications. Car editing
and image uploads require the administrator. Rental operations are currently
implemented in the API and exercised through Bruno; do not look for a rental
page in React.

To run the two applications separately, use two terminals instead of `make dev`:

```sh
# Terminal 1, repository root:
make api-dev
```

```sh
# Terminal 2, repository root (after make setup):
make web-dev
```

Do not run these alongside another instance of `make dev`: they use the same
ports. PostgreSQL uses 5432 and the local S3 endpoint uses 8333.

## 4. Open Bruno and understand the shared values

1. In VS Code, install the official Bruno extension and open `requests/bruno`
   as a collection. With Bruno desktop, choose **Open Collection** and select
   the same directory. Do not create a second collection or import the old `.http` file.
2. Select the **local** environment. Its `baseUrl` is `http://localhost:8080`.
3. Keep the cookie jar enabled. Open a `.bru` file and use the extension to send
   it. You can switch between text and GUI views.
4. Open the response and assertion/test results after every request. Continue
   when the expected status and checks pass.

The collection shares development credentials through `collection.bru`.
Successful requests save tokens, IDs and versions as **runtime variables**;
you do not copy them into files. Catalog folders inherit `adminToken`; rentals
inherit `driverToken`. Public car/image reads need no token.

Registration and session writes automatically reuse the small `auth/csrf`
request to obtain a matching CSRF cookie/header. Login itself remains explicit.
The refresh cookie belongs to the **last account you signed in with**.

## 5. Authenticate both roles

Paths below are relative to `requests/bruno`; every request has a `.bru` extension.
Run these in order:

| Step | Request | Expected result |
| --- | --- | --- |
| 1 | `auth/register-driver` | **201** creates `driver@example.com`; **409** is allowed if that email already exists |
| 2 | `auth/login-admin` | **200** captures `adminToken` |
| 3 | `auth/me-admin` | **200**, role `ADMIN` |
| 4 | `auth/login-driver` | **200** captures `driverToken` without replacing `adminToken` |
| 5 | `auth/me-driver` | **200**, role `USER` |
| 6 | `auth/refresh` | **200** renews the driver session, because driver was the last login |
| 7 | `auth/me-session` | **200** confirms the refreshed token works |

The example driver password is `local-example-only-123`. These credentials are
for development only. Registration does not reset an existing account's password.

## 6. Read catalogs and create equipment

| Step | Request | Expected result |
| --- | --- | --- |
| 8 | `categories/list` | **200**; captures the `SEDAN` category as `categoryId` |
| 9 | `specifications/list` | **200**; lists equipment |
| 10 | `specifications/create` | **201**; creates uniquely named equipment and captures its ID/version |
| 11 | `specifications/get` | **200**; reads the equipment you created |
| 12 | `specifications/update` | **200**; replaces its name/description and captures the new version |
| 13 | `specifications/patch` | **200**; changes only its description |

The category is selected by name, not by array position. The specification ID
comes from creation, so later mutations target your example record.

## 7. Create a car and exercise photos

| Step | Request | Expected result |
| --- | --- | --- |
| 14 | `cars/list` | **200**; public fleet, without changing the selected `carId` |
| 15 | `cars/create` | **201**; creates an available Argo using your category/equipment and captures `carId`/`carVersion` |
| 16 | `cars/get` | **200**; detail and current version |
| 17 | `cars/update` | **200**; edits the description using `expectedVersion` |
| 18 | `car-images/upload` | **201**; uploads the two Argo PNG fixtures and captures `imageId` |
| 19 | `car-images/read` | **200**, `image/png`; reads the image bytes without authentication |
| 20 | `car-images/append` | **201**; adds another photo without replacing the first two |
| 21 | `car-images/delete` | **204**, empty body; deletes the captured photo |
| 22 | `scenarios/images/deleted-image` | **404**, expected; confirms the photo is no longer readable |
| 23 | `scenarios/images/invalid-upload` | **422**, expected; rejects the deliberately invalid JPEG fixture |
| 24 | `cars/get` | **200**; remaining photos and refreshed version |

Fixtures live in `requests/fixtures`; multipart paths resolve relative to the
collection root. There is no need to select or copy local image paths manually.
Refresh the React car list and open this car to see the same records and photos.
Images are stored in SeaweedFS, not in the API container filesystem.

Every image mutation can change the car version. Run `cars/get` before a later
manual edit if your version is stale; do not guess `expectedVersion`.

## 8. Rent and return the car

| Step | Request | Expected result |
| --- | --- | --- |
| 25 | `rentals/create` | **201**; driver rents the car with a return date two days ahead; captures `rentalId` |
| 26 | `rentals/list` | **200**; includes only that driver's rentals |
| 27 | `cars/get` | **200**, `available=false` |
| 28 | `scenarios/rentals/unavailable-car` | **409**; administrator cannot rent the same occupied car |
| 29 | `scenarios/rentals/foreign-return` | **404**; administrator cannot return the driver's rental |
| 30 | `rentals/return` | **200**; fills return time/total and releases the car |
| 31 | `cars/get` | **200**, `available=true` |
| 32 | `scenarios/rentals/return-twice` | **409**; duplicate return is rejected |
| 33 | `scenarios/rentals/invalid-period` | **422**; expected return less than 24 hours ahead is rejected |
| 34 | `scenarios/rentals/delete-history` | **409**; rental history prevents deleting the car |

An immediate return costs **15000 centavos (R$150)** for this example. Billing
uses completed 24-hour periods, at least one daily charge, and completed days
late for the fine. The server uses the current car prices at return time.
User identity, start time, actual return time and total are assigned by the server.

An account can have only one open rental. If an earlier run stopped after
creation, use `rentals/list` to find it, set the runtime `rentalId` in Bruno and
return it before starting another rental. Do not delete the database to resolve
this ordinary workflow conflict.

## 9. Try deletion and finish the session

The rented car is retained as history. To test successful deletion separately:

1. Run `cars/create` again. This creates a different car and replaces runtime `carId`.
2. Run `cars/mark-unavailable`: expect **200**, unavailable car with equipment cleared.
3. Run `car-images/upload` and `car-images/read`: expect **201** and **200**.
4. Run `cars/delete`: expect **204**, because this second car has never been rented.
5. Optionally run `specifications/delete`: expect **204**; it also removes that
   equipment assignment from cars. Create another specification before creating
   another car with the sample request.
6. Run `auth/logout`: expect **204**. This ends the driver's current refresh session.
7. Run `scenarios/auth/refresh-after-logout`: expect **401**.

Rerun the logins before another workflow. An issued JWT has its own expiry;
logout invalidates the refresh session, not all previously issued access tokens.

## 10. Run the normal workflow automatically

With the API running, in a separate terminal:

```sh
cd requests/bruno
npx --yes @usebruno/cli@4.3.0 run --env local --tags smoke -r --bail
```

This runs the 24 normal requests, including session refresh, CRUD edits, image
upload/read/delete and rental creation/return. It creates example data and retains
the rented car's history. It does not run manual deletions or expected-error
scenarios. In the GUI collection runner, select the same **smoke** tag filter.

Do not use an unfiltered **Run All**: some examples intentionally require a
specific intermediate state, such as testing an unavailable car before its return.
Separate CLI invocations do not share runtime tokens or IDs.

## 11. Alternative: run everything in containers

You can skip running Java and React on the host in this mode. Docker builds both
applications using their Dockerfiles; host Node is only needed for Bruno CLI.
From the repository root:

```sh
docker compose -f compose.stack.yaml up --build -d --wait
docker compose -f compose.stack.yaml ps
curl -i http://localhost:8081/api/cars
```

Open React at **http://localhost:8081**. For Bruno, use a copied environment with
`baseUrl=http://localhost:8081/api`, or run the existing local environment with
this CLI override:

```sh
cd requests/bruno
npx --yes @usebruno/cli@4.3.0 run --env local --env-var baseUrl=http://localhost:8081/api --tags smoke -r --bail
```

Compose has its **own database and photo volumes**. Records created in host
local development will not appear here. Repeat the Bruno logins/setup rather
than reusing IDs or tokens from the other environment.

Inspect startup failures with:

```sh
# Repository root:
docker compose -f compose.stack.yaml logs --tail=100 api web postgres s3
```

## 12. Alternative: Docker Desktop Kubernetes

1. Enable Kubernetes in Docker Desktop and wait until it is ready.
2. Verify the additional tools and the explicit context:

```sh
kubectl version --client
helm version --short
openssl version
kubectl --context docker-desktop get nodes
```

The node should be **Ready**. No separate kind cluster, Ingress controller or
port-forward is required by the provided Make targets.

3. From the repository root, deploy:

```sh
make k8s-up
kubectl --context docker-desktop -n rent-cars get pods,svc,pvc
helm test fleet --kube-context docker-desktop -n rent-cars --logs
```

Expect the API, web, PostgreSQL and SeaweedFS pods to become ready and their
persistent claims to be bound. Open React at **http://localhost:8082** and choose
**k8s** in Bruno. Its API base URL is **http://localhost:8082/api**. Repeat the
same numbered Bruno walkthrough with fresh logins and IDs.

```sh
# From requests/bruno:
npx --yes @usebruno/cli@4.3.0 run --env k8s --tags smoke -r --bail
```

**`make k8s-up` pulls the published images configured in `helm/values.yaml`. It
does not build your working tree.** The published API must include rentals for
that part of the tutorial to work. Re-running the command restarts API/web to
pull the current tags while preserving existing data.

To test your own code through the registry, from the repository root:

```sh
# Optional publishing step: requires write access to your Docker Hub namespace.
docker login
IMAGE_NAMESPACE=docker.io/YOUR_NAMESPACE ./scripts/push-images.sh
```

Then set `api.image.repository` and `web.image.repository` in `helm/values.yaml`
to `docker.io/YOUR_NAMESPACE/rent-cars-api` and
`docker.io/YOUR_NAMESPACE/rent-cars-web`, and run `make k8s-up` again. The script
builds for your machine's platform; images must match the Kubernetes node's CPU.
Do not publish into someone else's namespace. See the [Helm guide](../helm/README.md)
for storage settings and external S3.

For failures, inspect the application namespace, not `default`:

```sh
kubectl --context docker-desktop -n rent-cars get events --sort-by=.lastTimestamp
kubectl --context docker-desktop -n rent-cars logs deployment/fleet-api --tail=100
kubectl --context docker-desktop -n rent-cars logs deployment/fleet-web --tail=100
```

## 13. Choose the right URL

| Mode | React | Bruno API base URL | Database and photos |
| --- | --- | --- | --- |
| `make dev` | `http://localhost:5173` | `http://localhost:8080` (`local`) | Development Compose volumes |
| Full Compose | `http://localhost:8081` | `http://localhost:8081/api` (override) | Separate stack volumes |
| Docker Desktop Kubernetes | `http://localhost:8082` | `http://localhost:8082/api` (`k8s`) | PVCs in `rent-cars` |

The `/api` prefix belongs to the web proxy. Direct Spring requests on port 8080
do **not** include it. Each environment needs its own login and record IDs.

## 14. Stop, reset and run checks

For local development, press Ctrl+C in the application terminal, then:

```sh
make infra-stop
```

For the all-container stack:

```sh
docker compose -f compose.stack.yaml down
```

Both preserve data volumes. To deliberately reset **only** the full Compose stack:

```sh
# DESTRUCTIVE: deletes that stack's database and uploaded photos.
docker compose -f compose.stack.yaml down -v
docker compose -f compose.stack.yaml up --build -d --wait
```

For Kubernetes, the provided down command is a **reset**, not a data-preserving pause:

```sh
# DESTRUCTIVE: deletes the rent-cars namespace, database, photos and credentials.
make k8s-down
make k8s-up
```

To verify code changes, run from the repository root with Docker running and
frontend dependencies installed:

```sh
make check-api
make check-web
# Or both:
make check
```

Backend checks use isolated Testcontainers databases/storage; frontend checks
run formatting, lint, coverage and build. Reports are in
`apps/api/target/site/jacoco` and `apps/web/coverage`.

## 15. Common problems

| Symptom | What to check |
| --- | --- |
| `java` or Maven reports the wrong release | Select JDK 25 in the terminal and IDE; confirm `JAVA_HOME` and `javac -version` |
| `node` fails or npm uses a different version | Select/reinstall Node 24.16.0, reopen the terminal, and rerun `make setup` |
| Docker cannot connect | Start Docker Desktop and wait for `docker version` to show the server |
| Port already in use | Stop the other instance; do not run `make dev` and `make api-dev` together |
| Bruno sends an unresolved variable | Select an environment and run the login/catalog/create prerequisite in the same collection/run |
| Login returns 403 | Enable cookies and rerun login; its script must successfully execute the shared CSRF request |
| Protected API returns 401 | Rerun the relevant login; JWTs expire and dev API restarts change signing keys |
| Catalog operation returns 403 | Use the administrator token, not the driver's token |
| Car/specification update returns 409 | Read the resource again to capture the current version |
| Rental creation returns 409 | Return the driver's open rental, or select an available car |
| Car deletion returns 409 | A car with rental history is retained; create an unrented example for deletion |
| No pods in the default namespace | Use `-n rent-cars` and context `docker-desktop` |
| Pod has ImagePullBackOff / exec format error | Check repository/tag, registry access and matching CPU architecture |
| Rentals are missing only in Kubernetes | Publish an API image containing the feature; `k8s-up` does not build source |
| Photos/data differ between modes | Each mode has separate storage; check the selected Bruno environment |

You have completed onboarding when the React catalog opens, Bruno confirms both
roles, you can create/edit a car, upload/read/delete its photos, and a driver can
rent and return it with availability changing correctly.
