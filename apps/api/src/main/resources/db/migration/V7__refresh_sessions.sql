CREATE TABLE refresh_sessions (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    expires_at timestamptz NOT NULL
);
CREATE INDEX refresh_sessions_expiry ON refresh_sessions(expires_at);

CREATE TABLE refresh_tokens (
    digest bytea PRIMARY KEY,
    session_id uuid NOT NULL REFERENCES refresh_sessions(id) ON DELETE CASCADE,
    consumed boolean NOT NULL DEFAULT false
);
CREATE INDEX refresh_tokens_session ON refresh_tokens(session_id);
