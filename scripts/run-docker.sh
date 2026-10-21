#!/usr/bin/env bash
# Controls Docker Compose for airports-routes-service
#
#   ./scripts/run-docker.sh              # 2 instances with PostgreSQL (default)
#   ./scripts/run-docker.sh --h2         # 2 instances with lightweight H2
#   ./scripts/run-docker.sh -n 3         # 3 instances with PostgreSQL
#   ./scripts/run-docker.sh down         # stop all containers
#   ./scripts/run-docker.sh clean        # stop and remove all volumes
#
set -euo pipefail
cd "$(dirname "$0")/.."

if ! command -v docker >/dev/null 2>&1; then
  echo "Error: 'docker' is not installed or not in PATH."
  echo "To install Docker on CachyOS / Arch Linux, run:"
  echo "  sudo pacman -S docker docker-compose"
  echo "  sudo systemctl enable --now docker"
  echo "  sudo usermod -aG docker \$USER"
  echo "Then log out and log back in."
  exit 1
fi

COMPOSE_CMD="docker compose"
if ! docker compose version >/dev/null 2>&1; then
  if command -v docker-compose >/dev/null 2>&1; then
    COMPOSE_CMD="docker-compose"
  else
    echo "Error: neither 'docker compose' nor 'docker-compose' found."
    exit 1
  fi
fi

if ! docker info >/dev/null 2>&1; then
  echo "Error: Docker daemon is not running or current user does not have permission to access /var/run/docker.sock."
  echo "Try running:"
  echo "  sudo systemctl start docker"
  echo "  sudo chmod 666 /var/run/docker.sock   # for immediate access without relogin"
  exit 1
fi

if [[ "${1:-}" == "down" ]]; then
  echo "Stopping containers..."
  $COMPOSE_CMD down
  exit 0
fi

if [[ "${1:-}" == "clean" ]]; then
  echo "Stopping containers and deleting volumes..."
  $COMPOSE_CMD down -v
  exit 0
fi

N=2
FILE_ARGS=(-f docker-compose.yml)
while [[ $# -gt 0 ]]; do
  case "$1" in
    --h2) FILE_ARGS=(-f docker-compose.h2.yml); shift ;;
    -n)
      N="$2"
      [[ "$N" == "3" ]] && FILE_ARGS+=(-f docker-compose.scale-3.yml)
      shift 2
      ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
done

echo "Starting $N instance(s) with ${FILE_ARGS[*]}..."
$COMPOSE_CMD "${FILE_ARGS[@]}" up --build -d

echo
echo "Waiting for services to be ready..."
sleep 5
$COMPOSE_CMD ps

echo
echo "=================================================================="
echo "  Airports & Routes Cluster is UP in Docker"
echo "  Instance 1: http://localhost:8082"
echo "  Instance 2: http://localhost:8092"
if [[ "$N" == "3" ]]; then
  echo "  Instance 3: http://localhost:8102"
fi
echo "  Stop with:  ./scripts/run-docker.sh down"
echo "  Logs with:  docker compose logs -f"
echo "=================================================================="
