CREATE TABLE cars_images (
    id uuid PRIMARY KEY,
    car_id uuid NOT NULL REFERENCES cars(id) ON DELETE CASCADE,
    storage_key varchar(255) NOT NULL UNIQUE,
    content_type varchar(100) NOT NULL CHECK (content_type IN ('image/jpeg', 'image/png')),
    size_bytes bigint NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 5242880),
    inserted_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX cars_images_car_id_index ON cars_images(car_id);

-- Independent upload intent survives rollback; deletion intent commits with the deletion.
CREATE TABLE image_cleanup_jobs (
    storage_key varchar(255) PRIMARY KEY,
    available_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE FUNCTION queue_deleted_car_image() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO image_cleanup_jobs(storage_key) VALUES (OLD.storage_key)
    ON CONFLICT (storage_key) DO UPDATE SET available_at = CURRENT_TIMESTAMP;
    RETURN OLD;
END;
$$;
CREATE TRIGGER car_image_cleanup AFTER DELETE ON cars_images
FOR EACH ROW EXECUTE FUNCTION queue_deleted_car_image();
