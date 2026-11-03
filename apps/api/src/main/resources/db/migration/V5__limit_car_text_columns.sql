-- Check existing rows before narrowing. PostgreSQL can discard trailing spaces when
-- assigning to a shorter varchar, so require an explicit cleanup for every oversized row.
ALTER TABLE cars ADD CONSTRAINT cars_text_fits_new_limits
    CHECK (char_length(name) <= 100 AND char_length(brand) <= 80 AND char_length(license_plate) <= 20);

ALTER TABLE cars
    ALTER COLUMN name TYPE varchar(100),
    ALTER COLUMN brand TYPE varchar(80),
    ALTER COLUMN license_plate TYPE varchar(20);

ALTER TABLE cars DROP CONSTRAINT cars_text_fits_new_limits;
