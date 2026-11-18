.DEFAULT_GOAL := help

# Kubernetes messaging is enabled unless explicitly disabled.
KAFKA_ENABLED ?= true

.PHONY: help
help:
	@printf '%s\n' \
	  '' \
	  'LOCAL DEVELOPMENT' \
	  '  make setup                    Install frontend dependencies' \
	  '  make dev                      Start API, React, infrastructure and Kafka UI' \
	  '  make api-dev                  Start only the API and infrastructure' \
	  '  make web-dev                  Start only React' \
	  '  make recommendations-dev      Start the Kafka consumer' \
	  '' \
	  'INFRASTRUCTURE AND KAFKA' \
	  '  make infra-up                 Start local infrastructure' \
	  '  make infra-stop               Stop local infrastructure; preserve volumes' \
	  '  make db-up / db-stop          Start or stop only PostgreSQL' \
	  '  make db-create                Create the development database' \
	  '  make db-drop                  DELETE the development database' \
	  '  make kafka-up                 Start Kafka and prepare the topic' \
	  '  make kafka-ui                 Start Kafka UI on localhost:8084' \
	  '  make kafka-events             Read retained rental events' \
	  '' \
	  'QUALITY' \
	  '  make check                    Verify all applications' \
	  '  make check-java               Verify API and recommendations' \
	  '  make check-api                Verify the API' \
	  '  make check-web                Verify React' \
	  '  make check-recommendations    Verify the Kafka consumer' \
	  '' \
	  'KUBERNETES (DOCKER DESKTOP)' \
	  '  make k8s-up                   Deploy published images on localhost:5173' \
	  '  make k8s-stop                 Stop workloads and release ports; preserve volumes' \
	  '  make k8s-down                 DELETE app namespace, database and photos' \
	  '' \
	  'Switch modes explicitly: Ctrl+C + make infra-stop, or make k8s-stop.' \
	  'KAFKA_ENABLED=false skips Kafka preparation on make k8s-up.'

# Local development
.PHONY: setup dev api-dev web-dev recommendations-dev

setup:
	npm --prefix apps/web ci

dev: infra-up
	docker compose --profile kafka-ui up -d kafka-ui
	@printf '%s\n' 'Web: http://localhost:5173' "Swagger: http://localhost:$${SERVER_PORT:-8080}/swagger-ui.html" 'Grafana: http://localhost:3000' 'Mailpit: http://localhost:8025' "Kafka UI: http://localhost:$${KAFKA_UI_PORT:-8084}"
	RENT_MESSAGING_ENABLED=true AWS_ACCESS_KEY_ID=rent-cars-dev AWS_SECRET_ACCESS_KEY=rent-cars-dev-secret API_TARGET="$${API_TARGET:-http://127.0.0.1:$${SERVER_PORT:-8080}}" npm --prefix apps/web run dev:stack

api-dev: infra-up
	cd apps/api && RENT_MESSAGING_ENABLED=true AWS_ACCESS_KEY_ID=rent-cars-dev AWS_SECRET_ACCESS_KEY=rent-cars-dev-secret ./mvnw spring-boot:run

web-dev: check-local-environment
	npm --prefix apps/web run dev -- --strictPort

recommendations-dev: kafka-up
	cd apps/recommendations && ./mvnw spring-boot:run

# Local infrastructure
.PHONY: infra-up infra-stop db-up db-stop db-create db-drop

infra-up: check-local-environment
	docker compose up -d --wait postgres s3 mailpit observability kafka
	docker compose run --rm kafka-init

infra-stop:
	docker compose stop postgres s3 mailpit observability kafka kafka-ui

db-up: check-local-environment
	docker compose up -d --wait postgres

db-stop:
	docker compose stop postgres

db-create:
	docker compose exec postgres createdb -U postgres rent_cars_dev

db-drop:
	docker compose exec postgres dropdb -U postgres rent_cars_dev

# Kafka tools
.PHONY: kafka-up kafka-ui kafka-events

kafka-up: check-local-environment
	docker compose up -d --wait kafka
	docker compose run --rm kafka-init

kafka-ui: kafka-up
	docker compose --profile kafka-ui up -d kafka-ui
	@printf '\nOpen http://localhost:8084 (local Kafka UI: browse, produce and resend messages)\n'

kafka-events:
	docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:19092 --topic rentals.events --from-beginning --property print.key=true

# Quality checks
.PHONY: check check-java check-api check-web check-recommendations

check: check-java check-web

check-java: check-api check-recommendations

check-api:
	cd apps/api && ./mvnw -B verify

check-web:
	npm --prefix apps/web run format:check
	npm --prefix apps/web run lint
	npm --prefix apps/web run architecture
	npm --prefix apps/web run test:coverage
	npm --prefix apps/web run build

check-recommendations:
	cd apps/recommendations && ./mvnw -B verify

# Local Kubernetes
.PHONY: k8s-up k8s-stop k8s-down k8s-kafka-up

ifeq ($(KAFKA_ENABLED),true)
k8s-up: k8s-kafka-up
endif

k8s-up: check-k8s-environment
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
	@printf '\nOpen http://localhost:5173\n'

k8s-stop:
	bash scripts/environment.sh stop-k8s

k8s-down:
	kubectl --context docker-desktop delete namespace rent-cars --ignore-not-found --wait=true

k8s-kafka-up: check-k8s-environment
	helm upgrade --install rent-kafka-operator strimzi-kafka-operator --repo https://strimzi.io/charts/ --version 0.49.1 --kube-context docker-desktop --namespace rent-kafka --create-namespace --wait --timeout 5m
	kubectl --context docker-desktop -n rent-kafka scale deployment/strimzi-cluster-operator --replicas=1
	kubectl --context docker-desktop -n rent-kafka rollout status deployment/strimzi-cluster-operator --timeout=120s
	kubectl --context docker-desktop -n rent-kafka apply -f helm/kafka/local.yaml
	kubectl --context docker-desktop -n rent-kafka wait kafka/rent --for=condition=Ready --timeout=300s
	kubectl --context docker-desktop -n rent-kafka wait kafkatopic/rentals-events --for=condition=Ready --timeout=120s
	kubectl --context docker-desktop -n rent-kafka apply -f helm/kafka/ui-local.yaml
	kubectl --context docker-desktop -n rent-kafka scale deployment/kafka-ui --replicas=1

# Environment guards (startup prerequisites)
.PHONY: check-local-environment check-k8s-environment

check-local-environment:
	bash scripts/environment.sh check-local

check-k8s-environment:
	bash scripts/environment.sh check-k8s
