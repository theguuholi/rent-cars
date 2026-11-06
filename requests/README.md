# API examples with Bruno

For tools, first startup and an end-to-end numbered walkthrough, start with the
[onboarding tutorial](../docs/getting-started.md).

Open `requests/bruno` as a collection in Bruno or its official VS Code extension.
Use the text editor view to edit `.bru` files. Each file contains one request.
The collection was validated with Bruno CLI **4.3.0** against Spring, PostgreSQL
and SeaweedFS. The old REST Client `.http` file has been replaced.

## Start

1. Run `make api-dev` from the repository root, or `make k8s-up` for Docker Desktop Kubernetes.
2. Open the collection and select **local** (`http://localhost:8080`) or **k8s** (`http://localhost:8082/api`).
3. Keep Bruno's cookie jar enabled. Run the requests below in order, or run the collection with the **smoke** tag filter.

| Folder | Normal order |
| --- | --- |
| `auth` | `register-driver` → `login-admin` → `me-admin` → `login-driver` → `me-driver` → `refresh` → `me-session` |
| `categories` | `list` (selects SEDAN by name) |
| `specifications` | `list` → `create` → `get` → `update` → `patch` |
| `cars` | `list` → `create` → `get` → `update` |
| `car-images` | `upload` → `read` → `append` → `delete` |
| `rentals` | `create` → `list` → `return` |

The normal run creates an example specification, car, photos and rental history.
It creates unique equipment names and plates so you can repeat it. Driver
registration accepts 409 when the email already exists; login still verifies
the password. If a run stops with an open rental, list and return it before
starting another. For a resumed CLI flow, pass `--env-var rentalId=<id>` and include driver login
and `rentals/return` in the same command.

## Shared values

- `collection.bru`: development-only admin/driver credentials. Override them in
  your selected environment when needed; do not commit personal credentials.
- `environments/*.bru`: the server URL. Tokens and IDs never belong in these files.
- `adminToken` and `driverToken`: separate runtime tokens captured at login.
- `categoryId`, `specificationId`, `carId`, `imageId`, `rentalId`: captured from responses.
- `specificationVersion` and `carVersion`: refreshed after reads and writes that return them.

Catalog folders inherit administrator authentication. Rentals inherit driver
authentication. Public reads explicitly use no authentication. Login is always
explicit; only CSRF preparation reuses `auth/csrf` automatically via a one-line
script. No scripts or helpers outside the `.bru` files are required.

The refresh cookie represents the **last signed-in account**, which is the driver
in the normal run. `auth/refresh` updates only that account's token;
`auth/me-session` checks it. `auth/logout` ends that device session.

Runtime variables are shared within the collection in the app and within one
CLI execution. Separate CLI commands do not share them. After changing
environments or restarting the app/API, sign in again and recreate/select IDs
from that environment. Run `cars/get` before editing after an image deletion;
a stale `carVersion` intentionally returns 409. `cars/list` never silently
selects an existing car for destructive operations.

## CLI

With Node.js 24 and npm, run from the collection directory:

```sh
cd requests/bruno
npx --yes @usebruno/cli@4.3.0 run --env local --tags smoke -r --bail
```

Use `--env k8s` for Docker Desktop Kubernetes. To test the full Compose proxy,
add `--env-var baseUrl=http://localhost:8081/api` to the local command.

The **smoke** filter runs the normal workflow. Do not run every file without a
filter: manual deletion and error examples have deliberately different prerequisites.
For an individual CLI flow, pass its request paths in order in the same command.
Bruno requires no project npm dependencies or global installation.

## Manual and error examples

These are excluded from the smoke run. Each asserts the expected HTTP status.

| Request | Run after |
| --- | --- |
| `scenarios/rentals/unavailable-car` | `rentals/create`, before returning |
| `scenarios/rentals/foreign-return` | `rentals/create` |
| `scenarios/rentals/return-twice` | `rentals/return` |
| `scenarios/rentals/invalid-period` | Creating a car; expects 422 |
| `scenarios/rentals/delete-history` | Creating a rental; expects 409 even after return |
| `scenarios/images/deleted-image` | `car-images/delete` |
| `scenarios/images/invalid-upload` | Creating a car; uses the deliberately invalid JPEG fixture |
| `scenarios/auth/refresh-after-logout` | `auth/logout` |
| `cars/mark-unavailable` | `cars/get`; changes availability and clears equipment |
| `cars/delete` | Creating a car that has **never** been rented |
| `specifications/delete` | Finishing the equipment walkthrough |
| `auth/logout` | Finishing the current session |

The image examples reuse the two Argo PNGs in `requests/fixtures`. File paths
are relative to the collection root, not the request file. Car deletion also
removes remaining image metadata and attempts storage cleanup. Cars with rental
history are intentionally retained; this collection does not reset your database.
