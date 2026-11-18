# Validate the merged stack locally

Run from the repository root. Use Docker Compose v2, Java 25, Node 24.16.0 and npm. For the Kubernetes steps, install kind, Helm 3, kubectl and OpenSSL. Use the same terminal for the exported image variables.

## 1. Trace and test renewal

The migration `V7__refresh_sessions.sql` creates the refresh session tables. Under the Java `auth` package, read `SessionController` for the HTTP contract and `RefreshSessions` for locking, rotation, expiry and replay handling. In the web app, follow `features/auth/protected-fetch.ts` from a 401 into `renewal.ts`. The editor regression lives in `SessionLayout.test.tsx`.

```sh
(cd apps/api && ./mvnw -Dtest=SessionControllerTest test)
make setup
npm --prefix apps/web run test:ci -- src/features/auth/renewal.test.ts src/features/auth/components/SessionLayout/SessionLayout.test.tsx
```

## 2. Run the applications locally

Use separate terminals so the API can be restarted without stopping the browser
development server:

```sh
# Terminal 1:
make api-dev

# Terminal 2:
make web-dev

# Optional terminal 3:
make recommendations-dev
```

Open React at http://localhost:5173 and the Mailpit inbox at
http://localhost:8025. Docker Compose runs the supporting infrastructure only.

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

## 3. Publish each tested image

Sign in to your registry using an account with package write access. For Docker Hub, sign in with your Docker ID. These commands publish the platform built locally.

```sh
docker login -u guuhworship
export TAG=$(git rev-parse --short=7 HEAD)
export API_IMAGE=rent-cars-api:$TAG WEB_IMAGE=rent-cars-web:$TAG
export REGISTRY=docker.io/guuhworship
docker build -t "$API_IMAGE" apps/api
docker build -t "$WEB_IMAGE" apps/web
docker tag "$API_IMAGE" "$REGISTRY/rent-cars-api:$TAG"
docker push "$REGISTRY/rent-cars-api:$TAG"
docker tag "$WEB_IMAGE" "$REGISTRY/rent-cars-web:$TAG"
docker push "$REGISTRY/rent-cars-web:$TAG"
docker buildx imagetools inspect "$REGISTRY/rent-cars-api:$TAG"
docker buildx imagetools inspect "$REGISTRY/rent-cars-web:$TAG"
```

Confirm the tag and platform in each manifest. Use a multi-platform build if deploying to a cluster with a different architecture.

## 4. Deploy those images with Helm

Create a dedicated cluster and credentials. Run the Secret creation once per new namespace and retain it with the database.

```sh
kind create cluster --name rent-cars
kubectl config use-context kind-rent-cars
kubectl create namespace rent-cars
(
  set -eu
  umask 077
  keys=$(mktemp -d)
  trap 'rm -f "$keys/private.pem" "$keys/public.pem" "$keys/postgres-password"; rmdir "$keys"' EXIT
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$keys/private.pem"
  openssl pkey -in "$keys/private.pem" -pubout -out "$keys/public.pem"
  openssl rand -hex 32 | tr -d '\n' > "$keys/postgres-password"
  kubectl -n rent-cars create secret generic rent-cars-credentials --from-file="$keys"
)
```

For public packages, run the following commands. For private packages, first create a registry pull Secret in the same namespace and add `--set 'imagePullSecrets[0].name=YOUR_PULL_SECRET'` to the install command. For offline testing with the images already in Docker, use `kind load docker-image "$API_IMAGE" "$WEB_IMAGE" --name rent-cars` and replace the two repository overrides below with `--set-string api.image.repository=rent-cars-api --set-string web.image.repository=rent-cars-web --set api.image.pullPolicy=Never --set web.image.pullPolicy=Never`.

```sh
helm lint helm
helm upgrade --install fleet ./helm -n rent-cars --wait --timeout 6m \
  --set api.demoData=true \
  --set-string api.image.repository="$REGISTRY/rent-cars-api" \
  --set-string web.image.repository="$REGISTRY/rent-cars-web" \
  --set-string api.image.tag="$TAG" --set-string web.image.tag="$TAG"
kubectl get pods,pvc -n rent-cars
helm test fleet -n rent-cars --logs
kubectl port-forward -n rent-cars svc/fleet-web 8082:8080
```

Open [the Kubernetes web app](http://localhost:8082) in a separate browser profile or private session. Cookies on localhost can be shared across ports. Sign in, reload and check for a successful refresh. Sign out and confirm a protected route redirects to login. Unlike local development with generated keys, this chart mounts a persistent JWT key. Restarting the API consequently preserves a valid JWT. Plain HTTP testing is intended for localhost: access via a non-loopback hostname requires HTTPS for secure cookies and Web Locks.

Stop port forwarding with Ctrl+C. Stop the local application terminals and use `make infra-stop` to stop infrastructure while keeping its data. To remove the Kubernetes release, run `helm uninstall fleet -n rent-cars`. Deleting the dedicated kind cluster also deletes its database.
