#!/bin/sh
set -eu

# Run on every startup, including existing PostgreSQL volumes. Flyway owns tables.
until pg_isready --quiet; do sleep 2; done
psql -X --set=ON_ERROR_STOP=1 <<'SQL'
SELECT pg_advisory_lock(829001);
SELECT 'CREATE DATABASE recommendations'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'recommendations')
\gexec
SELECT pg_advisory_unlock(829001);
SQL
