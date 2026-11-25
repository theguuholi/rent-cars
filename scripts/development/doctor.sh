#!/usr/bin/env bash
set -uo pipefail
cd "$(dirname "$0")/../.."
failures=0
fail() { echo "FAIL: $*"; failures=$((failures + 1)); }
for tool in java javac node npm make docker jq curl; do
  command -v "$tool" >/dev/null || fail "Install $tool and reopen your terminal."
done
if command -v java >/dev/null; then
  version=$(java -version 2>&1)
  [[ "$version" =~ version\ \"25[.\"] ]] || fail 'Select JDK 25 (java -version).'
fi
if command -v javac >/dev/null; then
  [[ "$(javac -version 2>&1)" == 'javac 25'* ]] || fail 'Select JDK 25 for javac too.'
fi
if command -v node >/dev/null; then
  [[ "$(node --version)" == 'v24.16.0' ]] || fail 'Select Node 24.16.0.'
fi
if command -v docker >/dev/null; then
  docker info >/dev/null 2>&1 || fail 'Start Docker Desktop and wait for its engine.'
  docker compose version >/dev/null 2>&1 || fail 'Install Docker Compose v2.'
fi
[[ -x apps/web/node_modules/.bin/concurrently ]] || fail 'Run make setup to install frontend and development dependencies.'
if command -v lsof >/dev/null; then
  echo 'Listening development ports (an existing environment can legitimately own them):'
  lsof -nP -iTCP:5173 -iTCP:8080 -iTCP:8085 -iTCP:5432 -iTCP:9092 -sTCP:LISTEN || true
else
  echo 'INFO: Install lsof to inspect occupied development ports.'
fi
if (( failures > 0 )); then
  echo "$failures prerequisite check(s) failed. No services were changed."
  exit 1
fi
echo 'Prerequisites OK. Run make dev; use make check-local-environment to check Kubernetes conflicts.'
