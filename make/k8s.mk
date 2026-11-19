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
	# k8s-stop patches Service types; client-side updates allow Helm to restore them.
	helm upgrade --install fleet ./helm --kube-context docker-desktop -n rent-cars --reset-values --server-side=false --set messaging.enabled=$(KAFKA_ENABLED) --wait --timeout 6m
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
	# Delete topics while their operator is running, before deleting the namespace.
	kubectl --context docker-desktop -n rent-cars delete kafkatopic --all --wait=true --timeout=60s
	kubectl --context docker-desktop delete namespace rent-cars --ignore-not-found --wait=true

k8s-kafka-up: check-k8s-environment
	helm upgrade --install rent-kafka-operator strimzi-kafka-operator --repo https://strimzi.io/charts/ --version 0.49.1 --kube-context docker-desktop --namespace rent-cars --create-namespace --server-side=false --wait --timeout 5m
	kubectl --context docker-desktop -n rent-cars scale deployment/strimzi-cluster-operator --replicas=1
	kubectl --context docker-desktop -n rent-cars rollout status deployment/strimzi-cluster-operator --timeout=120s
	kubectl --context docker-desktop -n rent-cars apply -f helm/kafka/local.yaml
	# A stopped broker can leave stale Ready status on Kafka and StrimziPodSet.
	@if [ -z "$$(kubectl --context docker-desktop -n rent-cars get pod rent-combined-0 --ignore-not-found -o name)" ] && \
	    [ -n "$$(kubectl --context docker-desktop -n rent-cars get strimzipodset rent-combined --ignore-not-found -o name)" ]; then \
	  kubectl --context docker-desktop -n rent-cars annotate strimzipodset rent-combined strimzi.io/manual-rolling-update=true --overwrite; \
	fi
	kubectl --context docker-desktop -n rent-cars wait --for=create pod/rent-combined-0 --timeout=180s
	kubectl --context docker-desktop -n rent-cars wait pod/rent-combined-0 --for=condition=Ready --timeout=300s
	kubectl --context docker-desktop -n rent-cars wait kafka/rent --for=condition=Ready --timeout=300s
	kubectl --context docker-desktop -n rent-cars wait kafkatopic/rentals-events --for=condition=Ready --timeout=120s
	kubectl --context docker-desktop -n rent-cars apply -f helm/kafka/ui-local.yaml
	kubectl --context docker-desktop -n rent-cars scale deployment/kafka-ui --replicas=1

check-k8s-environment:
	bash scripts/environment.sh check-k8s
	# Set the default namespace without switching the active cluster.
	kubectl config set-context docker-desktop --namespace=rent-cars
	@printf '%s\n' 'Namespace: rent-cars. With docker-desktop active, use: kubectl get pods' 'To select the local cluster: kubectl config use-context docker-desktop'
