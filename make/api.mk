# Included by the root Makefile; all paths are relative to the repository root.

.PHONY: api-dev check-api

api-dev: infra-up
	HDFS_ENABLED=$(HDFS_ENABLED) bash scripts/development/app.sh api

check-api:
	cd apps/api && ./mvnw -B verify
