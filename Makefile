.PHONY: db-up

db-up:
	docker compose up -d --wait postgres