# Car images implementation plan

Backend scope updated after PR #34 and the S3-only development setup: reuse the existing car CRUD and JPA aggregate, with one S3 implementation. React upload and gallery behavior are documented in the root README. No background cleanup infrastructure.

1. Configure Spring multipart limits and S3 destinations through environment-specific settings.
2. Persist CarImage metadata associated with Car.
3. Use JPA cascade and orphan removal through CarRepository.
4. Use S3ImageStorage behind ImageStorage in every environment: SeaweedFS for development and AWS S3 for production.
5. Validate image contents inside the upload operation; Spring handles multipart size limits.
6. Keep car creation and updates JSON-only; append multiple images through PATCH /cars/{id}/images.
7. Remove one image through DELETE /cars/{id}/images/{imageId}; serialize image mutations on the car and advance its edit version.
8. Return images in car detail and serve bytes via GET /cars/{id}?imageId=... with existing permissions.
9. Delete old files after commit and attempt new-file cleanup after rollback; document manual recovery limitations.
10. Test the HTTP contract, associations, rollback, S3 integration and migrations; run make check-api and retain CI checks.
11. Provide executable REST Client examples for creation, update, read and deletion with committed fixtures.
