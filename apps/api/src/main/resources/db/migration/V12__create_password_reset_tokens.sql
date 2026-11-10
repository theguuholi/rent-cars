CREATE TABLE password_reset_tokens (
    digest bytea PRIMARY KEY,
    account_id uuid NOT NULL UNIQUE REFERENCES accounts(id) ON DELETE CASCADE,
    expires_at timestamptz NOT NULL
);
