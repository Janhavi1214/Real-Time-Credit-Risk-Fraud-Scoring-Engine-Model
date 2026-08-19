#!/usr/bin/env bash
# ---------------------------------------------------------------------------
#  RiskGuard — End-to-End Verification Script
#
#  Brings up the full Docker Compose stack, waits for all services to
#  become healthy, sends a sample transaction through the pipeline
#  (ingestion → Kafka → decision → scoring → Postgres), polls for the
#  persisted decision, and reports PASS / FAIL.
#
#  Usage:  bash verify.sh
# ---------------------------------------------------------------------------
set -euo pipefail

COMPOSE_FILE="docker-compose.yml"
TIMEOUT=300          # seconds to wait for each service
POLL_INTERVAL=3      # seconds between health-checks
DECISION_POLL_MAX=40 # max polling attempts for decision to appear

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'  # No Color

banner() { echo -e "\n${CYAN}═══════════════════════════════════════════════════════════${NC}"; echo -e "${CYAN}  $1${NC}"; echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}\n"; }
info()   { echo -e "${CYAN}ℹ  $1${NC}"; }
ok()     { echo -e "${GREEN}✅ $1${NC}"; }
warn()   { echo -e "${YELLOW}⚠  $1${NC}"; }
fail()   { echo -e "${RED}❌ $1${NC}"; }

# ── Step 0: Tear down any previous run ──────────────────────────────────────
banner "Step 0 — Cleaning up previous containers"
docker compose -f "$COMPOSE_FILE" --profile app down --remove-orphans 2>/dev/null || true
ok "Previous containers removed"

# ── Step 1: Build & bring up the full stack ────────────────────────────────
banner "Step 1 — Building and starting all services"
docker compose -f "$COMPOSE_FILE" --profile app up -d --build
ok "docker compose up completed"

# ── Step 2: Wait for all services to become healthy ────────────────────────
wait_for_healthy() {
  local service=$1
  local elapsed=0
  info "Waiting for ${service} to become healthy (timeout: ${TIMEOUT}s)..."
  while true; do
    health=$(docker inspect --format='{{.State.Health.Status}}' "$(docker compose -f "$COMPOSE_FILE" ps -q "$service" 2>/dev/null)" 2>/dev/null || echo "not_found")
    if [ "$health" = "healthy" ]; then
      ok "${service} is healthy (${elapsed}s)"
      return 0
    fi
    if [ $elapsed -ge $TIMEOUT ]; then
      fail "${service} did not become healthy after ${TIMEOUT}s (status: ${health})"
      echo ""
      warn "Last logs from ${service}:"
      docker compose -f "$COMPOSE_FILE" logs --tail=30 "$service"
      return 1
    fi
    sleep $POLL_INTERVAL
    elapsed=$((elapsed + POLL_INTERVAL))
  done
}

banner "Step 2 — Waiting for infrastructure services"
wait_for_healthy kafka
wait_for_healthy redis
wait_for_healthy postgres

banner "Step 2b — Waiting for application services"
wait_for_healthy scoring-service
wait_for_healthy ingestion-service
wait_for_healthy decision-service

# ── Step 3: Send a sample transaction ──────────────────────────────────────
banner "Step 3 — Sending sample transaction to ingestion-service"

SAMPLE_TXN='{
  "userId": "user-e2e-test-001",
  "amount": 250.75,
  "currency": "USD",
  "merchantId": "merch-42",
  "merchantCategory": "electronics",
  "timestamp": "'$(date -u +%Y-%m-%dT%H:%M:%SZ)'",
  "deviceId": "device-abc",
  "location": "New York, US",
  "paymentMethod": "CREDIT_CARD"
}'

info "Payload:"
echo "$SAMPLE_TXN" | python -m json.tool 2>/dev/null || echo "$SAMPLE_TXN"
echo ""

INGEST_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  -H "Content-Type: application/json" \
  -d "$SAMPLE_TXN" \
  http://localhost:8080/api/v1/transactions)

HTTP_CODE=$(echo "$INGEST_RESPONSE" | tail -1)
BODY=$(echo "$INGEST_RESPONSE" | sed '$d')

info "Ingestion HTTP status: ${HTTP_CODE}"
info "Ingestion response body: ${BODY}"

if [ "$HTTP_CODE" != "202" ]; then
  fail "Ingestion service did not return 202 Accepted (got ${HTTP_CODE})"
  warn "Ingestion service logs:"
  docker compose -f "$COMPOSE_FILE" logs --tail=30 ingestion-service
  exit 1
fi

# Extract the transactionId from the response
TXN_ID=$(echo "$BODY" | python -c "import sys,json; print(json.load(sys.stdin)['transactionId'])" 2>/dev/null || echo "")
if [ -z "$TXN_ID" ]; then
  fail "Could not extract transactionId from ingestion response"
  exit 1
fi
ok "Transaction accepted: transactionId = ${TXN_ID}"

# ── Step 4: Poll decision-service for the resulting decision ───────────────
banner "Step 4 — Polling decision-service for decision on ${TXN_ID}"

DECISION_URL="http://localhost:8082/api/v1/decisions/${TXN_ID}"
attempt=0
DECISION_BODY=""

while [ $attempt -lt $DECISION_POLL_MAX ]; do
  RESP=$(curl -s -w "\n%{http_code}" "$DECISION_URL")
  D_HTTP=$(echo "$RESP" | tail -1)
  D_BODY=$(echo "$RESP" | sed '$d')

  if [ "$D_HTTP" = "200" ] && [ -n "$D_BODY" ]; then
    DECISION_BODY="$D_BODY"
    break
  fi

  attempt=$((attempt + 1))
  sleep $POLL_INTERVAL
  info "Poll attempt ${attempt}/${DECISION_POLL_MAX} — status ${D_HTTP}"
done

if [ -z "$DECISION_BODY" ]; then
  fail "Decision not found after ${DECISION_POLL_MAX} attempts"
  warn "Decision service logs:"
  docker compose -f "$COMPOSE_FILE" logs --tail=50 decision-service
  warn "Scoring service logs:"
  docker compose -f "$COMPOSE_FILE" logs --tail=20 scoring-service
  exit 1
fi

# ── Step 5: Validate the decision ──────────────────────────────────────────
banner "Step 5 — Verifying decision result"

info "Decision response:"
echo "$DECISION_BODY" | python -m json.tool 2>/dev/null || echo "$DECISION_BODY"
echo ""

FINAL_DECISION=$(echo "$DECISION_BODY" | python -c "import sys,json; print(json.load(sys.stdin).get('finalDecision','UNKNOWN'))" 2>/dev/null || echo "UNKNOWN")

case "$FINAL_DECISION" in
  APPROVE|FLAG|BLOCK)
    ok "Final decision: ${FINAL_DECISION}"
    ;;
  *)
    fail "Unexpected final decision value: ${FINAL_DECISION}"
    exit 1
    ;;
esac

# ── Summary ────────────────────────────────────────────────────────────────
banner "RESULT"
echo ""
ok "End-to-end verification PASSED!"
echo ""
info "Flow: ingestion-service → Kafka (transactions.raw) → decision-service → scoring-service → Postgres"
info "Transaction ID : ${TXN_ID}"
info "Final Decision : ${FINAL_DECISION}"
echo ""
echo -e "${GREEN}════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}  ALL SYSTEMS GO — RiskGuard is fully operational  🚀${NC}"
echo -e "${GREEN}════════════════════════════════════════════════════════════${NC}"
echo ""
