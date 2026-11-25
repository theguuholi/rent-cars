.DEFAULT_GOAL := help
HDFS_ENABLED ?= false

# Load category targets into one dependency graph, preserving startup order.
include make/development.mk
include make/api.mk
include make/web.mk
include make/recommendations.mk
include make/infrastructure.mk
include make/kafka.mk
include make/hdfs.mk
include make/k8s.mk

.PHONY: help check check-java

help:
	@printf '%s\n' \
	  'make doctor       Diagnose local prerequisites without changing services' \
	  'make dev-tmux     Start or reattach an optional tmux session' \
	  'make setup        Install frontend dependencies (first run)' \
	  'make dev          Start API, React, recommendations and all local infrastructure' \
	  'make dev HDFS_ENABLED=true  Also archive rental events in HDFS' \
	  'make infra-stop   Stop infrastructure after Ctrl+C; preserve data' \
	  'make db-reset     DELETE and recreate the local database (stop applications first)' \
	  'make check        Verify all applications' \
	  'make kafka-events Read Kafka messages in the terminal' \
	  'make k8s-up       Deploy the complete stack using published images' \
	  'make k8s-stop     Stop Kubernetes workloads; preserve volumes' \
	  '' \
	  'Advanced commands: docs/getting-started.md and helm/README.md.'

# Root commands coordinate the independent application checks.
check: check-java check-web

check-java: check-api check-recommendations check-recommendations-job
