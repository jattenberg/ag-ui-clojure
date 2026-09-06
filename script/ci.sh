#!/usr/bin/env bash
# Local and GitHub Actions entrypoint.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ -z "${JAVA_HOME:-}" ]]; then
  if [[ -d /opt/homebrew/opt/openjdk ]]; then
    export JAVA_HOME=/opt/homebrew/opt/openjdk
  fi
fi

wait_http() {
  local url="$1"
  local n=0
  while (( n < 50 )); do
    if curl -sf "$url" >/dev/null; then
      return 0
    fi
    sleep 0.2
    n=$((n + 1))
  done
  echo "timeout waiting for $url" >&2
  return 1
}

PIDS=()
cleanup() {
  local p
  for p in "${PIDS[@]+"${PIDS[@]}"}"; do
    kill "$p" 2>/dev/null || true
  done
}
trap cleanup EXIT

echo "==> Babashka unit tests"
bb test

echo "==> JVM unit + property tests"
clojure -M:test

echo "==> Conformance (producer + consumer)"
clojure -M:conformance --profile all
bb conformance --profile all

echo "==> Live Clojure echo server"
clojure -M:server 18790 &
PIDS+=($!)
wait_http "http://127.0.0.1:18790/health"
clojure -M:client "http://127.0.0.1:18790/" Hello
clojure -M:conformance --profile producer --endpoint "http://127.0.0.1:18790/"
node interop/node/post-echo.mjs "http://127.0.0.1:18790/"

echo "==> Official @ag-ui/client against Clojure server"
(
  cd interop/node
  npm ci --no-fund --no-audit
  node consume-http-agent.mjs "http://127.0.0.1:18790/"
)

echo "==> Official Python encoder + echo server"
python3 -m venv .venv
.venv/bin/pip install -q --upgrade pip
.venv/bin/pip install -q ag-ui-protocol
.venv/bin/python interop/python/encode_events.py | diff -u fixtures/events/basic-run.jsonl -
.venv/bin/python interop/python/echo_server.py 18091 &
PIDS+=($!)
wait_http "http://127.0.0.1:18091/health"
clojure -M:client "http://127.0.0.1:18091/" Hello
clojure -M:conformance --profile consumer --endpoint "http://127.0.0.1:18091/"

echo "==> All CI checks passed"
