# Included by the root Makefile; all paths are relative to the repository root.

.PHONY: dev

dev: infra-up
	docker compose --profile kafka-ui up -d kafka-ui
	@printf '%s\n' 'Web: http://localhost:5173' "Swagger: http://localhost:$${SERVER_PORT:-8080}/swagger-ui.html" 'Grafana: http://localhost:3000' 'Mailpit: http://localhost:8025' "Kafka UI: http://localhost:$${KAFKA_UI_PORT:-8084}"
	HDFS_ARCHIVE_ENABLED=$(HDFS_ENABLED) RENT_MESSAGING_ENABLED=true AWS_ACCESS_KEY_ID=rent-cars-dev AWS_SECRET_ACCESS_KEY=rent-cars-dev-secret API_TARGET="$${API_TARGET:-http://127.0.0.1:$${SERVER_PORT:-8080}}" npm --prefix apps/web run dev:stack
