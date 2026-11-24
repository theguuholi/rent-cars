# Included by the root Makefile; all paths are relative to the repository root.
KAFKA_ENABLED ?= true

ifeq ($(KAFKA_ENABLED),true)
k8s-up: k8s-kafka-up
endif

.PHONY: k8s-up k8s-stop k8s-down k8s-kafka-up check-k8s-environment

k8s-up: check-k8s-environment
	@kubectl --context docker-desktop create namespace rent-cars --dry-run=client -o yaml | kubectl --context docker-desktop apply -f -
	@kubectl --context docker-desktop -n rent-cars get secret rent-cars-credentials >/dev/null 2>&1 || ( \
	  set -eu; umask 077; keys=$$(mktemp -d); trap 'rm -rf "$$keys"' EXIT; \
	  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$$keys/private.pem" 2>/dev/null; \
	  openssl pkey -in "$$keys/private.pem" -pubout -out "$$keys/public.pem"; \
	  openssl rand -hex 32 | tr -d '\n' > "$$keys/postgres-password"; \
	  kubectl --context docker-desktop -n rent-cars create secret generic rent-cars-credentials --from-file="$$keys"; \
	)
	helm upgrade --install fleet ./helm --kube-context docker-desktop -n rent-cars --reset-values --set messaging.enabled=$(KAFKA_ENABLED) --set hdfs.enabled=$(HDFS_ENABLED) --set recommendations.job.enabled=$(HDFS_ENABLED) --wait --timeout 6m
	kubectl --context docker-desktop -n rent-cars rollout restart deployment/fleet-api deployment/fleet-web
	kubectl --context docker-desktop -n rent-cars rollout status deployment/fleet-api deployment/fleet-web --timeout=180s
	@if kubectl --context docker-desktop -n rent-cars get deployment/fleet-recommendations >/dev/null 2>&1; then \
	  kubectl --context docker-desktop -n rent-cars rollout restart deployment/fleet-recommendations && \
	  kubectl --context docker-desktop -n rent-cars rollout status deployment/fleet-recommendations --timeout=180s; \
	fi
	@printf '\nOpen http://localhost:5173\n'

k8s-stop:
	bash scripts/environment.sh stop-k8s

k8s-down:
	kubectl --context docker-desktop delete namespace rent-cars --ignore-not-found --wait=true

k8s-kafka-up: check-k8s-environment
	helm upgrade --install rent-kafka-operator strimzi-kafka-operator --repo https://strimzi.io/charts/ --version 0.49.1 --kube-context docker-desktop --namespace rent-cars --create-namespace --wait --timeout 5m
	kubectl --context docker-desktop -n rent-cars scale deployment/strimzi-cluster-operator --replicas=1
	kubectl --context docker-desktop -n rent-cars rollout status deployment/strimzi-cluster-operator --timeout=120s
	kubectl --context docker-desktop -n rent-cars apply -f helm/kafka/local.yaml
	kubectl --context docker-desktop -n rent-cars wait kafka/rent --for=condition=Ready --timeout=300s
	kubectl --context docker-desktop -n rent-cars wait kafkatopic/rentals-events kafkatopic/rentals-events-recommendations-dlt --for=condition=Ready --timeout=120s
	kubectl --context docker-desktop -n rent-cars apply -f helm/kafka/ui-local.yaml
	kubectl --context docker-desktop -n rent-cars scale deployment/kafka-ui --replicas=1

check-k8s-environment:
	bash scripts/environment.sh check-k8s

.PHONY: k8s-recommendations-build
k8s-recommendations-build:
	kubectl --context docker-desktop -n rent-cars create job --from=cronjob/fleet-recommendations-job fleet-recommendations-job-$$(date +%s)
