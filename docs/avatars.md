# Private account avatars

Open **My avatar** after signing in. Select a JPEG or PNG, review the preview and choose **Save avatar**. **Remove avatar** clears the account reference. The file belongs to the authenticated JWT subject. The API provides no route to read or replace another account's avatar. Account roles remain unchanged.

## HTTP contract

- `PUT /users/me/avatar` accepts multipart form data with one `image` part and returns 204.
- `GET /users/me/avatar` returns a private, uncached PNG, or 404 when absent.
- `DELETE /users/me/avatar` returns 204 and is idempotent.
- All three requests require a bearer token. Follow the avatar requests in `requests/rent-cars.http`, which include a committed PNG fixture.

Input is limited to 5 MiB and 4 million pixels. The reader verifies content independently of extension and MIME headers. JPEG and PNG are decoded and re-encoded as PNG, stripping input metadata and fitting within 512 × 512 pixels. Spring limits requests to 6 MiB including multipart overhead, matching Nginx. A file over the limit returns 413. Invalid content returns 422.

## Local persistence

The storage directory defaults to `.rent-cars/uploads` relative to the API's working directory. Set `RENT_STORAGE_DIRECTORY` to override it. Compose mounts a dedicated named volume at `/data/uploads`. Restarting an API container preserves the files. Removing the Compose volumes deletes the files and database.

Helm creates a separate avatar PVC. The non-root API writes through the pod's filesystem group. API upgrades use Recreate to support a single ReadWriteOnce volume. This local-disk backend is intended for a single API instance. Back up the database and upload volume together. The avatar PVC is retained on uninstall and must be deleted explicitly to discard images.

## Consistency and cleanup

Each upload reserves a unique object ID in PostgreSQL before writing to storage. A transaction locks that reservation and updates the account row, serializing concurrent replacements. The pointer commits only after the object write succeeds. On failure the old avatar remains available, and the reservation makes even partially written objects discoverable.

Every ten minutes, a transactional cleanup scans at most 100 objects older than one hour that have no account reference. Locked uploads are skipped. The reference is rechecked before deletion. Storage deletion is idempotent. A failed deletion rolls back removal of the database reservation so the next run retries. The grace period permits recovery after a process crash. Deleting an account also makes its avatar eligible for cleanup.

`ImageStorage` defines write, read and delete operations independently of this workflow. Local, S3 and GCS adapters implement the same contract.

Metadata uses Spring Data JPA: `AccountRepository` updates/locks account references and `AvatarObjectRepository` manages the allocation ledger. `@Transactional`, `@Lock` and `TransactionTemplate` delimit the operations. Replacement uses `Propagation.NOT_SUPPORTED` to suspend any caller transaction: `saveAndFlush` commits the reservation first, then the transaction template commits the account pointer only after storage succeeds. Keep this ordering when adding callers. Native SQL is confined to repository cleanup queries because PostgreSQL `SKIP LOCKED` and the conditional orphan deletion express concurrency guarantees that plain inherited CRUD does not provide. No schema change is needed for this persistence refactor; V8 remains unchanged.

## Validation

Run `make check-api` with Docker running and `make check-web`. Testcontainers exercises concurrent writes and storage failure with PostgreSQL. Directory tests use temporary files. Browser tests cover preview, failure, retry and deletion. In Compose, upload the fixture, restart the API, sign in again and read the avatar to verify volume persistence.

## Select a production storage provider

The API supports `RENT_STORAGE_PROVIDER=local` (default), `s3` and `gcs`. Exactly one adapter is registered. Local mode needs no cloud credentials or network access. The HTTP contract and React UI are identical in every mode. The API streams the normalized PNG through its authenticated endpoint; clients never receive cloud credentials or a public bucket URL.

| Setting | Local | S3 | GCS |
| --- | --- | --- | --- |
| `RENT_STORAGE_PROVIDER` | `local` or unset | `s3` | `gcs` |
| `RENT_STORAGE_DIRECTORY` | Optional local directory | Unused | Unused |
| `RENT_STORAGE_BUCKET` | Unused | Required existing private bucket | Required existing private bucket |
| `RENT_STORAGE_REGION` | Unused | Required bucket region | Unused |
| Credentials | None | AWS SDK default credential chain | Google Application Default Credentials |

The application does not create buckets or grant IAM permissions. Provision the bucket in the same region as the workload. Keep public access blocked and use an identity restricted to that bucket. Cloud uploads have a create-only precondition (`If-None-Match: *` for S3, `doesNotExist` for GCS). A collision fails instead of overwriting an existing image. Both SDKs have bounded connection/read timeouts and retry budgets. A provider failure rolls back the account pointer; cleanup retains its ledger entry for retry. If bucket versioning or soft delete is enabled, configure retention/lifecycle policies for old versions: deleting the current object does not necessarily reclaim all historical bytes. Do not expire live avatar objects by age alone.

### AWS S3

Configure the API environment, then start it normally:

```sh
export RENT_STORAGE_PROVIDER=s3
export RENT_STORAGE_BUCKET=your-private-avatar-bucket
export RENT_STORAGE_REGION=us-east-1
make api-dev
```

Locally, obtain temporary credentials through your AWS workflow. In AWS, prefer an IAM workload role (for example EKS Pod Identity or IRSA), supplied through the [SDK default credential chain](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html). The application needs `s3:PutObject`, `s3:GetObject` and `s3:DeleteObject` on the bucket's objects. `s3:ListBucket` can be granted on the bucket if you want missing reads to return S3 404 instead of 403. A customer-managed KMS key needs the matching KMS permissions as well. No ACL or public access permission is needed.

### Google Cloud Storage

For local access to a real development bucket, run `gcloud auth application-default login` outside the repository, then:

```sh
export RENT_STORAGE_PROVIDER=gcs
export RENT_STORAGE_BUCKET=your-private-avatar-bucket
make api-dev
```

In GKE, prefer Workload Identity Federation and [Application Default Credentials](https://cloud.google.com/docs/authentication/application-default-credentials). Grant bucket-scoped object read/create/delete permissions; `roles/storage.objectUser` is a convenient predefined role, or use a narrower custom role. Enable uniform bucket-level access and public access prevention. Do not commit service-account keys. See Google's [create-only upload guidance](https://cloud.google.com/storage/docs/uploading-objects-from-memory).

### Kubernetes

The chart keeps the local PVC and Recreate strategy for `local`. For cloud storage it omits the avatar mount/PVC and uses RollingUpdate. Supply the name of an **existing** Kubernetes ServiceAccount bound to the cloud identity; the chart does not create cloud IAM resources. Enable token automount only when the chosen identity integration requires it.

```yaml
# Example S3 values override; merge with your existing deployment settings.
api:
  storage:
    provider: s3
    bucket: your-private-avatar-bucket
    region: us-east-1
  serviceAccount:
    name: rent-cars-storage
    automountToken: false
```

For GCS, change `provider` to `gcs`, use your GCS bucket, and omit `region`. Install with `helm upgrade --install fleet ./helm -n rent-cars -f your-values.yaml --wait`. Store only non-secret configuration in the values file. Keep the auth/database Secret configured separately as described in the Helm guide.

**Changing providers or buckets does not move existing files.** Use distinct databases/buckets for development and production. To move existing data, stop API writers and cleanup, back up the database and storage, copy every `<uuid>.png` unchanged into the new bucket root, and verify object counts and contents before switching every API replica together. Keep the previous storage for rollback. Do not run replicas against different providers/buckets with the same database. The local PVC remains retained after switching; delete it only after verifying the migration and backups.

## Step-by-step verification

1. For normal local development, leave cloud variables unset and run `make dev` from the repository root. The existing Compose stack also continues to use local persistent storage.
2. Open `requests/rent-cars.http`, run the CSRF and login steps, then upload the committed PNG fixture. Expect 204.
3. Read your avatar. Expect 200, `Content-Type: image/png` and `Cache-Control: no-store`.
4. Restart the API with the same storage configuration. Log in again and read the avatar; it must still be present.
5. Remove it, expect 204, then read it again and expect 404. A different user's login cannot read this avatar.
6. To verify a real development bucket, repeat steps 2–5 after selecting `s3` or `gcs`. Inspect the bucket privately: a UUID PNG exists after upload. Removed/replaced files are intentionally retained until cleanup (older than one hour). Verify the workload identity, bucket permissions and network access in the actual deployment before production release.

Run the following from the repository root with Docker running:

```sh
make check-api
# Only SDK contract tests against disposable local emulators:
./apps/api/mvnw -f apps/api/pom.xml -Dtest=CloudImageStorageTest test
```

Testcontainers starts pinned S3Mock and fake-gcs-server containers on random ports and removes them afterward. Tests send real SDK requests to verify bytes, create-only writes, reads, missing objects and idempotent deletion. They do not require cloud credentials or billable infrastructure. Unit tests additionally cover provider failures and Spring bean selection. PostgreSQL tests cover concurrent replacement, rollback, references and cleanup. Emulators validate the API contract; they do not validate production IAM, KMS or workload identity.
