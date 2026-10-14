#!/usr/bin/env bash
# Waits until the local airports-routes instances are ready for tests, then prints READY FOR TESTS.
# "Ready" = /actuator/health answers UP: Spring has fully started and the sample data is loaded.
set -uo pipefail
cd "$(dirname "$0")/.."

N=2
SCHEME=http
ONLY=""
TIMEOUT=180
while [[ $# -gt 0 ]]; do
  case "$1" in
    -n) N="$2"; shift 2 ;;
    --tls) SCHEME=https; shift ;;
    --instance) ONLY="$2"; shift 2 ;;
    --timeout) TIMEOUT="$2"; shift 2 ;;
    *) echo "unknown option: $1"; exit 1 ;;
  esac
done

port() { echo $((8082 + 10 * ($1 - 1))); }
CURL=(curl -sS -o /dev/null -w '%{http_code}' --max-time 2)
[[ "$SCHEME" == https ]] && CURL+=(--insecure)
ERRF=$(mktemp)
trap 'rm -f "$ERRF"' EXIT

probe() { "${CURL[@]}" "$SCHEME://localhost:$(port "$1")/actuator/health" 2>"$ERRF"; }

wait_for() {
  local start=$SECONDS code rc
  while true; do
    code=$(probe "$1"); rc=$?
    [[ "$code" == 200 ]] && break
    if (( rc != 0 && rc != 7 && rc != 28 )); then
      echo "airports-$1: $(head -1 "$ERRF")"
      return 1
    elif (( rc == 0 )) && [[ "$code" != 503 ]]; then
      case "$code" in
        400) echo "airports-$1: HTTP 400 - the instance runs HTTPS, add --tls" ;;
        *)   echo "airports-$1: HTTP $code from /actuator/health" ;;
      esac
      return 1
    fi
    if (( SECONDS - start >= TIMEOUT )); then
      echo "airports-$1 is NOT ready after ${TIMEOUT}s - see logs/airports-$1.log"
      return 1
    fi
    sleep 1
  done
  echo "airports-$1 ready  $SCHEME://localhost:$(port "$1")  ($((SECONDS - start))s)"
}

if [[ -n "$ONLY" ]]; then
  wait_for "$ONLY"
  exit
fi

echo "Waiting for $N airports-routes instance(s) to be ready..."
failed=0
for i in $(seq 1 "$N"); do
  wait_for "$i" || failed=1
done
(( failed )) && exit 1

echo
echo "=================================================================="
echo "  READY FOR TESTS - all $N airports-routes instance(s) are up"
echo "  Instance 1: http://localhost:8082"
echo "  Instance 2: http://localhost:8092"
echo "=================================================================="
echo
