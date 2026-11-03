CREATE TABLE cars (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name varchar(255) NOT NULL CHECK (name ~ '[^[:space:]]'),
    description varchar(255) NOT NULL CHECK (description ~ '[^[:space:]]'),
    brand varchar(255) NOT NULL CHECK (brand ~ '[^[:space:]]'),
    license_plate varchar(255) NOT NULL UNIQUE CHECK (
        license_plate ~ '[^[:space:]]' AND license_plate = upper(trim(license_plate))
    ),
    available boolean NOT NULL DEFAULT true,
    daily_rate_cents integer NOT NULL CHECK (daily_rate_cents >= 0),
    fine_amount_cents integer NOT NULL CHECK (fine_amount_cents >= 0),
    category_id uuid NOT NULL REFERENCES categories(id) ON DELETE CASCADE ON UPDATE CASCADE,
    inserted_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX cars_category_id_index ON cars(category_id);

CREATE TABLE cars_specifications (
    car_id uuid NOT NULL REFERENCES cars(id) ON DELETE CASCADE ON UPDATE CASCADE,
    specification_id uuid NOT NULL REFERENCES specifications(id) ON DELETE CASCADE ON UPDATE CASCADE,
    PRIMARY KEY (car_id, specification_id)
);

CREATE INDEX cars_specifications_specification_id_index ON cars_specifications(specification_id);
