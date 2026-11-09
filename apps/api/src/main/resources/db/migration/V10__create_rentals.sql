CREATE TABLE rentals (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    car_id uuid NOT NULL REFERENCES cars(id) ON DELETE RESTRICT,
    account_id uuid NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    start_date timestamptz NOT NULL,
    expected_return_date timestamptz NOT NULL,
    end_date timestamptz,
    total_cents bigint,
    inserted_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT rentals_period CHECK (expected_return_date >= start_date + interval '24 hours'),
    CONSTRAINT rentals_return CHECK (
        (end_date IS NULL AND total_cents IS NULL) OR
        (end_date IS NOT NULL AND total_cents IS NOT NULL AND end_date >= start_date AND total_cents >= 0)
    )
);

CREATE INDEX rentals_car_id_index ON rentals(car_id);
CREATE INDEX rentals_account_id_index ON rentals(account_id);
CREATE UNIQUE INDEX rentals_open_car ON rentals(car_id) WHERE end_date IS NULL;
CREATE UNIQUE INDEX rentals_open_account ON rentals(account_id) WHERE end_date IS NULL;
