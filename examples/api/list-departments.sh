#!/usr/bin/env bash
set -euo pipefail

PORTAL_URL="${PORTAL_URL:-http://localhost:8080}"

curl -sS --fail-with-body "$PORTAL_URL/api/departments" | jq .
