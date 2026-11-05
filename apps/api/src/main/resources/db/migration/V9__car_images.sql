-- Preserve existing avatar allocations and their account foreign keys.
ALTER TABLE avatar_objects RENAME TO image_objects;
CREATE TABLE car_images (
    id uuid PRIMARY KEY REFERENCES image_objects(id),
    car_id uuid NOT NULL REFERENCES cars(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX car_images_car_id ON car_images(car_id);
