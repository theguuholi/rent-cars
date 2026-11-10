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

The chart defaults target local Docker Desktop: release `fleet`, port `8082`,
demo data enabled and SeaweedFS storage. Images and storage settings are in
`values.yaml`. API/web use
`imagePullPolicy: Always`, so restarting them pulls the current published tag.
The credentials Secret is created once and reused until `k8s-down`.
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
