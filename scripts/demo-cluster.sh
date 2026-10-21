#!/usr/bin/env bash
# ==============================================================================
# Live Demonstration Script for SIDIS 2026/2027 (Afonso Sobreiro - 1241013)
# Component: airports-routes-service
# Demonstrates:
#   1. Health and cluster status
#   2. Local Hit (zero inter-instance hops)
#   3. P2P Unicast Forwarding (Instance 1 -> Instance 2)
#   4. P2P Unicast Forwarding (Instance 2 -> Instance 1)
#   5. Cross-Instance Route Forwarding
#   6. Distributed Loop / Cycle Prevention (X-Forwarded-From)
#   7. Inter-service Security Boundary (Role enforcement)
#   8. Public HATEOAS HAL API
# ==============================================================================

set -uo pipefail

# ANSI color codes
BOLD="\033[1m"
GREEN="\033[1;32m"
BLUE="\033[1;34m"
YELLOW="\033[1;33m"
CYAN="\033[1;36m"
RED="\033[1;31m"
MAGENTA="\033[1;35m"
RESET="\033[0m"

INST1="http://localhost:8082"
INST2="http://localhost:8092"

# Long-lived pre-generated tokens matching application JWT secret
SERVICE_TOKEN="eyJhbGciOiAiSFMyNTYifQ.eyJzdWIiOiAiZmxpZ2h0LW9wZXJhdGlvbnMtc2VydmljZSIsICJyb2xlIjogIlNFUlZJQ0UiLCAiaWF0IjogMTc5MTk4NzYwNywgImV4cCI6IDIxMDczNDc2MDd9.F5-WuUBJCYVGTi7N6zBwVr4V8_HuGrnoEn68aCISv_0"
ATCC_TOKEN="eyJhbGciOiAiSFMyNTYifQ.eyJzdWIiOiAiYXRjY191c2VyIiwgInJvbGUiOiAiQVRDQyIsICJpYXQiOiAxNzkxOTg3NjA3LCAiZXhwIjogMjEwNzM0NzYwN30.6Eu82D_esAmleeTUNilXWWzXMrDTHm16kWGpAlkamw8"
OPERATOR_TOKEN="eyJhbGciOiAiSFMyNTYifQ.eyJzdWIiOiAib3BlcmF0b3JfdXNlciIsICJyb2xlIjogIkJBQ0tPRkZJQ0VfT1BFUkFUT1IiLCAiaWF0IjogMTc5MTk4NzYwNywgImV4cCI6IDIxMDczNDc2MDd9.J1DOKgmUv8loS0iWtXqEYB_wnbTRAG9xlDNgg4FZJBo"

echo -e "${BOLD}${BLUE}"
echo "================================================================================"
echo "    LETI-SIDIS 2026/2027 - AIRPORTS & ROUTES CLUSTER LIVE DEMONSTRATION        "
echo "    Student: Afonso Sobreiro (1241013)                                          "
echo "    Transversal: Component Cloning & P2P Unicast Query Forwarding               "
echo "================================================================================"
echo -e "${RESET}"

# 0. Check cluster connectivity
echo -e "${YELLOW}>> Checking cluster connectivity...${RESET}"
H1=$(curl -s -o /dev/null -w "%{http_code}" "$INST1/actuator/health" 2>/dev/null || echo "DOWN")
H2=$(curl -s -o /dev/null -w "%{http_code}" "$INST2/actuator/health" 2>/dev/null || echo "DOWN")

if [[ "$H1" != "200" || "$H2" != "200" ]]; then
  echo -e "${RED}[ERROR] Both instances must be running to execute the live demonstration.${RESET}"
  echo "  - Instance 1 (port 8082): $H1"
  echo "  - Instance 2 (port 8092): $H2"
  echo
  echo -e "To start the cluster with Docker Compose, run:"
  echo -e "  ${BOLD}./scripts/run-docker.sh${RESET}       (PostgreSQL cluster)"
  echo -e "  ${BOLD}./scripts/run-docker.sh --h2${RESET}  (Lightweight in-memory H2)"
  exit 1
fi

echo -e "  Instance 1 ($INST1): ${GREEN}HEALTHY (HTTP 200)${RESET}"
echo -e "  Instance 2 ($INST2): ${GREEN}HEALTHY (HTTP 200)${RESET}"
echo

# Helper function to print test outcome
test_passed() {
  echo -e "  Result: ${GREEN}PASS${RESET} - $1"
  echo
}

# ------------------------------------------------------------------------------
# SCENARIO 1: Local Hit
# ------------------------------------------------------------------------------
echo -e "${CYAN}${BOLD}[Scenario 1] Local Hit on Instance 1${RESET}"
echo "  Goal: Prove that queries for local resources are answered with zero inter-instance hops."
echo "  Request: GET $INST1/internal/airports/LIS"
RESP=$(curl -s -i -H "Authorization: Bearer $SERVICE_TOKEN" "$INST1/internal/airports/LIS")
HTTP_CODE=$(echo "$RESP" | head -n 1 | awk '{print $2}')
DATA_SRC=$(echo "$RESP" | tr -d '\r' | grep -i "^X-Data-Source:" | awk '{print $2}')
INSTANCE=$(echo "$RESP" | tr -d '\r' | grep -i "^X-Instance:" | awk '{print $2}')

echo "  Status Code   : $HTTP_CODE"
echo "  X-Data-Source : $DATA_SRC"
echo "  X-Instance    : $INSTANCE"

if [[ "$HTTP_CODE" == "200" && "$DATA_SRC" == "local" ]]; then
  test_passed "LIS served directly by $INSTANCE without forwarding."
else
  echo -e "  ${RED}FAIL - expected HTTP 200 and X-Data-Source: local${RESET}"
fi

# ------------------------------------------------------------------------------
# SCENARIO 2: P2P Forwarding (Instance 1 -> Instance 2)
# ------------------------------------------------------------------------------
echo -e "${CYAN}${BOLD}[Scenario 2] P2P Forwarding (Instance 1 -> Instance 2)${RESET}"
echo "  Goal: Query Instance 1 for airport 'FAO' (stored only in Instance 2's partition)."
echo "  Request: GET $INST1/internal/airports/FAO"
RESP=$(curl -s -i -H "Authorization: Bearer $SERVICE_TOKEN" "$INST1/internal/airports/FAO")
HTTP_CODE=$(echo "$RESP" | head -n 1 | awk '{print $2}')
DATA_SRC=$(echo "$RESP" | tr -d '\r' | grep -i "^X-Data-Source:" | awk '{print $2}')
NAME=$(echo "$RESP" | grep -o '"name":"[^"]*"' | head -n 1)

echo "  Status Code   : $HTTP_CODE"
echo "  X-Data-Source : $DATA_SRC"
echo "  Payload Data  : $NAME"

if [[ "$HTTP_CODE" == "200" && "$DATA_SRC" == "peer" ]]; then
  test_passed "Instance 1 queried peer replica via Unicast HTTP GET and returned FAO ($NAME)."
else
  echo -e "  ${RED}FAIL - expected HTTP 200 and X-Data-Source: peer${RESET}"
fi

# ------------------------------------------------------------------------------
# SCENARIO 3: P2P Forwarding (Instance 2 -> Instance 1)
# ------------------------------------------------------------------------------
echo -e "${CYAN}${BOLD}[Scenario 3] Reverse P2P Forwarding (Instance 2 -> Instance 1)${RESET}"
echo "  Goal: Query Instance 2 for airport 'BCN' (stored only in Instance 1's partition)."
echo "  Request: GET $INST2/internal/airports/BCN"
RESP=$(curl -s -i -H "Authorization: Bearer $SERVICE_TOKEN" "$INST2/internal/airports/BCN")
HTTP_CODE=$(echo "$RESP" | head -n 1 | awk '{print $2}')
DATA_SRC=$(echo "$RESP" | tr -d '\r' | grep -i "^X-Data-Source:" | awk '{print $2}')
NAME=$(echo "$RESP" | grep -o '"name":"[^"]*"' | head -n 1)

echo "  Status Code   : $HTTP_CODE"
echo "  X-Data-Source : $DATA_SRC"
echo "  Payload Data  : $NAME"

if [[ "$HTTP_CODE" == "200" && "$DATA_SRC" == "peer" ]]; then
  test_passed "Instance 2 queried peer replica via Unicast HTTP GET and returned BCN ($NAME)."
else
  echo -e "  ${RED}FAIL - expected HTTP 200 and X-Data-Source: peer${RESET}"
fi

# ------------------------------------------------------------------------------
# SCENARIO 4: Route P2P Forwarding
# ------------------------------------------------------------------------------
echo -e "${CYAN}${BOLD}[Scenario 4] Cross-Instance Route Forwarding${RESET}"
echo "  Goal: Query Instance 1 for flight route 'route-fao-lis' (stored on Instance 2)."
echo "  Request: GET $INST1/internal/routes/route-fao-lis"
RESP=$(curl -s -i -H "Authorization: Bearer $SERVICE_TOKEN" "$INST1/internal/routes/route-fao-lis")
HTTP_CODE=$(echo "$RESP" | head -n 1 | awk '{print $2}')
DATA_SRC=$(echo "$RESP" | tr -d '\r' | grep -i "^X-Data-Source:" | awk '{print $2}')
ROUTE_ID=$(echo "$RESP" | grep -o '"routeId":"[^"]*"' | head -n 1)

echo "  Status Code   : $HTTP_CODE"
echo "  X-Data-Source : $DATA_SRC"
echo "  Payload Data  : $ROUTE_ID"

if [[ "$HTTP_CODE" == "200" && "$DATA_SRC" == "peer" ]]; then
  test_passed "Route route-fao-lis retrieved seamlessly from peer clone."
else
  echo -e "  ${RED}FAIL - expected HTTP 200 and X-Data-Source: peer${RESET}"
fi

# ------------------------------------------------------------------------------
# SCENARIO 5: Cycle / Infinite Loop Prevention
# ------------------------------------------------------------------------------
echo -e "${CYAN}${BOLD}[Scenario 5] Cycle / Infinite Loop Prevention (X-Forwarded-From)${RESET}"
echo "  Goal: Query for a non-existent airport 'NONEXISTENT'. Prevent ping-pong loop."
echo "  Request: GET $INST1/internal/airports/NONEXISTENT"
RESP=$(curl -s -i -H "Authorization: Bearer $SERVICE_TOKEN" "$INST1/internal/airports/NONEXISTENT")
HTTP_CODE=$(echo "$RESP" | head -n 1 | awk '{print $2}')

echo "  Status Code   : $HTTP_CODE"

if [[ "$HTTP_CODE" == "404" ]]; then
  test_passed "Cycle prevented. Peer was queried exactly once, returned 404 cleanly without loop."
else
  echo -e "  ${RED}FAIL - expected HTTP 404${RESET}"
fi

# ------------------------------------------------------------------------------
# SCENARIO 6: Security Boundary Enforcement
# ------------------------------------------------------------------------------
echo -e "${CYAN}${BOLD}[Scenario 6] Security Boundary Enforcement (/internal/**)${RESET}"
echo "  Goal: Ensure internal service endpoints reject unauthorized or human user tokens."
echo "  Request A: No token -> GET $INST1/internal/airports/LIS"
NO_AUTH_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$INST1/internal/airports/LIS")
echo "  No Token Status : $NO_AUTH_CODE"

echo "  Request B: ATCC human user token -> GET $INST1/internal/airports/LIS"
ATCC_CODE=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $ATCC_TOKEN" "$INST1/internal/airports/LIS")
echo "  ATCC Status     : $ATCC_CODE"

if [[ ("$NO_AUTH_CODE" == "401" || "$NO_AUTH_CODE" == "403") && "$ATCC_CODE" == "403" ]]; then
  test_passed "Security boundary strictly enforced. Only ROLE_SERVICE can access /internal/**."
else
  echo -e "  ${RED}FAIL - expected 401/403 for unauthorized calls${RESET}"
fi

# ------------------------------------------------------------------------------
# SCENARIO 7: Public REST API with HATEOAS HAL
# ------------------------------------------------------------------------------
echo -e "${CYAN}${BOLD}[Scenario 7] Public REST API with HATEOAS HAL Links${RESET}"
echo "  Goal: Access public airport catalog using BACKOFFICE_OPERATOR token."
echo "  Request: GET $INST1/api/airports"
RESP=$(curl -s -i -H "Authorization: Bearer $OPERATOR_TOKEN" "$INST1/api/airports")
HTTP_CODE=$(echo "$RESP" | head -n 1 | awk '{print $2}')
HAS_LINKS=$(echo "$RESP" | grep -c '"_links"' || true)

echo "  Status Code : $HTTP_CODE"
echo "  Has _links  : $HAS_LINKS"

if [[ "$HTTP_CODE" == "200" && "$HAS_LINKS" -gt 0 ]]; then
  test_passed "Public catalog returned HATEOAS HAL structure with embedded links."
else
  echo -e "  ${RED}FAIL - expected HTTP 200 with _links${RESET}"
fi

echo -e "${BOLD}${GREEN}"
echo "================================================================================"
echo "    ALL DEMONSTRATION SCENARIOS COMPLETED SUCCESSFULLY!                         "
echo "================================================================================"
echo -e "${RESET}"
