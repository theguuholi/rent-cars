#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
export HDFS_ARCHIVE_ENABLED="${HDFS_ENABLED:-false}"
export RENT_MESSAGING_ENABLED=true
export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-rent-cars-dev}"
export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-rent-cars-dev-secret}"
export API_TARGET="${API_TARGET:-http://127.0.0.1:${SERVER_PORT:-8080}}"
case "${1:-}" in
  api) cd apps/api; exec ./mvnw spring-boot:run ;;
  web) exec npm --prefix apps/web run dev -- --strictPort ;;
  recommendations) cd apps/recommendations; exec ./mvnw spring-boot:run ;;
  *) echo 'Usage: app.sh api|web|recommendations' >&2; exit 2 ;;
esac
