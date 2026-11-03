CREATE TABLE specifications (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name varchar(255) NOT NULL,
    description text NOT NULL,
    inserted_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT specifications_name_index UNIQUE (name),
    CONSTRAINT specifications_name_present CHECK (name ~ '[^[:space:]]'),
    CONSTRAINT specifications_description_present CHECK (description ~ '[^[:space:]]')
);
