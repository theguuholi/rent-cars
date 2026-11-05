# Car images implementation plan

Scope updated after PR #34 review: Spring only, reuse the existing car CRUD and JPA aggregate. No React changes or background cleanup infrastructure.

1. Configure Spring multipart limits and local/S3 destinations.
2. Persist CarImage metadata associated with Car.
3. Use JPA cascade and orphan removal through CarRepository.
4. Keep local/S3 adapters behind ImageStorage; one bean chooses the configured provider.
5. Validate image contents inside the upload operation; Spring handles multipart size limits.
6. Accept multiple images on car creation, preserving JSON creation.
7. Append images and explicitly remove selected removeImageIds on car update, respecting expectedVersion.
8. Return images in car detail and serve bytes via GET /cars/{id}?imageId=... with existing permissions.
9. Delete old files after commit and attempt new-file cleanup after rollback; document manual recovery limitations.
10. Test the HTTP contract, associations, rollback, adapters and migrations; run make check-api and retain CI checks.
11. Provide executable REST Client examples for creation, update, read and deletion with committed fixtures.
