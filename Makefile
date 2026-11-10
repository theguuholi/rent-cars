.DEFAULT_GOAL := help

.PHONY: help k8s-up k8s-down
help:
	@printf '%s\n' \
	  'make dev       Start local development' \
	  'make check     Run backend and frontend checks' \
	  'make k8s-up    Start/update Kubernetes with published images (localhost:8082)' \
	  'make k8s-down  Delete the app from Kubernetes, including database and photos'

k8s-up:
	@kubectl --context docker-desktop create namespace rent-cars --dry-run=client -o yaml | kubectl --context docker-desktop apply -f -
	@kubectl --context docker-desktop -n rent-cars get secret rent-cars-credentials >/dev/null 2>&1 || ( \
	  set -eu; umask 077; keys=$$(mktemp -d); trap 'rm -rf "$$keys"' EXIT; \
	  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$$keys/private.pem" 2>/dev/null; \
	  openssl pkey -in "$$keys/private.pem" -pubout -out "$$keys/public.pem"; \
	  openssl rand -hex 32 | tr -d '\n' > "$$keys/postgres-password"; \
	  kubectl --context docker-desktop -n rent-cars create secret generic rent-cars-credentials --from-file="$$keys"; \
	)
	helm upgrade --install fleet ./helm --kube-context docker-desktop -n rent-cars --reset-values --wait --timeout 6m
	kubectl --context docker-desktop -n rent-cars rollout restart deployment/fleet-api deployment/fleet-web
	kubectl --context docker-desktop -n rent-cars rollout status deployment/fleet-api deployment/fleet-web --timeout=180s
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
	AWS_ACCESS_KEY_ID=rent-cars-dev AWS_SECRET_ACCESS_KEY=rent-cars-dev-secret API_TARGET="$${API_TARGET:-http://127.0.0.1:$${SERVER_PORT:-8080}}" npm --prefix apps/web run dev:stack

api-dev: infra-up
	cd apps/api && AWS_ACCESS_KEY_ID=rent-cars-dev AWS_SECRET_ACCESS_KEY=rent-cars-dev-secret ./mvnw spring-boot:run

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

check: check-api check-web

.PHONY: infra-up infra-stop
infra-up:
	docker compose up -d --wait postgres s3 mailpit

infra-stop:
	docker compose stop postgres s3 mailpit
