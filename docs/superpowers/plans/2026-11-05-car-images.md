# Car images implementation plan

Goal: Create cars and JPEG/PNG images in one multipart request, with local and S3 storage. Backend only; the React client is intentionally unchanged.

Architecture: The cars module owns keys (`cars/{carId}/{imageId}.{extension}`). Storage adapters receive complete keys. PostgreSQL metadata and a durable cleanup queue cover rollbacks and cascaded deletes. Existing JSON creation remains supported.

Approved scope: the implementation steps agreed in chat, restricted to Spring and REST Client, on branch `upload`.

1. Configure shared multipart limits (5 MB/file, 30 MB/request), local development and S3 production properties.
2. Add Flyway tables for images and cleanup jobs, including a deletion trigger covering cascades.
3. Map CarImage and the detail-only Car association; keep storage internals out of JSON.
4. Implement local and S3 adapters with an explicit provider selection and typed configuration.
5. Validate JPEG/PNG contents and bounded dimensions before storage, generating UUID keys.
6. Add multipart POST /cars while retaining JSON creation.
7. Register cleanup intent before file writes, lock jobs during upload, and clear intent in the metadata transaction. Retry orphan/deletion cleanup asynchronously.
8. Add public image reads and admin-only deletion with car/image ownership checks.
9. Keep all React files unchanged; describe the future multipart client contract in API documentation.
10. Add behavior tests, run make check-api, inspect coverage and review the diff before committing.
11. Add runnable REST Client examples with committed small image fixtures, including upload, read and delete.

Review focus: failed commit after file writes; deletion through category cascades; concurrent cleanup versus upload; unsupported/corrupt image content; safe local path resolution. Tests exercise these paths through PostgreSQL, HTTP and adapter boundaries. S3 is tested against an isolated HTTP stub; no live AWS resources are provisioned.
