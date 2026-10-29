#!/usr/bin/env bash
set -euo pipefail

PORTAL_URL="${PORTAL_URL:-http://localhost:8080}"
FILE="${1:?usage: import-config.sh CONFIG_FILE [SERVICE_NAME]}"

jq -n --rawfile content "$FILE" --arg name "${2:-}" '{content: $content, serviceName: (if $name == "" then null else $name end)}' \
  | curl -sS --fail-with-body -X POST "$PORTAL_URL/api/service-configs/import" \
      -H 'Content-Type: application/json' --data-binary @- \
  | jq 'if .detail then . else {pipeline, notes, services: [.services[] | {name, notes}]} end'
