# Included by the root Makefile; all paths are relative to the repository root.

.PHONY: recommendations-dev check-recommendations

recommendations-dev: kafka-up
	cd apps/recommendations && ./mvnw spring-boot:run

check-recommendations:
	cd apps/recommendations && ./mvnw -B verify
