#!/usr/bin/env bash
# Local Docker Desktop only. Never delete namespaces, volumes or credentials.
set -euo pipefail
command -v jq >/dev/null || { echo "Install jq before switching environments." >&2; exit 1; }
cd "$(dirname "$0")/.."

kube() { kubectl --context docker-desktop --request-timeout=10s "$@"; }

check_local() {
  command -v kubectl >/dev/null || return 0
  if ! kubectl config get-contexts -o name | grep -qx docker-desktop; then return 0; fi
  # An unreachable configured cluster is not proof that its workloads are stopped.
  local resources
  resources=$(kube get pods,deployments,statefulsets,services -A -o json) || {
    echo 'Cannot inspect Docker Desktop Kubernetes. Restore access before starting local services.' >&2
    return 1
  }
  if jq -e '.items[] | select(.metadata.namespace == "rent-cars") |
    select((.kind == "Pod" and .status.phase != "Succeeded" and .status.phase != "Failed") or
      ((.kind == "Deployment" or .kind == "StatefulSet") and .spec.replicas > 0) or
      (.kind == "Service" and .spec.type == "LoadBalancer"))' <<< "$resources" >/dev/null; then
    echo 'Kubernetes is active. Run make k8s-stop, then retry. Data is preserved.' >&2
    return 1
  fi
}

check_k8s() {
  local containers processes
  containers=$(docker compose --profile kafka-ui ps --status running -q)
  processes=$(pgrep -f 'com.github.theguuholi.(rentcars.RentCarsApiApplication|recommendations.RecommendationsApplication)|/rent-cars/apps/web/node_modules/.bin/vite' || true)
  if [[ -n "$containers" || -n "$processes" ]]; then
    echo 'Local environment is active. Stop make dev/api-dev/web-dev/recommendations-dev with Ctrl+C in their terminals, then run make infra-stop and retry.' >&2
    return 1
  fi
}

stop_k8s() {
  local services workloads pods operator
  if [[ -z "$(kube get namespace rent-cars --ignore-not-found -o name)" ]]; then return 0; fi
  # Stop Strimzi first so it cannot recreate Kafka pods during shutdown.
  operator=$(kube -n rent-cars get deployment strimzi-cluster-operator --ignore-not-found -o name)
  if [[ -n "$operator" ]]; then
    kube -n rent-cars scale deployment strimzi-cluster-operator --replicas=0
    kube -n rent-cars wait --for=delete pod -l name=strimzi-cluster-operator --timeout=120s
  fi
  workloads=$(kube -n rent-cars get deployment,statefulset -o name)
  if [[ -n "$workloads" ]]; then
    kube -n rent-cars scale deployment,statefulset --all --replicas=0
  fi
  # Release host ports as well as stopping processes; scale alone keeps LoadBalancers.
  services=$(kube -n rent-cars get services -o json | jq -r '.items[] | select(.spec.type == "LoadBalancer") | .metadata.name')
  while read -r service; do
    [[ -n "$service" ]] || continue
    kube -n rent-cars patch service "$service" --type=merge -p '{"spec":{"type":"ClusterIP"}}'
  done <<< "$services"
  kube -n rent-cars delete pods -l strimzi.io/cluster=rent --wait=true --timeout=120s
  pods=$(kube -n rent-cars get pods --field-selector=status.phase!=Succeeded,status.phase!=Failed -o name)
  if [[ -n "$pods" ]]; then
    while read -r pod; do
      kube -n rent-cars wait --for=delete "$pod" --timeout=120s
    done <<< "$pods"
  fi
  echo 'Kubernetes workloads stopped. Volumes, Kafka events and credentials retained. Resume with make k8s-up.'
}

case "${1:-}" in
  check-local) check_local ;;
  check-k8s) check_k8s ;;
  stop-k8s) stop_k8s ;;
  *) echo 'Usage: environment.sh check-local|check-k8s|stop-k8s' >&2; exit 2 ;;
esac
