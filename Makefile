.DEFAULT_GOAL := help
KAFKA_ENABLED ?= true

.PHONY: help k8s-up k8s-down
help:
	@printf '%s\n' \
	  'make dev       Start API, React, infrastructure and Kafka UI' \
	  'make check     Run all Java and frontend checks' \
	  'make recommendations-dev  Consume rental events with Spring Boot' \
	  'make kafka-up  Start Kafka and create the rental events topic' \
	  'make kafka-ui  Open local Kafka UI on localhost:8084' \
	  'make kafka-events  Read retained rental events' \
	  'make k8s-kafka-up  Install local Strimzi/Kafka separately' \
	  'make k8s-up    Start/update Kubernetes with published images (localhost:8082)' \
	  'make k8s-down  Delete the app from Kubernetes, including database and photos'

ifeq ($(KAFKA_ENABLED),true)
k8s-up: k8s-kafka-up
endif

k8s-up:
	@kubectl --context docker-desktop create namespace rent-cars --dry-run=client -o yaml | kubectl --context docker-desktop apply -f -
	@kubectl --context docker-desktop -n rent-cars get secret rent-cars-credentials >/dev/null 2>&1 || ( \
	  set -eu; umask 077; keys=$$(mktemp -d); trap 'rm -rf "$$keys"' EXIT; \
	  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$$keys/private.pem" 2>/dev/null; \
	  openssl pkey -in "$$keys/private.pem" -pubout -out "$$keys/public.pem"; \
	  openssl rand -hex 32 | tr -d '\n' > "$$keys/postgres-password"; \
	  kubectl --context docker-desktop -n rent-cars create secret generic rent-cars-credentials --from-file="$$keys"; \
	)
	helm upgrade --install fleet ./helm --kube-context docker-desktop -n rent-cars --reset-values --set messaging.enabled=$(KAFKA_ENABLED) --wait --timeout 6m
	kubectl --context docker-desktop -n rent-cars rollout restart deployment/fleet-api deployment/fleet-web
	kubectl --context docker-desktop -n rent-cars rollout status deployment/fleet-api deployment/fleet-web --timeout=180s
	@if kubectl --context docker-desktop -n rent-cars get deployment/fleet-recommendations >/dev/null 2>&1; then \
	  kubectl --context docker-desktop -n rent-cars rollout restart deployment/fleet-recommendations && \
	  kubectl --context docker-desktop -n rent-cars rollout status deployment/fleet-recommendations --timeout=180s; \
	fi
	@printf '\nOpen http://localhost:8082\n'

k8s-down:
	kubectl --context docker-desktop delete namespace rent-cars --ignore-not-found --wait=true

.PHONY: db-up db-stop db-drop db-create setup dev api-dev web-dev

db-up:
	docker compose up -d --wait postgres

db-drop:
	docker compose exec postgres dropdb -U postgres rent_cars_dev

db-create:
	docker compose exec postgres createdb -U postgres rent_cars_dev

setup:
	npm --prefix apps/web ci

dev: infra-up
	docker compose --profile kafka-ui up -d kafka-ui
	@printf '%s\n' 'Web: http://localhost:5173' "Swagger: http://localhost:$${SERVER_PORT:-8080}/swagger-ui.html" 'Grafana: http://localhost:3000' 'Mailpit: http://localhost:8025' "Kafka UI: http://localhost:$${KAFKA_UI_PORT:-8084}"
	RENT_MESSAGING_ENABLED=true AWS_ACCESS_KEY_ID=rent-cars-dev AWS_SECRET_ACCESS_KEY=rent-cars-dev-secret API_TARGET="$${API_TARGET:-http://127.0.0.1:$${SERVER_PORT:-8080}}" npm --prefix apps/web run dev:stack

api-dev: infra-up
	cd apps/api && RENT_MESSAGING_ENABLED=true AWS_ACCESS_KEY_ID=rent-cars-dev AWS_SECRET_ACCESS_KEY=rent-cars-dev-secret ./mvnw spring-boot:run

web-dev:
	npm --prefix apps/web run dev -- --strictPort

db-stop:
	docker compose stop postgres

.PHONY: check-api check-web check
check-api:
	cd apps/api && ./mvnw -B verify

check-web:
	npm --prefix apps/web run format:check
	npm --prefix apps/web run lint
	npm --prefix apps/web run architecture
	npm --prefix apps/web run test:coverage
	npm --prefix apps/web run build

check: check-java check-web check-infra

.PHONY: infra-up infra-stop
infra-up:
	docker compose up -d --wait postgres s3 mailpit observability kafka
	docker compose run --rm kafka-init

infra-stop:
	docker compose stop postgres s3 mailpit observability kafka kafka-ui

.PHONY: kafka-up kafka-ui kafka-events k8s-kafka-up
kafka-up:
	docker compose up -d --wait kafka
	docker compose run --rm kafka-init

kafka-ui: kafka-up
	docker compose --profile kafka-ui up -d kafka-ui
	@printf '\nOpen http://localhost:8084 (local Kafka UI: browse, produce and resend messages)\n'

kafka-events:
	docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:19092 --topic rentals.events --from-beginning --property print.key=true

k8s-kafka-up:
	helm upgrade --install rent-kafka-operator strimzi-kafka-operator --repo https://strimzi.io/charts/ --version 0.49.1 --kube-context docker-desktop --namespace rent-kafka --create-namespace --wait --timeout 5m
	kubectl --context docker-desktop -n rent-kafka apply -f deploy/kafka/local.yaml
	kubectl --context docker-desktop -n rent-kafka wait kafka/rent --for=condition=Ready --timeout=300s
	kubectl --context docker-desktop -n rent-kafka wait kafkatopic/rentals-events --for=condition=Ready --timeout=120s

.PHONY: recommendations-dev check-recommendations check-java
recommendations-dev: kafka-up
	cd apps/recommendations && ./mvnw spring-boot:run

check-recommendations:
	cd apps/recommendations && ./mvnw -B verify

check-java: check-api check-recommendations

.PHONY: check-infra
check-infra:
	python3 -m unittest discover -s scripts/tests -p '*_test.py' -v
