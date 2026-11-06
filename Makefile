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
	npm --prefix apps/web run test:coverage
	npm --prefix apps/web run build

check: check-api check-web

.PHONY: infra-up infra-stop
infra-up:
	docker compose up -d --wait postgres s3

infra-stop:
	docker compose stop postgres s3
