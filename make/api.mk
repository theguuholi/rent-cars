# Included by the root Makefile; all paths are relative to the repository root.

.PHONY: api-dev check-api

api-dev: infra-up
	cd apps/api && RENT_MESSAGING_ENABLED=true AWS_ACCESS_KEY_ID=rent-cars-dev AWS_SECRET_ACCESS_KEY=rent-cars-dev-secret ./mvnw spring-boot:run

check-api:
	cd apps/api && ./mvnw -B verify
