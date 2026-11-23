# Included by the root Makefile; all paths are relative to the repository root.

.PHONY: recommendations-dev check-recommendations

recommendations-dev: kafka-up
	cd apps/recommendations && HDFS_ARCHIVE_ENABLED=$(HDFS_ENABLED) ./mvnw spring-boot:run

check-recommendations:
	cd apps/recommendations && ./mvnw -B verify
