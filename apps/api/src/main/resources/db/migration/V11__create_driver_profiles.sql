CREATE TABLE driver_profiles (
    account_id uuid PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    first_name varchar(80) NOT NULL CHECK (first_name = trim(first_name) AND first_name <> ''),
    last_name varchar(80) NOT NULL CHECK (last_name = trim(last_name) AND last_name <> ''),
    user_name varchar(40) NOT NULL UNIQUE CHECK (user_name ~ '^[a-z0-9._-]{3,40}$'),
    drive_license varchar(11) NOT NULL UNIQUE CHECK (drive_license ~ '^[0-9]{11}$')
);
