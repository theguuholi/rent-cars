# Car photos

Visitors can browse a car's gallery from its detail page. Administrators can add and remove images there. Each upload adds one photo; it does not replace existing gallery entries or change the car's optimistic edit version. Upload multiple photos by repeating the operation.

## API and REST Client

| Method | Endpoint | Access | Result |
| --- | --- | --- | --- |
| GET | `/cars/{carId}/images` | Public | 200, ordered `[{"id":"uuid"}]`; empty array when the gallery is empty |
| GET | `/cars/{carId}/images/{imageId}` | Public | 200 PNG with `Cache-Control: no-store` |
| POST | `/cars/{carId}/images` | ADMIN | Multipart `image` part; 201 with the `CarImage` entity and a `Location` header |
| DELETE | `/cars/{carId}/images/{imageId}` | ADMIN | 204; a missing or already removed photo returns 404 |

A missing car or a photo belonging to another car returns 404. Anonymous writes return 401; authenticated non-admin writes return 403. Missing multipart input returns 400, oversized input returns 413 and invalid image content returns 422. Unexpected storage failures preserve existing photos and return a server error.

In `requests/rent-cars.http`, browse the fleet and sign in as admin, then follow **Car photos**. Upload the committed fixture, list the gallery, read its first photo without a token, and delete it as admin. The read/delete examples use the first ID from the named gallery response. Refresh the gallery list before targeting another image. Swagger documents these operations under **Cars**.

## Validation and persistence

The API accepts JPEG/PNG content up to 5 MiB and 4 million pixels. It checks decoded content independently of the submitted filename and MIME type, strips metadata by re-encoding, and preserves aspect ratio within 1600 × 1600 pixels. SVG is not accepted. File paths and cloud credentials are never supplied by the client.

Following the [Spring upload guide](https://spring.io/guides/gs/uploading-files/), `CarController` receives a `MultipartFile` and delegates to `CarService`; downloads return a Spring `Resource`. These operations belong to the existing car controller and service, as they do in the Elixir car context. Spring Data JPA manages `CarImage` and the image allocation ledger. Spring transactions and repository locks coordinate uploads, reads and deletion. The storage key is an immutable UUID, independent of the original filename. Account avatars retain their existing endpoints and their 512-pixel normalization limit.

Flyway V9 preserves existing avatar allocations, renames their table to `image_objects`, and creates `car_images` with a car foreign key and an index. Deleting a car cascades its gallery rows. Each successful upload links an allocation to a car only after its storage write succeeds; a failed or interrupted upload remains discoverable for cleanup. Concurrent uploads append separate images rather than overwriting each other.

The shared scheduled cleanup deletes unreferenced objects older than one hour in batches of up to 100. It checks both avatar and car references, skips locked uploads and retries failed storage deletions. Deleting a photo or its car immediately removes public access through the API, while the underlying object is reclaimed asynchronously. Storage providers must remain private even though car photo reads through the API are public.

## Local, cloud and Kubernetes

Use the [Java environment walkthrough](../apps/api/README.md#avatar-storage-choose-your-environment). Both features use `RENT_STORAGE_PROVIDER=local|s3|gcs`, the same configured directory/bucket and the same Compose volume or Helm PVC. Do not switch providers with existing data before following the documented object migration procedure.

For a local smoke test:

1. Run `make setup` and `RENT_STORAGE_PROVIDER=local make dev` from the repository root.
2. Sign in as the development admin and open a car detail.
3. Add two photos; confirm both appear. Remove the second and confirm the first remains.
4. Reload and restart the API with the same storage; confirm the remaining image still loads.
5. Open the same car detail in a private browser window. Confirm photos are visible and upload/delete controls are absent.

For Compose use port 8081; for the documented Docker Desktop LoadBalancer setup use port 8082. Rebuild/publish images containing this feature before testing containers. The same UI and REST Client requests work with real S3/GCS buckets; validate workload permissions separately from local emulator tests.

## References and tests

The Elixir reference uses `CarImage`, `cars_images`, a car foreign key with cascade deletion and admin-only image writes. This implementation preserves the gallery behavior while using decoded-content validation, immutable storage objects and deferred cleanup. The `mylibrary` reference has controller/service/Spring Data repository separation, but no image-upload implementation in its master source tree.

Run `make check-api` and `make check-web`. Backend tests cover HTTP permissions and schemas, normalization, isolated car galleries, failed uploads, concurrent additions, cascading deletion, cleanup and upgrade from an existing avatar schema. SDK contract tests run against disposable local S3/GCS emulators. Frontend Given/When/Then scenarios cover loading, empty galleries, public/admin views, cancellation, upload retries, deletion, pending controls and results arriving after navigation. Run `npm --prefix apps/web run mutation` to check the whole frontend at the unchanged 100% threshold.
