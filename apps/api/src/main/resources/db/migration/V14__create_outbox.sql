CREATE TABLE outbox_messages (
    sequence bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id uuid NOT NULL UNIQUE,
    rental_id uuid NOT NULL,
    payload text NOT NULL,
    occurred_at timestamptz NOT NULL,
    attempts bigint NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at timestamptz NOT NULL,
    published_at timestamptz
);
CREATE INDEX outbox_pending ON outbox_messages(sequence) WHERE published_at IS NULL;
-- No FK: published facts survive deletion of their source record.
