#!/usr/bin/env bash
set -euo pipefail

PORTAL_URL="${PORTAL_URL:-http://localhost:8080}"
PIPELINE_ID="${1:?usage: revoke-key.sh PIPELINE_ID REASON}"
REASON="${2:?usage: revoke-key.sh PIPELINE_ID REASON}"

jq -n --arg reason "$REASON" '{reason: $reason}' \
  | curl -sS --fail-with-body -X POST "$PORTAL_URL/api/pipelines/$PIPELINE_ID/keys/revoke" \
      -H 'Content-Type: application/json' --data-binary @- \
  | jq 'if .detail then . else {id, type, serviceName, enabled,
         keys: [.keys[] | {hint, status, revokedAt, revokeReason}]} end'
