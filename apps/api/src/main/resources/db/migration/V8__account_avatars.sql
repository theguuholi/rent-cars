-- Keep allocated keys until the corresponding object has been successfully removed.
CREATE TABLE avatar_objects (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE accounts ADD COLUMN avatar_key uuid UNIQUE REFERENCES avatar_objects(id);
CREATE INDEX avatar_objects_created_at ON avatar_objects(created_at);
