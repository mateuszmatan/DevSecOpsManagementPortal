#!/usr/bin/env bash
set -euo pipefail

PORTAL_URL="${PORTAL_URL:-http://localhost:8080}"
PIPELINE_ID="${1:?usage: rotate-key.sh PIPELINE_ID}"

curl -sS --fail-with-body -X POST "$PORTAL_URL/api/pipelines/$PIPELINE_ID/keys" \
  | jq 'if .detail then . else {id, type, serviceName, enabled, activeKey: .activeKey.value,
         keys: [.keys[] | {hint, status, issuedAt, revokedAt, revokeReason}]} end'
