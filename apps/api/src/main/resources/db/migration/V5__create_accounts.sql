CREATE TABLE accounts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email varchar(254) NOT NULL UNIQUE CHECK (email = lower(trim(email))),
    password_hash varchar(100) NOT NULL,
    role varchar(5) NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN'))
);
