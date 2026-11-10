# API requests in VS Code

Use **HTTP Yac** (`anweber.vscode-httpyac`), recommended by this workspace.
See the [Getting Started tutorial](../docs/getting-started.md) for installation,
React/Spring startup, expected responses and the full numbered walkthrough.

## Manual flow

1. Run `make setup`, then `make dev` from the repository root.
2. Open [auth.http](http/auth.http) and click **Send** above **Sign in as admin**.
3. Open [categories.http](http/categories.http) and send its request.
   The admin token is already available in this other file.
4. Continue with the files below. Each request captures the IDs needed later.

Local (`http://localhost:8080`) is the default. No environment selection, desktop
client, runner or token copying is required. Use **httpyac: Send** if another
HTTP extension also supplies a Send action. Requests execute only when you send
them; session writes automatically run the CSRF helper first.

| File | Responsibility / order |
| --- | --- |
| [common.http](http/common.http) | URL, credentials, shared in-memory state and CSRF |
| [auth.http](http/auth.http) | Register driver; admin login/identity; driver login/identity; refresh/identity |
| [categories.http](http/categories.http) | List and capture SEDAN category ID |
| [specifications.http](http/specifications.http) | List, create, read, replace and patch equipment |
| [cars.http](http/cars.http) | List, create, read and update a car |
| [car-images.http](http/car-images.http) | Upload, read, append and delete photos |
| [rentals.http](http/rentals.http) | Rent, list and return as driver |
| [password-recovery.http](http/password-recovery.http) | Request a reset link, read the local email, reset the password and confirm one-time use |
| `scenarios-*.http` | Expected errors; follow the prerequisites in the tutorial |

Tokens, IDs and versions are shared across files through `$global.rentCars`,
separately for each API URL. The request scripts do not write tokens into the repository. HTTP Yac may keep response previews in its own extension storage. Sign in again after
reloading VS Code, resetting environments, API key changes or token expiration.
Admin and driver tokens are independent; refresh uses the last signed-in session.
Read a car again before editing if its version changed after image operations.

Use **httpyac: Select Environment** for `compose` (8081/api) or `k8s` (8082/api).
Environment URLs live in [http-client.env.json](http/http-client.env.json).
The image examples read the existing PNG fixtures relative to each HTTP file.

To test password recovery, first register the example driver in `auth.http`.
Then send the requests in `password-recovery.http` from top to bottom. The
Mailpit inbox is at http://localhost:8025 for local development and
http://localhost:8026 for the Compose stack. The `compose` environment selects
that inbox automatically. In Kubernetes, forward the Mailpit service to local
port 8025 before sending the two inbox requests. The reset request responds
with **202** even for an unknown email; the link works once for 30 minutes.

## Optional automated check

With Node 24 and the API running:

```sh
cd requests/http
npx --yes httpyac@6.16.7 send auth.http categories.http specifications.http cars.http car-images.http rentals.http --tag smoke --bail --output short
```

Add `--env compose` or `--env k8s` for another deployment. This runs 24 workflow
requests plus four automatic CSRF requests. Separate CLI processes do not share
state with each other or VS Code. The smoke flow creates example data and retains
rental history. Do not add `--all`: logout, deletion and expected-error examples
need specific intermediate states and are intentionally excluded.
