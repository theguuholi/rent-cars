# From checkout to your first rental

This tutorial takes a new contributor from installing tools to running React,
Spring Boot, image storage and the basic API workflows in HTTP Yac. Start with
**local development**, then try Docker Compose or Kubernetes as alternatives.
Commands use a POSIX shell (macOS/Linux, or a configured WSL2 environment on Windows).
Unless a command explicitly changes directory, run it from the repository root.

## Step 1: Install and verify the tools

| Tool | Version / installation | Why it is needed |
| --- | --- | --- |
| Git | [Install Git](https://git-scm.com/downloads) | Clone and review the repository |
| JDK | [Temurin JDK 25](https://adoptium.net/temurin/releases/?version=25), **JDK**, matching your CPU | Run/build/test Spring locally |
| Node.js and npm | [Node 24.16.0](https://nodejs.org/dist/v24.16.0/) | Match the web Dockerfile; run React and HTTP Yac CLI |
| Docker Desktop | [Install for your OS](https://docs.docker.com/desktop/) | PostgreSQL, SeaweedFS, containers and local Kubernetes |
| Make | macOS command-line developer tools; Linux/WSL package manager | Repository shortcuts |
| VS Code + HTTP Yac | [Install HTTP Yac](https://marketplace.visualstudio.com/items?itemName=anweber.vscode-httpyac) | Send the checked-in HTTP requests directly from the editor |
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
Maven version. No separate PostgreSQL, SeaweedFS, Mailpit, React or Spring installation
is needed. Docker supplies the data services and project dependencies supply
the frameworks. Initial builds need network access to download dependencies/images.

- [ ] Step 1 complete: I followed the instructions and verified the results before continuing.

## Step 2: Get the project

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

- [ ] Step 2 complete: I followed the instructions and verified the results before continuing.

## Step 3: Start local development

```sh
make dev
```

Leave this terminal running. This starts PostgreSQL, SeaweedFS and Mailpit in Docker,
then starts the Spring API and Vite/React on your machine. It supplies the local
S3 credentials. Flyway creates/updates the schema and loads development catalogs
and the development administrator automatically.

| Surface | Address |
| --- | --- |
| React application | http://localhost:5173 |
| Spring API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Local email inbox | http://localhost:8025 |
| HTTP Yac environment | No environment needed (local default) |

From a second terminal in the repository root:

```sh
docker compose ps
curl -i http://localhost:8080/cars
```

All three data services should be healthy. The API should return **200** and a JSON
array. If it is still compiling or starting, wait for the API startup message.

Open React and sign in with the development administrator:

- Email: `test@test`
- Password: `test@tesT@dev`

Browse cars, open the editor, and inspect categories/specifications. Car editing
and image uploads require the administrator. Sign in as a driver to rent and
return a car from its detail page in React, or use the HTTP Yac examples below.

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
ports. PostgreSQL uses 5432, the local S3 endpoint uses 8333, and Mailpit uses
8025 for its inbox and 1025 for SMTP.

- [ ] Step 3 complete: I followed the instructions and verified the results before continuing.

## Step 4: Open the requests in VS Code

1. Open the repository folder in VS Code and install the recommended **HTTP Yac**
   extension (`anweber.vscode-httpyac`). No Desktop API client is needed.
2. Open [auth.http](../requests/http/auth.http). Click **Send** above the request
   you want to execute. Alternatively, place the cursor inside it and run
   **httpyac: Send** from the Command Palette. If REST Client is also installed,
   use the HTTP Yac command: these files use HTTP Yac scripts. To avoid duplicate
   Send actions and incompatible diagnostics, disable REST Client for this
   workspace (Extensions → REST Client → gear → Disable (Workspace)).
3. Local is the default (`http://localhost:8080`), even without an environment
   selected. For another deployment, use **httpyac: Select Environment** and
   choose `compose` or `k8s` from `http-client.env.json`.
4. Send requests manually in the order below. Check each response before moving
   to the next request. You can switch files and tabs without copying tokens.

[common.http](../requests/http/common.http) contains the URL defaults, development
credentials and one CSRF helper. Each responsibility file imports it. Session
writes reference that helper automatically; keep HTTP Yac's default cookie
handling enabled. Login stays manual. Public reads and bearer-authenticated
requests do not fetch CSRF unnecessarily.

Successful responses store tokens, IDs and versions in `$global.rentCars`, keyed
by API URL. This is memory shared across request files, not a generated project secrets
file. HTTP Yac may keep response previews in its extension storage. Sign in again after restarting VS Code, resetting HTTP Yac's environments,
API signing-key changes or token expiration. Admin and driver tokens are separate.
The refresh cookie belongs to the last account you signed in with.

- [ ] Step 4 complete: I followed the instructions and verified the results before continuing.

## Step 5: Authenticate both roles

Track your progress in the **Done** column: replace `☐` with `☑` after
executing each step and checking its result. These table checkboxes are edited
in the Markdown source, not clicked in the preview.

The notation below is `file.http → request_name`, under `requests/http`.
Find the matching `# @name` or the descriptive heading in that file.
Run these in order:

| Step | Done | Request | Expected result |
| --- | --- | --- | --- |
| 1 | ☐ | `auth.http → auth_register_driver` | **201** creates the driver with name, username, CNH number and matching passwords; **409** is allowed if an identifier already exists |
| 2 | ☐ | `auth.http → auth_login_admin` | **200** captures `adminToken` |
| 3 | ☐ | `auth.http → auth_me_admin` | **200**, role `ADMIN` |
| 4 | ☐ | `auth.http → auth_login_driver` | **200** captures `driverToken` without replacing `adminToken` |
| 5 | ☐ | `auth.http → auth_me_driver` | **200**, role `USER` |
| 6 | ☐ | `auth.http → auth_refresh` | **200** renews the driver session, because driver was the last login |
| 7 | ☐ | `auth.http → auth_me_session` | **200** confirms the refreshed token works |

The example driver password is `local-example-only-123`. These credentials are
for development only. Registration does not reset an existing account's password.
The example CNH number is a placeholder for local testing. To register another
driver, change the email, username and CNH number together. You can run
`scenarios-auth.http → scenarios_auth_registration_password_mismatch` to see
the API reject a confirmation that differs from the password with **422**.

- [ ] Step 5 complete: I followed the instructions and verified the results before continuing.

## Step 6: Read catalogs and create equipment

| Step | Done | Request | Expected result |
| --- | --- | --- | --- |
| 8 | ☐ | `categories.http → categories_list` | **200**; captures the `SEDAN` category as `categoryId` |
| 9 | ☐ | `specifications.http → specifications_list` | **200**; lists equipment |
| 10 | ☐ | `specifications.http → specifications_create` | **201**; creates uniquely named equipment and captures its ID/version |
| 11 | ☐ | `specifications.http → specifications_get` | **200**; reads the equipment you created |
| 12 | ☐ | `specifications.http → specifications_update` | **200**; replaces its name/description and captures the new version |
| 13 | ☐ | `specifications.http → specifications_patch` | **200**; changes only its description |

The category is selected by name, not by array position. The specification ID
comes from creation, so later mutations target your example record.

- [ ] Step 6 complete: I followed the instructions and verified the results before continuing.

## Step 7: Create a car and exercise photos

| Step | Done | Request | Expected result |
| --- | --- | --- | --- |
| 14 | ☐ | `cars.http → cars_list` | **200**; public fleet, without changing the selected `carId` |
| 15 | ☐ | `cars.http → cars_create` | **201**; creates an available Argo using your category/equipment and captures `carId`/`carVersion` |
| 16 | ☐ | `cars.http → cars_get` | **200**; detail and current version |
| 17 | ☐ | `cars.http → cars_update` | **200**; edits the description using `expectedVersion` |
| 18 | ☐ | `car-images.http → car_images_upload` | **201**; uploads the two Argo PNG fixtures and captures `imageId` |
| 19 | ☐ | `car-images.http → car_images_read` | **200**, `image/png`; reads the image bytes without authentication |
| 20 | ☐ | `car-images.http → car_images_append` | **201**; adds another photo without replacing the first two |
| 21 | ☐ | `car-images.http → car_images_delete` | **204**, empty body; deletes the captured photo |
| 22 | ☐ | `scenarios-images.http → scenarios_images_deleted_image` | **404**, expected; confirms the photo is no longer readable |
| 23 | ☐ | `scenarios-images.http → scenarios_images_invalid_upload` | **422**, expected; rejects the deliberately invalid JPEG fixture |
| 24 | ☐ | `cars.http → cars_get` | **200**; remaining photos and refreshed version |

Fixtures live in `requests/fixtures`; multipart paths resolve relative to the
HTTP file directory. There is no need to select or copy local image paths manually.
Refresh the React car list and open this car to see the same records and photos.
Images are stored in SeaweedFS, not in the API container filesystem.

Every image mutation can change the car version. Run `cars.http → cars_get` before a later
manual edit if your version is stale; do not guess `expectedVersion`.

- [ ] Step 7 complete: I followed the instructions and verified the results before continuing.

## Step 8: Rent and return the car

Before sending `rentals_create`, confirm that `cars_create` returned **201**
in this VS Code window and environment. Its response saves `state.carId`;
`cars_list` only lists cars and does not select one. Also sign in as driver.
After restarting VS Code or resetting HTTP Yac, repeat the login/catalog/create
steps to restore the in-memory values. A car created in React or another CLI
process does not populate this editor's request state.

| Step | Done | Request | Expected result |
| --- | --- | --- | --- |
| 25 | ☐ | `rentals.http → rentals_create` | **201**; driver rents the car with a return date two days ahead; captures `rentalId` |
| 26 | ☐ | `rentals.http → rentals_list` | **200**; includes only that driver's rentals |
| 27 | ☐ | `cars.http → cars_get` | **200**, `available=false` |
| 28 | ☐ | `scenarios-rentals.http → scenarios_rentals_unavailable_car` | **409**; administrator cannot rent the same occupied car |
| 29 | ☐ | `scenarios-rentals.http → scenarios_rentals_foreign_return` | **404**; administrator cannot return the driver's rental |
| 30 | ☐ | `rentals.http → rentals_return` | **200**; fills return time/total and releases the car |
| 31 | ☐ | `cars.http → cars_get` | **200**, `available=true` |
| 32 | ☐ | `scenarios-rentals.http → scenarios_rentals_return_twice` | **409**; duplicate return is rejected |
| 33 | ☐ | `scenarios-rentals.http → scenarios_rentals_invalid_period` | **422**; expected return less than 24 hours ahead is rejected |
| 34 | ☐ | `scenarios-rentals.http → scenarios_rentals_delete_history` | **409**; rental history prevents deleting the car |

An immediate return costs **15000 centavos (R$150)** for this example. Billing
uses completed 24-hour periods, at least one daily charge, and completed days
late for the fine. The server uses the current car prices at return time.
User identity, start time, actual return time and total are assigned by the server.

An account can have only one open rental. If an earlier run stopped after
creation, use `rentals.http → rentals_list` to find it, then temporarily replace
`{{state.rentalId}}` in the return URL with that rental ID and send the return.
Restore the placeholder afterward. Do not delete the database to resolve
this ordinary workflow conflict.

- [ ] Step 8 complete: I followed the instructions and verified the results before continuing.

## Step 9: Try deletion and finish the session

The rented car is retained as history. To test successful deletion separately:

1. Run `cars.http → cars_create` again. This creates a different car and replaces runtime `carId`.
2. Run `cars.http → cars_mark_unavailable`: expect **200**, unavailable car with equipment cleared.
3. Run `car-images.http → car_images_upload` and `car-images.http → car_images_read`: expect **201** and **200**.
4. Run `cars.http → cars_delete`: expect **204**, because this second car has never been rented.
5. Optionally run `specifications.http → specifications_delete`: expect **204**; it also removes that
   equipment assignment from cars. Create another specification before creating
   another car with the sample request.
6. Run `auth.http → auth_logout`: expect **204**. This ends the driver's current refresh session.
7. Run `scenarios-auth.http → scenarios_auth_refresh_after_logout`: expect **401**.

Rerun the logins before another workflow. An issued JWT has its own expiry;
logout invalidates the refresh session, not all previously issued access tokens.

- [ ] Step 9 complete: I followed the instructions and verified the results before continuing.

### Step 9a: Recover a driver's password

This flow uses the Mailpit inbox started by `make dev`. Run it after Step 5,
while the example driver still exists. Keep the browser and HTTP Yac open.

| Step | Done | Action | Expected result |
| --- | --- | --- | --- |
| 1 | ☐ | In React, open **Sign in → Forgot password?**, enter `driver@example.com` and send | The page shows the same confirmation for existing and unknown addresses |
| 2 | ☐ | Open http://localhost:8025 and read the latest message to that driver | The email has a **Reset password** link to the React application |
| 3 | ☐ | Open the link, enter a new password twice and submit | The page confirms that the password changed |
| 4 | ☐ | Sign in using the new password, then reopen the email link | Sign-in succeeds; the used link reports an invalid or expired token |
| 5 | ☐ | Alternatively, send `password-recovery.http` requests top to bottom in HTTP Yac | **202** request, **200** inbox/message, **204** reset, then **400** on replay |

The link expires after 30 minutes and only its hash is kept in PostgreSQL.
Requesting another link replaces the earlier one. If you use the sample driver
again in Step 5 or the HTTP Yac smoke flow, restore its original password by
requesting a new reset link or register a different driver. In Compose, use the
Mailpit inbox at http://localhost:8026 and select the `compose` HTTP Yac
environment. In Kubernetes, forward `svc/fleet-mailpit` as shown below.

- [ ] Step 9a complete: I requested, opened and used a reset link, then verified that it cannot be reused.

## Step 10: Run the normal workflow automatically

With the API running, in a separate terminal:

```sh
cd requests/http
npx --yes httpyac@6.16.7 send auth.http categories.http specifications.http cars.http car-images.http rentals.http --tag smoke --bail --output short
```

This runs the 24 normal requests, including session refresh, CRUD edits, image
upload/read/delete and rental creation/return. It creates example data and retains
the rented car's history. It does not run manual deletions or expected-error
scenarios. The CLI reports 28 requests including four automatic CSRF calls.
This check is optional; the manual VS Code walkthrough does not require a runner.

Do not use an unfiltered **Run All**: some examples intentionally require a
specific intermediate state, such as testing an unavailable car before its return.
Separate CLI invocations do not share runtime tokens or IDs.

- [ ] Step 10 complete: I executed and verified this optional workflow, or intentionally skipped it for my chosen setup.

## Step 11: Verify the API, catalog, authentication and rental with Playwright

Run these commands from the repository root. Docker Desktop must be running;
complete `make setup` from Step 2 once before running the test.

1. In **terminal 1**, start the local stack (or keep the `make dev` process from
   Step 3 running):

   ```sh
   make dev
   ```

   Wait for Spring to report that it started on port 8080 and Vite on port
   5173. Confirm the public endpoint responds before starting Playwright:

   ```sh
   curl -i http://localhost:8080/cars
   ```

   Expect **200 OK** and a JSON array of development cars.

2. Install Playwright's Chromium browser once, then run every API and browser journey
   in **terminal 2**:

   ```sh
   cd apps/web
   npx playwright install chromium
   cd ../..
   npm --prefix apps/web run test:e2e
   ```

   Expect `13 passed`: one public API check, one catalog journey, five authentication journeys, two
   complete-registration journeys, two administrator form journeys, one rental journey and one
   password-recovery journey that reads the reset link from Mailpit.
   The authentication tests cover driver registration and login, duplicate
   email, invalid admin credentials followed by a successful retry, session
   restore and sign out, redirect after admin login, and denial for a driver
   opening an admin page. The rental journey registers a new driver, rents one
   available car, confirms that it becomes unavailable, returns it and confirms
   that it becomes available again.

   Playwright discovers all `*.pw.ts` files under `apps/web/e2e/`, so a new
   journey needs no package script or CI change. To run one file, pass its path:

   ```sh
   npm --prefix apps/web run test:e2e -- e2e/auth-journeys.pw.ts
   ```

   These tests use the seeded development administrator from Step 3. Set
   `RENT_CARS_ADMIN_EMAIL` and `RENT_CARS_ADMIN_PASSWORD` if your local seed
   differs. Each run registers four drivers with unique test emails in the
   development database; they do not depend on earlier runs or a fixed driver ID.
   The rental journey creates one additional driver. A successful run returns
   the car before finishing; the completed rental remains in its history. The
   administrator form journeys create and remove a car and a specification with
   unique test names.
   The recovery journey creates a driver and leaves its completed email in Mailpit.
   Press Ctrl+C in terminal 1 when finished.

The public API test sends one unauthenticated **GET /cars** request to the Spring API at
`http://localhost:8080`. The test checks status 200, JSON, the seeded catalog's
basic fields and alphabetical order. Playwright uses its HTTP request fixture:
the API test does **not** open a browser and needs neither a browser download
nor a login. It expects at least one car, so use the seeded development API.

To target an API listening on another port, set its root URL, for example:

```sh
RENT_CARS_API_URL=http://localhost:18080 npm --prefix apps/web run test:e2e -- e2e/public-cars.pw.ts
```

If the test reports a connection error, check that terminal 1 is still running
and that `curl` succeeds. If it reports an empty catalog, check that the API is
using the development profile and has completed its seed migration. All Playwright
checks run in the `Playwright journeys` GitHub Actions workflow on relevant
pull requests and pushes to master.

For new API cases, keep each test focused on an observable HTTP contract. Use
the isolated `request` fixture for public calls, avoid shared tokens and fixed
database IDs, and assert response data instead of implementation details. Keep
Playwright tests in `apps/web/e2e/` with the `.pw.ts` suffix so Vitest unit tests
and browser checks run separately.

The scenario name uses **Given / When / Then**, and `test.step` prints each real
action or assertion while the test runs. Given names the prepared state, When
performs the request, and Then checks the result. This gives a readable BDD
sequence without a separate Gherkin parser. The catalog browser journey uses
steps like these:

```ts
await test.step('When the guest opens that car', async () => {
  await page.getByRole('link', { name: carName }).click()
})
await test.step('Then the car details and a way back are visible', async () => {
  await expect(page.getByRole('heading', { name: carName, exact: true })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Back to cars' })).toBeVisible()
})
```

Prefer visible roles and labels for clicks and form fields, then assert the
result on screen. Keep setup and steps real; do not add empty narrative steps or
fixed sleeps. See the [Playwright journey skill](../.agents/skills/rent-cars-playwright-journey/SKILL.md)
when extending browser coverage. For another Vite host, set `RENT_CARS_WEB_URL`;
for another API host, set `RENT_CARS_API_URL`; for another Mailpit inbox, set
`RENT_CARS_MAILPIT_URL`.

- [ ] Step 11 complete: I ran the public API and all twelve browser journeys and checked that they passed.

## Step 12: Alternative: run everything in containers

You can skip running Java and React on the host in this mode. Docker builds both
applications using their Dockerfiles; host Node is only needed for HTTP Yac CLI.
From the repository root:

```sh
docker compose -f compose.stack.yaml up --build -d --wait
docker compose -f compose.stack.yaml ps
curl -i http://localhost:8081/api/cars
```

Open React at **http://localhost:8081** and the email inbox at
**http://localhost:8026**. Select `compose` with
**httpyac: Select Environment**, or use the CLI:

```sh
cd requests/http
npx --yes httpyac@6.16.7 send auth.http categories.http specifications.http cars.http car-images.http rentals.http --tag smoke --bail --output short --env compose
```

Compose has its **own database and photo volumes**. Records created in host
local development will not appear here. Repeat the HTTP Yac logins/setup rather
than reusing IDs or tokens from the other environment.

Inspect startup failures with:

```sh
# Repository root:
docker compose -f compose.stack.yaml logs --tail=100 api web postgres s3 mailpit
```

- [ ] Step 12 complete: I executed and verified this optional workflow, or intentionally skipped it for my chosen setup.

## Step 13: Alternative: Docker Desktop Kubernetes

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

Expect the API, web, PostgreSQL, SeaweedFS and Mailpit pods to become ready and their
persistent claims to be bound. Open React at **http://localhost:8082** and choose
**k8s** in HTTP Yac. Its API base URL is **http://localhost:8082/api**. Repeat the
same numbered HTTP Yac walkthrough with fresh logins and IDs.

For the password-recovery inbox examples, keep this command running in a second
terminal while sending `password-recovery.http`:

```sh
kubectl --context docker-desktop -n rent-cars port-forward svc/fleet-mailpit 8025:8025
```

```sh
# From requests/http:
npx --yes httpyac@6.16.7 send auth.http categories.http specifications.http cars.http car-images.http rentals.http --tag smoke --bail --output short --env k8s
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

- [ ] Step 13 complete: I executed and verified this optional workflow, or intentionally skipped it for my chosen setup.

## Step 14: Choose the right URL

| Mode | React | HTTP Yac API base URL | Database and photos |
| --- | --- | --- | --- |
| `make dev` | `http://localhost:5173` | `http://localhost:8080` (`local`) | Development Compose volumes |
| Full Compose | `http://localhost:8081` | `http://localhost:8081/api` (override) | Separate stack volumes |
| Docker Desktop Kubernetes | `http://localhost:8082` | `http://localhost:8082/api` (`k8s`) | PVCs in `rent-cars` |

The `/api` prefix belongs to the web proxy. Direct Spring requests on port 8080
do **not** include it. Each environment needs its own login and record IDs.

- [ ] Step 14 complete: I followed the instructions and verified the results before continuing.

## Step 15: Stop, reset and run checks

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

- [ ] Step 15 complete: I followed the instructions and verified the results before continuing.

## Step 16: Common problems

| Symptom | What to check |
| --- | --- |
| `java` or Maven reports the wrong release | Select JDK 25 in the terminal and IDE; confirm `JAVA_HOME` and `javac -version` |
| `node` fails or npm uses a different version | Select/reinstall Node 24.16.0, reopen the terminal, and rerun `make setup` |
| Docker cannot connect | Start Docker Desktop and wait for `docker version` to show the server |
| Port already in use | Stop the other instance; do not run `make dev` and `make api-dev` together |
| Unresolved token or ID | Run the matching login/catalog/create prerequisite in the same VS Code window and API environment |
| Login returns 403 | Keep cookie handling enabled and use HTTP Yac; `common.http` must obtain `/csrf` before session writes |
| Categories return 401 after login | Send admin login and categories using HTTP Yac in the same VS Code window; rerun login if expired or reset |
| Protected API returns 401 | Rerun the relevant login; JWTs expire and dev API restarts change signing keys |
| Catalog operation returns 403 | Use the administrator token, not the driver's token |
| Car/specification update returns 409 | Read the resource again to capture the current version |
| Rental creation returns 409 | Return the driver's open rental, or select an available car |
| Car deletion returns 409 | A car with rental history is retained; create an unrented example for deletion |
| No pods in the default namespace | Use `-n rent-cars` and context `docker-desktop` |
| Pod has ImagePullBackOff / exec format error | Check repository/tag, registry access and matching CPU architecture |
| Rentals are missing only in Kubernetes | Publish an API image containing the feature; `k8s-up` does not build source |
| Photos/data differ between modes | Each mode has separate storage; check the selected HTTP Yac environment |

You have completed onboarding when the React catalog opens, HTTP Yac confirms both
roles, you can create/edit a car, upload/read/delete its photos, and a driver can
rent and return it with availability changing correctly.

- [ ] Step 16 complete: I reviewed the troubleshooting guidance and resolved any issues encountered.
