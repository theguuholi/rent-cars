# Included by the root Makefile; all paths are relative to the repository root.

.PHONY: infra-up infra-stop db-up db-stop db-create db-drop db-reset check-local-environment

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

# No FORCE: fail if applications still hold database connections.
db-reset: db-up
	docker compose exec -T postgres dropdb --if-exists -U postgres rent_cars_dev
	docker compose exec -T postgres createdb -U postgres rent_cars_dev
	@printf '%s\n' 'Database recreated. Run make dev for migrations and seeds. Kafka and storage are unchanged.'

check-local-environment:
	bash scripts/environment.sh check-local
