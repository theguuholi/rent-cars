#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
echo 'Starting applications. These addresses become available after each application finishes startup:'
printf '%s\n' 'Web: http://localhost:5173' "Swagger: http://localhost:${SERVER_PORT:-8080}/swagger-ui.html" 'Recommendations: http://localhost:8085/swagger-ui/index.html' 'Grafana: http://localhost:3000' 'Mailpit: http://localhost:8025' "Kafka UI: http://localhost:${KAFKA_UI_PORT:-8084}"
echo 'Keep this terminal open. Ctrl+C stops applications; make infra-stop stops infrastructure.'
exec apps/web/node_modules/.bin/concurrently --kill-others-on-fail --names api,web,recommendations \
  'bash scripts/development/app.sh api' \
  'bash scripts/development/app.sh web' \
  'bash scripts/development/app.sh recommendations'
