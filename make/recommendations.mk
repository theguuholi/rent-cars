# Included by the root Makefile; all paths are relative to the repository root.

.PHONY: recommendations-dev check-recommendations

recommendations-dev: kafka-up
	docker compose run --rm recommendations-db-init
	cd apps/recommendations && HDFS_ARCHIVE_ENABLED=$(HDFS_ENABLED) ./mvnw spring-boot:run

check-recommendations:
	cd apps/recommendations && ./mvnw -B verify

.PHONY: recommendations-build check-recommendations-job

# The service must have applied its Flyway migration before running a batch.
recommendations-build:
	RECOMMENDATIONS_AS_OF=$${RECOMMENDATIONS_AS_OF:-$$(date -u +%Y-%m-%dT%H:%M:%SZ)} docker compose --profile hdfs --profile spark run --build --rm recommendations-job

JAVA21_HOME ?= $(JAVA_HOME)

check-recommendations-job:
	cd apps/recommendations-job && JAVA_HOME="$(JAVA21_HOME)" ./mvnw -B verify
