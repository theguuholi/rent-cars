# Included by the root Makefile; all paths are relative to the repository root.

.PHONY: kafka-up kafka-ui kafka-events

kafka-up: check-local-environment
	docker compose up -d --wait kafka
	docker compose run --rm kafka-init

kafka-ui: kafka-up
	docker compose --profile kafka-ui up -d kafka-ui
	@printf '\nOpen http://localhost:8084 (local Kafka UI: browse, produce and resend messages)\n'

kafka-events:
	docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:19092 --topic rentals.events --from-beginning --property print.key=true
