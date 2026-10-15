#!/usr/bin/env bash
set -euo pipefail

PORTAL_URL="${PORTAL_URL:-http://localhost:8080}"
PRODUCT_ID="${1:?usage: monitoring.sh PRODUCT_ID PIPELINE_ID [RANGE]}"
PIPELINE_ID="${2:?usage: monitoring.sh PRODUCT_ID PIPELINE_ID [RANGE]}"
RANGE="${3:-30d}"

echo "== Metrics and dashboards"
curl -sS --fail-with-body "$PORTAL_URL/api/monitoring/status" | jq .

echo "== Every product"
curl -sS --fail-with-body "$PORTAL_URL/api/monitoring/products" \
  | jq 'if .detail then . else {metricsError,
         products: [.products[] | {productId, code, overall, statusCounts, lastRunAt}]} end'

echo "== All pipelines together, $RANGE"
curl -sS --fail-with-body "$PORTAL_URL/api/monitoring/activity?range=$RANGE" \
  | jq 'if .detail then . else {pipelines, metricsError, dora: (.dora | del(.daily)),
         lastSevenDays: (.dora.daily | .[-7:])} end'

echo "== Product $PRODUCT_ID"
curl -sS --fail-with-body "$PORTAL_URL/api/monitoring/products/$PRODUCT_ID" \
  | jq 'if .detail then . else {code, overall, metricsError,
         pipelines: [.pipelines[] | {id: .pipeline.id, type: .pipeline.type, service: .pipeline.serviceName,
                                     status, lastRunAt: .lastRun.time}]} end'

echo "== Pipeline $PIPELINE_ID, $RANGE"
curl -sS --fail-with-body "$PORTAL_URL/api/monitoring/pipelines/$PIPELINE_ID?range=$RANGE" \
  | jq 'if .detail then . else {status, lastRun, dora: (.dora | del(.daily)),
         recentRuns: (.recentRuns | length), grafana, metricsError} end'
