#!/usr/bin/env bash
# Starts N airports-routes instances locally. Ctrl+C stops all of them.
#
#   ./scripts/run-local.sh            2 instances, in-memory H2 (8082, 8092)
#   ./scripts/run-local.sh -n 3       3 instances (8082, 8092, 8102)
#   ./scripts/run-local.sh --tls      With HTTPS / TLS 1.3 encryption in transit
#   ./scripts/run-local.sh --postgres PostgreSQL instead of in-memory H2
#
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -z "${JAVA_HOME:-}" && -x "/home/afonso/Downloads/idea-IU-262.10968.63/jbr/bin/java" ]]; then
  export JAVA_HOME="/home/afonso/Downloads/idea-IU-262.10968.63/jbr"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

N=2
PROFILE=""
DB=h2
TLS=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    -n) N="$2"; [[ "$N" =~ ^[123]$ ]] || { echo "-n must be 1, 2 or 3"; exit 1; }; shift 2 ;;
    --postgres) DB=postgres; shift ;;
    --tls) TLS=true; PROFILE="${PROFILE:+$PROFILE,}tls"; shift ;;
    *) echo "unknown option: $1"; exit 1 ;;
  esac
done

if [[ "$TLS" == true ]]; then
  if [[ ! -f "certs/airports.p12" ]]; then
    echo "Generating / importing certificates for TLS..."
    ./scripts/generate-dev-certs.sh
  fi
fi

port() { echo $((8082 + 10 * ($1 - 1))); }

# Check for occupied ports
for i in $(seq 1 "$N"); do
  if (exec 3<>"/dev/tcp/127.0.0.1/$(port "$i")") 2>/dev/null; then
    echo "Port $(port "$i") is already in use - stop the running instance(s) first."
    exit 1
  fi
done

if [[ "$DB" == postgres ]]; then
  PROFILE="${PROFILE:+$PROFILE,}postgres"
fi

echo "Building airports-routes-service..."
./mvnw -q -B package -DskipTests
JAR=$(ls target/airports-routes-service-*.jar | head -1)
mkdir -p logs

PIDS=()
stop_port() {
  if command -v lsof >/dev/null; then
    kill $(lsof -t -i:"$1" -sTCP:LISTEN) 2>/dev/null || true
  elif command -v fuser >/dev/null; then
    fuser -k "$1/tcp" 2>/dev/null || true
  fi
}

cleanup() {
  trap '' INT TERM; trap - EXIT
  echo; echo "Stopping airports-routes instances..."
  for pid in "${PIDS[@]}"; do kill "$pid" 2>/dev/null || true; done
  for i in $(seq 1 "$N"); do stop_port "$(port "$i")"; done
  wait 2>/dev/null || true
  echo "All instances stopped."
}
trap cleanup INT TERM EXIT

for i in $(seq 1 "$N"); do
  echo "Starting airports-$i on port $(port "$i")..."
  java -jar "$JAR" --spring.profiles.active="instance$i${PROFILE:+,$PROFILE}" > "logs/airports-$i.log" 2>&1 &
  PIDS+=($!)
done

./scripts/wait-ready.sh -n "$N" $([[ "$TLS" == true ]] && echo "--tls")

echo "Instances running. Press Ctrl+C to stop."
wait
