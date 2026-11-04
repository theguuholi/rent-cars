# Run Rent Cars on local Kubernetes

The chart installs one Spring Boot API, one Nginx web server and one PostgreSQL 17 instance. The browser uses a single origin. Nginx serves the React bundle and forwards `/api/*` to Spring with the prefix removed. PostgreSQL uses a persistent volume. Kubernetes mounts the RSA keys so access tokens remain verifiable when API pods restart.

## 1. Create a local cluster

Run these commands from the repository root with Docker, kind, kubectl, Helm 3 and OpenSSL installed. The chart needs a default StorageClass that can provision a ReadWriteOnce volume. kind provides one.

```sh
kind create cluster --name rent-cars
kubectl config use-context kind-rent-cars
```

The Java build uses JDK 25 and the Maven Wrapper. Its distroless runtime contains the JRE and the layered application, without a shell or a package manager. The web build uses the project's pinned Node version and npm lockfile. Its runtime serves static files from Nginx. Both application containers run as non-root with read-only root filesystems.

The default values pull the published ARM64 images from Docker Hub. Override `api.image.repository`, `api.image.tag`, `web.image.repository` and `web.image.tag` independently for other releases. With the default `Always` pull policy, pods resolve the tag when starting. To deploy a new build, publish a new tag and upgrade with that tag.

## 2. Create the namespace and credentials once

Keep the Secret for as long as you keep the database volume. The PostgreSQL password initializes an empty database. Changing the Secret alone does not change the password in an existing database.

```sh
kubectl create namespace rent-cars
(
  set -eu
  umask 077
  keys=$(mktemp -d)
  trap 'rm -f "$keys/postgres-password" "$keys/private.pem" "$keys/public.pem"; rmdir "$keys"' EXIT
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$keys/private.pem"
  openssl pkey -in "$keys/private.pem" -pubout -out "$keys/public.pem"
  openssl rand -hex 32 | tr -d '\n' > "$keys/postgres-password"
  kubectl -n rent-cars create secret generic rent-cars-credentials --from-file="$keys"
)
```

## 3. Install and verify

```sh
helm lint helm
helm upgrade --install fleet ./helm -n rent-cars --wait --timeout 6m
helm test fleet -n rent-cars --logs
kubectl -n rent-cars port-forward svc/fleet-web 8081:8080
```

Open [Rent Cars](http://localhost:8081) and [the fleet API](http://localhost:8081/api/cars). An empty fleet returns `[]`. The Helm test requests a nested SPA URL and reads the fleet through the web proxy. To install local demo cars and the development admin, add `--set api.demoData=true` to the install command. The demo login is documented in the repository README. Use demo data only on a private local cluster.

## Docker Desktop access without port forwarding

Once the namespace and Secret exist, expose the web Service on port 8082:

```sh
helm upgrade --install fleet ./helm --kube-context docker-desktop -n rent-cars --reset-then-reuse-values \
  --set web.service.type=LoadBalancer --set web.service.port=8082 --wait --timeout 6m
kubectl --context docker-desktop -n rent-cars get svc fleet-web
```

Open http://localhost:8082 or http://localhost:8082/api/cars. Nginx proxies API requests inside the cluster. Flyway applies pending migrations on API startup before the pod becomes ready. Inspect applied versions in the database's `flyway_schema_history` table.

## Use published images

Choose repositories you can push to. An immutable tag for each build lets Kubernetes roll the deployments. For a private registry, configure `imagePullSecrets` in a values file with the name of a registry Secret in the release namespace.

```sh
registry=docker.io/guuhworship
tag=$(git rev-parse --short=12 HEAD)
docker tag rent-cars-api:local "$registry/rent-cars-api:$tag"
docker tag rent-cars-web:local "$registry/rent-cars-web:$tag"
docker push "$registry/rent-cars-api:$tag"
docker push "$registry/rent-cars-web:$tag"

helm upgrade --install fleet ./helm -n rent-cars --wait --timeout 6m \
  --set-string api.image.repository="$registry/rent-cars-api" \
  --set-string web.image.repository="$registry/rent-cars-web" \
  --set-string api.image.tag="$tag" --set-string web.image.tag="$tag"
```

A local build targets the machine's CPU architecture. For a mixed ARM64/AMD64 cluster, publish a multi-platform build of each Dockerfile with `docker buildx build --platform linux/amd64,linux/arm64 --push`.

If you have an Ingress controller, set `ingress.enabled=true`, `ingress.className` and `ingress.host`. Point the hostname at the controller. Port forwarding works without an Ingress controller.

## Stop the stack

```sh
helm uninstall fleet -n rent-cars
```

The PostgreSQL claim and the manually created Secret survive uninstall. Reinstall with the same namespace and release name to reuse them. When you want to discard the entire local environment including its data, delete the dedicated cluster with `kind delete cluster --name rent-cars`.
