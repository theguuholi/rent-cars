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

`ImageStorage` defines write, read and delete operations independently of this workflow. This PR implements local storage. S3 or GCS implementations belong in a later change with contract tests for the chosen provider.

## Validation

Run `make check-api` with Docker running and `make check-web`. Testcontainers exercises concurrent writes and storage failure with PostgreSQL. Directory tests use temporary files. Browser tests cover preview, failure, retry and deletion. In Compose, upload the fixture, restart the API, sign in again and read the avatar to verify volume persistence.
