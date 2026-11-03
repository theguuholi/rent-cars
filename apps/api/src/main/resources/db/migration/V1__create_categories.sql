CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) NOT NULL,
    description VARCHAR(255), 
    inserted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT categories_name_unique UNIQUE (name),
    CONSTRAINT categories_name_uppercase CHECK (name = upper(name)),
    CONSTRAINT categories_name_not_blank CHECK (name ~ '[^[:space:]]'),
    CONSTRAINT categories_description_not_blank CHECK (description ~ '[^[:space:]]')
);