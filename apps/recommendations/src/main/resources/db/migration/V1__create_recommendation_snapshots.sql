-- One bounded immutable result per UTC window cutoff. Insertion is publication.
CREATE TABLE recommendation_snapshots (
    as_of TIMESTAMPTZ PRIMARY KEY,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    algorithm VARCHAR(40) NOT NULL CHECK (algorithm = 'popular-cars-v1'),
    items JSONB NOT NULL CHECK (jsonb_typeof(items) = 'array' AND jsonb_array_length(items) <= 1000),
    parquet_path TEXT NOT NULL
);
