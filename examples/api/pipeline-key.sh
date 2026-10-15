#!/usr/bin/env bash
set -euo pipefail

PORTAL_URL="${PORTAL_URL:-http://localhost:8080}"
PRODUCT_CODE="${1:?usage: pipeline-key.sh PRODUCT_CODE SERVICE [FULL|SECURITY|EXTENDED|SAST|NEXUS_IQ]}"
SERVICE="${2:?usage: pipeline-key.sh PRODUCT_CODE SERVICE [FULL|SECURITY|EXTENDED|SAST|NEXUS_IQ]}"
TYPE="${3:-FULL}"

PRODUCT_ID=$(curl -sS --fail-with-body -G "$PORTAL_URL/api/products" --data-urlencode "search=$PRODUCT_CODE" \
  | jq -e --arg code "$PRODUCT_CODE" '.[] | select(.code == $code) | .id')

curl -sS --fail-with-body "$PORTAL_URL/api/products/$PRODUCT_ID/pipelines" \
  | jq -er --arg service "$SERVICE" --arg type "$TYPE" \
      '.[] | select(.serviceName == $service) | .pipelines[] | select(.type == $type) | .activeKey.value'
