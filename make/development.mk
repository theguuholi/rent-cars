# Root-level coordination; application commands share one environment definition.
.PHONY: dev dev-prepare dev-tmux doctor check-development

dev-prepare: infra-up
	docker compose --profile kafka-ui up -d kafka-ui

dev: dev-prepare
	HDFS_ENABLED=$(HDFS_ENABLED) bash scripts/development/start.sh

dev-tmux:
	HDFS_ENABLED=$(HDFS_ENABLED) bash scripts/development/tmux.sh

doctor:
	bash scripts/development/doctor.sh

check-development:
	node --test scripts/development/development.test.mjs
