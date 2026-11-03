.PHONY: db-up db-stop db-drop db-create setup dev api-dev web-dev

db-up:
	docker compose up -d --wait postgres

db-drop:
	docker compose exec postgres dropdb -U postgres rent_cars_dev

db-create:
	docker compose exec postgres createdb -U postgres rent_cars_dev

setup:
	npm --prefix web ci

dev: db-up
	API_TARGET="$${API_TARGET:-http://127.0.0.1:$${SERVER_PORT:-8080}}" npm --prefix web run dev:stack

api-dev: db-up
	cd api && ./mvnw spring-boot:run

web-dev:
	npm --prefix web run dev -- --strictPort

db-stop:
	docker compose stop postgres

.PHONY: check-api check-web check
check-api:
	cd api && ./mvnw -B verify

check-web:
	npm --prefix web run format:check
	npm --prefix web run lint
	npm --prefix web run test:coverage
	npm --prefix web run build

check: check-api check-web
